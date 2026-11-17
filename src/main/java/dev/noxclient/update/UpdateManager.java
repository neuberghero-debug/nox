package dev.noxclient.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Signed self-update (Modul 19).
 *
 * Manifest (JSON, fetched from NOX_UPDATE_URL / -Dnox.update.url):
 *   {"version":"0.2.0","url":"https://.../noxclient-0.2.0.jar",
 *    "sha256":"<hex>","min_client_version":"0.1.0",
 *    "timestamp":1794833400000,"sig":"<b64url ed25519>"}
 * Signature covers the canonical string
 *   version|url|sha256|min_client_version|timestamp
 * with the SAME Ed25519 key as license tokens — one trust root.
 *
 * Flow: check → download to mods/.nox-staged.jar → sha256 → sig →
 * anti-downgrade (version + signed timestamp monotonic) → on shutdown spawn
 * Apply-JVM → old.jar→.bak, staged→jar name. Next boot: if apply failed,
 * restore .bak automatically.
 */
public final class UpdateManager {
    /** Same trust anchor as LicenseManager — update manifests are signed with the license key. */
    private static final byte[] PUB_DER;
    static {
        byte[] raw = Base64.getDecoder().decode("wAoUOK5sP+q9eqjoXY7V4C//P+v4hel4AMNvP2H8/qE=");
        byte[] prefix = {0x30, 0x2A, 0x30, 0x05, 0x06, 0x03, 0x2B, 0x65, 0x70, 0x03, 0x21, 0x00};
        PUB_DER = new byte[prefix.length + raw.length];
        System.arraycopy(prefix, 0, PUB_DER, 0, prefix.length);
        System.arraycopy(raw, 0, PUB_DER, prefix.length, raw.length);
    }

    private static final String MANIFEST_URL = System.getProperty("nox.update.url",
        System.getenv().getOrDefault("NOX_UPDATE_URL",
            "https://nox-client.vercel.app/api/update.json"));
    private static final long CHECK_MS = 6 * 3_600_000L;
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private static volatile boolean pending;   // staged update awaits restart
    private static volatile String pendingVersion = "";
    private static long lastCheck = 0;

    public static boolean pending() { return pending; }
    public static String pendingVersion() { return pendingVersion; }

    /** Boot-time: resolve last session's apply result, then check in background.
     *  Dev runs (classes dir, no jar) skip the network entirely. */
    public static void init() {
        resolveApplyResult();
        if (selfJar() != null) checkAsync();
    }

    /** 6h re-check from the client tick. */
    public static void tick() {
        if (pending || selfJar() == null) return;
        if (System.currentTimeMillis() - lastCheck < CHECK_MS) return;
        checkAsync();
    }

    private static void checkAsync() {
        lastCheck = System.currentTimeMillis();
        Thread.ofVirtual().start(() -> {
            try {
                JsonObject m = fetchManifest();
                if (m == null || !eligible(m)) return;
                Path staged = modsDir().resolve(".nox-staged.jar");
                download(m.get("url").getAsString(), staged);
                String sha = sha256(staged);
                if (!sha.equalsIgnoreCase(m.get("sha256").getAsString())) {
                    Files.deleteIfExists(staged);
                    alert("update hash mismatch — refused");
                    return;
                }
                markState("staged", m);
                pending = true;
                pendingVersion = m.get("version").getAsString();
                dev.noxclient.gui.Toasts.ok(
                    "NOX " + pendingVersion + " bereit — Neustart zum Installieren");
                Runtime.getRuntime().addShutdownHook(new Thread(UpdateManager::spawnApplier));
            } catch (Throwable ignored) {}
        });
    }

    /* ---------------- verification ---------------- */

    private static JsonObject fetchManifest() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(MANIFEST_URL))
            .timeout(Duration.ofSeconds(8)).GET().build();
        HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) return null;
        return JsonParser.parseString(r.body()).getAsJsonObject();
    }

    /** sig + anti-downgrade (version ordering + monotonic signed timestamp). */
    private static boolean eligible(JsonObject m) {
        try {
            String ver = m.get("version").getAsString();
            String url = m.get("url").getAsString();
            String sha = m.get("sha256").getAsString();
            String min = m.has("min_client_version") ? m.get("min_client_version").getAsString() : "0";
            long ts = m.get("timestamp").getAsLong();
            String sig = m.get("sig").getAsString();

            String canon = ver + "|" + url + "|" + sha + "|" + min + "|" + ts;
            if (!verify(canon.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        Base64.getUrlDecoder().decode(sig))) {
                alert("update signature invalid — refused");
                return false;
            }
            // anti-downgrade: version must exceed current, ts must not go backwards
            if (compareVersions(ver, currentVersion()) <= 0) return false;
            if (compareVersions(currentVersion(), min) < 0) {
                alert("client too old for this manifest");
                return false;
            }
            long lastTs = stateTs();
            if (ts < lastTs) {
                alert("update timestamp rolled back — refused");
                return false;
            }
            return true;
        } catch (Throwable t) { return false; }
    }

    private static boolean verify(byte[] payload, byte[] sig) throws Exception {
        PublicKey pk = KeyFactory.getInstance("Ed25519")
            .generatePublic(new X509EncodedKeySpec(PUB_DER));
        Signature s = Signature.getInstance("Ed25519");
        s.initVerify(pk);
        s.update(payload);
        return s.verify(sig);
    }

    /* ---------------- install ---------------- */

    private static void download(String url, Path out) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMinutes(3)).GET().build();
        Path tmp = out.resolveSibling(out.getFileName() + ".part");
        HttpResponse<Path> r = HTTP.send(req, HttpResponse.BodyHandlers.ofFile(tmp));
        if (r.statusCode() != 200) { Files.deleteIfExists(tmp); throw new Exception("http " + r.statusCode()); }
        Files.move(tmp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /** Spawn the detached swap JVM — runs only when this JVM exits. */
    private static void spawnApplier() {
        try {
            Path old = selfJar();
            if (old == null) return;
            Path staged = modsDir().resolve(".nox-staged.jar");
            if (!Files.exists(staged)) return;
            String javaBin = Path.of(System.getProperty("java.home"), "bin",
                isWin() ? "java.exe" : "java").toString();
            new ProcessBuilder(javaBin, "-cp", staged.toString(),
                Apply.class.getName(),
                String.valueOf(ProcessHandle.current().pid()),
                old.toString(), staged.toString(),
                old.resolveSibling(old.getFileName() + ".bak").toString())
                .redirectErrorStream(true)
                .redirectOutput(Path.of(Minecraft.getInstance().gameDirectory.toString(),
                    "nox", "update-apply.log").toFile())
                .start();
        } catch (Throwable ignored) {}
    }

    /** Next boot: check Apply's result; restore .bak on failure. */
    private static void resolveApplyResult() {
        try {
            Path self = selfJar();
            if (self == null) return;
            Path result = self.resolveSibling(self.getFileName() + ".bak.result");
            if (!Files.exists(result)) return;
            String r = Files.readString(result).trim();
            Files.deleteIfExists(result);
            Path bak = self.resolveSibling(self.getFileName() + ".bak");
            if (r.startsWith("fail") && Files.exists(bak)) {
                Files.move(bak, self, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } else if (r.equals("ok")) {
                dev.noxclient.gui.Toasts.ok("NOX update installed");
            }
        } catch (Throwable ignored) {}
    }

    /* ---------------- helpers ---------------- */

    public static String currentVersion() {
        return FabricLoader.getInstance().getModContainer("noxclient")
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse("0.0.0");
    }

    /** path to the currently-running mod jar; null in dev (classes dir). */
    private static Path selfJar() {
        try {
            var cont = FabricLoader.getInstance().getModContainer("noxclient").orElse(null);
            if (cont == null) return null;
            var paths = cont.getRootPaths();
            if (paths.isEmpty()) return null;
            Path root = paths.get(0);
            return Files.isRegularFile(root) ? root : null;   // dir → dev run
        } catch (Throwable t) { return null; }
    }

    private static Path modsDir() {
        Path self = selfJar();
        if (self != null) return self.getParent();
        return FabricLoader.getInstance().getGameDir().resolve("mods");
    }

    private static String sha256(Path f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(f)) {
            byte[] buf = new byte[1 << 16];
            for (int n; (n = in.read(buf)) > 0;) md.update(buf, 0, n);
        }
        return HexFormat.of().formatHex(md.digest());
    }

    private static int compareVersions(String a, String b) {
        int[] pa = parse(a), pb = parse(b);
        for (int i = 0; i < 3; i++)
            if (pa[i] != pb[i]) return Integer.compare(pa[i], pb[i]);
        return 0;
    }

    private static int[] parse(String v) {
        String[] s = v.split("[.\\-+]"); int[] r = {0, 0, 0};
        for (int i = 0; i < Math.min(3, s.length); i++)
            try { r[i] = Integer.parseInt(s[i].replaceAll("\\D", "")); } catch (Exception ignored) {}
        return r;
    }

    private static Path stateFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("nox/update.state");
    }

    private static long stateTs() {
        try {
            if (!Files.exists(stateFile())) return 0;
            JsonObject o = JsonParser.parseString(Files.readString(stateFile())).getAsJsonObject();
            return o.has("ts") ? o.get("ts").getAsLong() : 0;
        } catch (Throwable t) { return 0; }
    }

    private static void markState(String phase, JsonObject m) {
        try {
            Files.createDirectories(stateFile().getParent());
            Files.writeString(stateFile(), "{\"phase\":\"" + phase + "\",\"ts\":"
                + m.get("timestamp").getAsLong() + ",\"version\":\""
                + m.get("version").getAsString() + "\"}");
        } catch (Throwable ignored) {}
    }

    private static void alert(String msg) {
        dev.noxclient.gui.Toasts.warn("NOX update: " + msg);
    }

    private static boolean isWin() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
