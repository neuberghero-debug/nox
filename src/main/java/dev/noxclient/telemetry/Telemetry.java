package dev.noxclient.telemetry;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Opt-in telemetry (Modul 20). DEFAULT OFF — first-start popup asks once.
 *
 * Sends ONLY: module toggles (names), session length, crash flag,
 * feature usage counters, client/MC version, os.name.
 * NEVER: server IPs, player names, coords, inventory, chat, paths.
 * TelemetrySanitizer strips anything else before queueing.
 *
 * Transport: batched (max 50 events / 5 min), HMAC-signed, own endpoint.
 * Endpoint empty → buffer only, nothing leaves the machine.
 */
public final class Telemetry {
    /** Transport, resolved once at class load, in order:
     *  1) -Dnox.telemetry.url  2) $NOX_TELEMETRY_URL  3) nox/telemetry.url file.
     *  Point it at any JSON POST endpoint (Discord webhook works — payloads
     *  are wrapped as `content` automatically). Empty → buffer only. */
    private static final String ENDPOINT = endpoint();
    private static final String SECRET = System.getProperty("nox.telemetry.hmac",
        System.getenv().getOrDefault("NOX_TELEMETRY_HMAC", ""));
    private static final long FLUSH_MS = 5 * 60_000L;
    private static final int MAX_BATCH = 50;
    private static final int MAX_QUEUE = 500;
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(4)).build();

    private static final ConcurrentLinkedQueue<JsonObject> queue = new ConcurrentLinkedQueue<>();
    private static volatile boolean enabled;      // user opt-in
    private static volatile boolean asked;        // first-start popup shown
    private static long sessionStart, lastFlush;

    public static boolean enabled() { return enabled; }
    public static boolean asked() { return asked; }

    public static void init() {
        sessionStart = System.currentTimeMillis();
        loadState();
        if (enabled) event("session_start", null);
    }

    /** Called by the privacy popup / settings toggle. */
    public static void setEnabled(boolean v) {
        if (v == enabled) return;
        enabled = v;
        asked = true;
        saveState();
        if (v) event("opt_in", null);
        else { queue.clear(); event("opt_out", null); flushSync(); }
    }

    public static void markAsked() { asked = true; saveState(); }

    /** Queue one sanitized event. Never throws. */
    public static void event(String type, JsonObject data) {
        if (!enabled) return;
        try {
            JsonObject e = new JsonObject();
            e.addProperty("t", type);
            e.addProperty("ts", System.currentTimeMillis());
            if (data != null) e.add("d", TelemetrySanitizer.scrub(data));
            if (queue.size() >= MAX_QUEUE) queue.poll();
            queue.offer(e);
        } catch (Throwable ignored) {}
    }

    public static void moduleToggled(String name, boolean on) {
        JsonObject d = new JsonObject();
        d.addProperty("m", name);
        d.addProperty("on", on);
        event("module_toggle", d);
    }

    /** Tick — batch flush every 5 min when enabled + endpoint set. */
    public static void tick() {
        if (!enabled || ENDPOINT.isEmpty() || queue.isEmpty()) return;
        if (System.currentTimeMillis() - lastFlush < FLUSH_MS) return;
        lastFlush = System.currentTimeMillis();
        Thread.ofVirtual().start(Telemetry::flushSync);
    }

    /** Shutdown hook — final flush incl. session_length. */
    public static void shutdown() {
        if (!enabled) return;
        JsonObject d = new JsonObject();
        d.addProperty("ms", System.currentTimeMillis() - sessionStart);
        event("session_end", d);
        flushSync();
    }

    private static void flushSync() {
        if (ENDPOINT.isEmpty()) return;
        try {
            JsonArray batch = new JsonArray();
            JsonObject e;
            int n = 0;
            while (n < MAX_BATCH && (e = queue.poll()) != null) { batch.add(e); n++; }
            if (n == 0) return;
            JsonObject body = new JsonObject();
            body.addProperty("v", dev.noxclient.update.UpdateManager.currentVersion());
            body.addProperty("mc", Minecraft.getInstance().getLaunchedVersion());
            body.addProperty("os", System.getProperty("os.name", "?"));
            body.add("events", batch);
            // discord webhooks only surface `content` — wrap the batch so the
            // payload is actually readable in the channel
            String raw = body.toString();
            if (ENDPOINT.contains("discord.com/api/webhooks") || ENDPOINT.contains("discordapp.com/api/webhooks")) {
                if (raw.length() > 1800) raw = raw.substring(0, 1800) + "…";
                raw = "{\"content\":\"```json\\n" + raw.replace("\\", "\\\\")
                    .replace("\"", "\\\"").replace("\n", "\\n") + "\\n```\"}";
            }
            byte[] bytes = raw.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(ENDPOINT))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(6))
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes));
            if (!SECRET.isEmpty())
                b.header("x-nox-sig", hmac(bytes, SECRET));
            HTTP.send(b.build(), HttpResponse.BodyHandlers.discarding());
        } catch (Throwable ignored) {}
    }

    private static String endpoint() {
        String e = System.getProperty("nox.telemetry.url",
            System.getenv().getOrDefault("NOX_TELEMETRY_URL", "")).trim();
        if (!e.isEmpty()) return e;
        try {
            var f = Minecraft.getInstance().gameDirectory.toPath().resolve("nox/telemetry.url");
            if (Files.exists(f)) return Files.readString(f).trim();
        } catch (Throwable ignored) {}
        return "";
    }

    private static String hmac(byte[] data, String secret) throws Exception {
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
            secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        return java.util.HexFormat.of().formatHex(mac.doFinal(data));
    }

    /* ---------- persisted opt-in state (nox/privacy.json) ---------- */
    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("nox/privacy.json");
    }
    private static void loadState() {
        try {
            if (!Files.exists(file())) return;
            JsonObject o = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
            enabled = o.has("telemetry") && o.get("telemetry").getAsBoolean();
            asked = o.has("asked") && o.get("asked").getAsBoolean();
        } catch (Throwable ignored) {}
    }
    private static void saveState() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), "{\"telemetry\":" + enabled + ",\"asked\":" + asked + "}");
        } catch (Throwable ignored) {}
    }
}
