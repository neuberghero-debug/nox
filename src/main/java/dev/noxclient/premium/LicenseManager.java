package dev.noxclient.premium;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;

/**
 * Premium gate — Ed25519-signed license tokens, verified offline first.
 *
 * Token format: NOX1.<b64url(payload)>.<b64url(signature)>
 * Payload JSON: {"tier":"premium","lid":"a1b2c3","exp":1780000000000,"sub":"discordId"}
 *
 * Hardening (Modul 18):
 *  - Server activation binds license↔hwid when NOX_LICENSE_API is reachable.
 *  - Silent revalidation every 24h: revoked licenses kill premium even offline-sigged.
 *  - Offline grace: premium stays up to 14d without a server response.
 *  - Clock rollback: persisted lastSeen > now → tamper → premium off.
 *  - assertPremium() is the check used at the 5+ gate sites.
 */
public final class LicenseManager {
    /** NOX license public key (raw 32B Ed25519, embedded as X.509 DER). */
    private static final byte[] PUB_DER;
    static {
        byte[] raw = Base64.getDecoder().decode("wAoUOK5sP+q9eqjoXY7V4C//P+v4hel4AMNvP2H8/qE=");
        byte[] prefix = {0x30, 0x2A, 0x30, 0x05, 0x06, 0x03, 0x2B, 0x65, 0x70, 0x03, 0x21, 0x00};
        PUB_DER = new byte[prefix.length + raw.length];
        System.arraycopy(prefix, 0, PUB_DER, 0, prefix.length);
        System.arraycopy(raw, 0, PUB_DER, prefix.length, raw.length);
    }

    /** Backend base URL — empty = offline-only mode. */
    private static final String API = System.getProperty("nox.license.api",
        System.getenv().getOrDefault("NOX_LICENSE_API", ""));
    private static final long REVALIDATE_MS = 24 * 3_600_000L;
    private static final long GRACE_MS = 14L * 86_400_000L;
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(4)).build();

    public enum Result { OK, BAD_FORMAT, BAD_SIGNATURE, EXPIRED, HWID_BOUND, REVOKED, TAMPER }

    private static volatile boolean premium;
    private static volatile boolean capeUnlocked;
    private static volatile String licenseId = "";
    private static volatile long expires = 0;
    private static volatile long lastValid = 0;   // last successful server validate
    private static volatile long lastSeen = 0;    // max(serverTime, local now)
    private static volatile long lastAttempt = 0; // last validate attempt
    private static volatile boolean validating;

    public static boolean premium() { return premium && !clockRolledBack() && !graceExpired(); }
    /** Gate used at every premium feature site (module toggle, cosmetics, replay, config, shop). */
    public static boolean assertPremium() { return premium(); }
    /** One-time custom-cape upload entitlement (tier "cape", no expiry). */
    public static boolean capeUnlocked() { return premium() || capeUnlocked; }
    public static String licenseId() { return licenseId; }
    public static long expires() { return expires; }
    /** True when the license was revoked server-side (or clock tampered). */
    public static boolean clockRolledBack() { return lastSeen > 0 && System.currentTimeMillis() < lastSeen - 60_000; }

    /** Stored activation key (or "") — used by FriendSync login. */
    public static String storedKey() {
        try {
            Path p = keyFile();
            return Files.exists(p) ? Files.readString(p).trim() : "";
        } catch (Exception e) { return ""; }
    }

    /** Configured license API base ("" if unset). */
    public static String api() { return API; }

    public static void init() {
        try {
            Path p = keyFile();
            if (Files.exists(p) && check(Files.readString(p).trim()) == Result.OK)
                premium = true;
        } catch (Exception ignored) {}
        try {
            Path c = capeFile();
            if (Files.exists(c) && checkTier(Files.readString(c).trim(), "cape") == Result.OK)
                capeUnlocked = true;
        } catch (Exception ignored) {}
        loadState();
        if (premium) validateAsync();   // catch up silently on start
    }

    /** Called from the client tick — silent 24h revalidation. */
    public static void tick() {
        if (!premium || API.isEmpty() || validating) return;
        if (System.currentTimeMillis() - lastAttempt < REVALIDATE_MS) return;
        validateAsync();
    }

    /** Grace expired → drop premium (until a successful validate restores it). */
    public static boolean graceExpired() {
        return premium && !API.isEmpty() && lastValid > 0
            && System.currentTimeMillis() - lastValid > GRACE_MS;
    }

    /** One-time cape unlock — "cape" tier tokens never expire. */
    public static Result unlockCape(String key) {
        Result r = checkTier(key, "cape");
        if (r != Result.OK) return r;
        try {
            Path c = capeFile();
            Files.createDirectories(c.getParent());
            Files.writeString(c, key.trim());
        } catch (Exception ignored) {}
        capeUnlocked = true;
        return Result.OK;
    }

    /** Activate a key. Offline sig check first; server bind when reachable. */
    public static Result unlock(String key) {
        Result r = check(key);
        if (r != Result.OK) return r;
        if (clockRolledBack()) return Result.TAMPER;
        if (!API.isEmpty()) {
            Result srv = serverCall("/activate", key);
            if (srv != null && srv != Result.OK) return srv;
        }
        try {
            Path p = keyFile();
            Files.createDirectories(p.getParent());
            Files.writeString(p, key.trim());
        } catch (Exception ignored) {}
        premium = true;
        saveState();
        return Result.OK;
    }

    /** Silent server validation — revocation → premium off. */
    private static void validateAsync() {
        if (API.isEmpty() || validating) return;
        validating = true;
        lastAttempt = System.currentTimeMillis();
        Thread.ofVirtual().start(() -> {
            try {
                Path p = keyFile();
                if (!Files.exists(p)) { validating = false; return; }
                Result r = serverCall("/validate", Files.readString(p).trim());
                if (r == Result.OK) {
                    lastValid = System.currentTimeMillis();
                } else if (r == Result.REVOKED || r == Result.EXPIRED || r == Result.HWID_BOUND) {
                    premium = false;
                    dev.noxclient.gui.Toasts.push(
                        "NOX license " + r.name().toLowerCase() + " — premium off", 0xFFFF5555);
                }
                saveState();
            } catch (Throwable ignored) {
            } finally { validating = false; }
        });
    }

    /** POST {key,hwid} → mapped Result or null when unreachable. */
    private static Result serverCall(String path, String key) {
        try {
            String body = "{\"key\":" + json(key) + ",\"hwid\":" + json(dev.noxclient.ncore.NativeCore.hwid()) + "}";
            HttpRequest req = HttpRequest.newBuilder(URI.create(API + path))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(6))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            String resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body();
            JsonObject o = JsonParser.parseString(resp).getAsJsonObject();
            if (o.get("ok").getAsBoolean()) {
                if (o.has("serverTime"))
                    lastSeen = Math.max(lastSeen, o.get("serverTime").getAsLong());
                lastSeen = Math.max(lastSeen, System.currentTimeMillis());
                return Result.OK;
            }
            String err = o.has("err") ? o.get("err").getAsString() : "";
            return switch (err) {
                case "revoked" -> Result.REVOKED;
                case "expired" -> Result.EXPIRED;
                case "hwid_bound", "hwid_mismatch" -> Result.HWID_BOUND;
                default -> Result.BAD_FORMAT;
            };
        } catch (Exception e) {
            return null;    // offline → grace mode
        }
    }

    private static String json(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static Result check(String key) {
        try {
            String[] parts = key.trim().split("\\.");
            if (parts.length != 3 || !parts[0].equals("NOX1")) return Result.BAD_FORMAT;
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            byte[] sig = Base64.getUrlDecoder().decode(parts[2]);
            if (!verify(payload, sig)) return Result.BAD_SIGNATURE;
            JsonObject o = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8))
                .getAsJsonObject();
            if (!"premium".equals(o.get("tier").getAsString())) return Result.BAD_FORMAT;
            long exp = o.get("exp").getAsLong();
            if (System.currentTimeMillis() > exp) return Result.EXPIRED;
            licenseId = o.has("lid") ? o.get("lid").getAsString() : "";
            expires = exp;
            return Result.OK;
        } catch (Exception e) { return Result.BAD_FORMAT; }
    }

    /** Signature + tier check without touching premium state. exp<=0 = never. */
    private static Result checkTier(String key, String tier) {
        try {
            String[] parts = key.trim().split("\\.");
            if (parts.length != 3 || !parts[0].equals("NOX1")) return Result.BAD_FORMAT;
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            byte[] sig = Base64.getUrlDecoder().decode(parts[2]);
            if (!verify(payload, sig)) return Result.BAD_SIGNATURE;
            JsonObject o = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8))
                .getAsJsonObject();
            if (!tier.equals(o.get("tier").getAsString())) return Result.BAD_FORMAT;
            long exp = o.has("exp") ? o.get("exp").getAsLong() : 0;
            if (exp > 0 && System.currentTimeMillis() > exp) return Result.EXPIRED;
            return Result.OK;
        } catch (Exception e) { return Result.BAD_FORMAT; }
    }

    private static boolean verify(byte[] payload, byte[] sig) throws Exception {
        // native-first: lib reassembles the pubkey from salted fragments,
        // so it never sits contiguous in the jar or a heap snapshot
        if (dev.noxclient.ncore.NativeCore.available())
            return dev.noxclient.ncore.NativeCore.verify(
                payload, sig, dev.noxclient.ncore.NativeCore.publicKeyRaw());
        PublicKey pk = KeyFactory.getInstance("Ed25519")
            .generatePublic(new X509EncodedKeySpec(PUB_DER));
        Signature s = Signature.getInstance("Ed25519");
        s.initVerify(pk);
        s.update(payload);
        return s.verify(sig);
    }

    /** Remove the stored key and drop premium state (keeps cape unlock). */
    public static void deactivate() {
        try {
            if (!API.isEmpty() && Files.exists(keyFile()))
                serverCall("/deactivate", Files.readString(keyFile()).trim());
            Files.deleteIfExists(keyFile());
        } catch (Exception ignored) {}
        premium = false; licenseId = ""; expires = 0; lastValid = 0;
        saveState();
    }
    /** Days left on the active license, 0 when none/expired. */
    public static long daysLeft() {
        return premium() && expires > 0 ? Math.max(0, (expires - System.currentTimeMillis()) / 86_400_000L) : 0;
    }
    /** Activation timestamp (key file mtime), 0 when not activated. */
    public static long activatedAt() {
        try { return Files.exists(keyFile()) ? Files.getLastModifiedTime(keyFile()).toMillis() : 0; }
        catch (Exception e) { return 0; }
    }

    /* ---------- persisted state: grace + clock-rollback ---------- */
    private static void loadState() {
        try {
            Path f = stateFile();
            if (!Files.exists(f)) return;
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            long lv = o.has("lastValid") ? o.get("lastValid").getAsLong() : 0;
            long ls = o.has("lastSeen") ? o.get("lastSeen").getAsLong() : 0;
            // ConfigSig: reject hand-edited grace/lastSeen (free-premium path)
            String sig = o.has("sig") ? o.get("sig").getAsString() : "";
            if (!dev.noxclient.security.ConfigSig.verify(
                    "license-state", lv + "|" + ls, sig)) {
                lastValid = 0; premium = false;
            } else {
                lastValid = lv;
                lastSeen = Math.max(lastSeen, ls);
                if (clockRolledBack()) premium = false;
            }
        } catch (Exception ignored) {}
    }

    private static void saveState() {
        try {
            Path f = stateFile();
            Files.createDirectories(f.getParent());
            lastSeen = Math.max(lastSeen, System.currentTimeMillis());
            String sig = dev.noxclient.security.ConfigSig.tag(
                "license-state", lastValid + "|" + lastSeen);
            Files.writeString(f, "{\"lid\":" + json(licenseId) + ",\"lastValid\":" + lastValid
                + ",\"lastSeen\":" + lastSeen + ",\"sig\":\"" + sig + "\"}");
        } catch (Exception ignored) {}
    }

    private static Path keyFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("nox/license.key");
    }

    private static Path capeFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("nox/cape.key");
    }

    private static Path stateFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("nox/license.state");
    }
}
