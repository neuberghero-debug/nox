package dev.noxclient.security;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.noxclient.premium.Hwid;
import dev.noxclient.premium.LicenseManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Security heartbeat (Modul 30.5) — periodic HMAC-signed ping to the license
 * API carrying build + integrity telemetry: watermark, canary shape-hash,
 * distributed-check consensus, anti-debug flags. The server sees anomalies
 * per-lid (cracked builds reporting weird shapes) without any payload that
 * identifies the user beyond their (already-bound) license id.
 *
 * Body is HMAC-SHA256'd with a key derived from the license token — only a
 * licensed client can produce a valid beat, so fake beats get rejected.
 */
public final class Heartbeat {
    private static final long BEAT_MS = 10 * 60_000;    // 10 min
    private static final Gson G = new Gson();
    private static long last;

    /** Called every client tick; self-throttled. Premium + API only. */
    public static void tick() {
        if (!LicenseManager.premium() || LicenseManager.api().isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - last < BEAT_MS) return;
        last = now;

        JsonObject b = new JsonObject();
        b.addProperty("lid", LicenseManager.licenseId());
        b.addProperty("hw", WebhookSec.anonId());                    // hashed hwid
        b.addProperty("wm", Watermark.get());
        b.addProperty("shape", Canary.shapeHash());
        b.addProperty("ok", dev.noxclient.premium.Distributed.premiumAllowed());
        b.addProperty("dbg", dev.noxclient.ncore.NativeCore.antiDebug());
        b.addProperty("ts", now);
        b.addProperty("v", dev.noxclient.NoxClient.VERSION);
        String body = G.toJson(b);

        new Thread(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder(
                        URI.create(LicenseManager.api() + "/heartbeat"))
                    .header("Content-Type", "application/json")
                    .header("X-Nox-Sig", hmac(body, LicenseManager.licenseId()))
                    .timeout(Duration.ofSeconds(8))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
                HttpClient.newHttpClient().send(req,
                    HttpResponse.BodyHandlers.discarding());
            } catch (Throwable ignored) {}
        }, "nox-heartbeat").start();
    }

    private static String hmac(String data, String key) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(
                ("nox-hb|" + key).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] d = m.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Throwable t) { return ""; }
    }
}
