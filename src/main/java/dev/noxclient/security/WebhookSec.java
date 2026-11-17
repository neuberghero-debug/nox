package dev.noxclient.security;

import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * SECURITY-BLOCK webhook transport — shared by TamperWarner (11) and
 * CrashReport (12). Hardened per spec:
 *
 * - rate-limit: max {@value #MAX_PER_MIN} posts per 60 s window (queue, not
 *   drop, up to {@value #MAX_QUEUE})
 * - dedup: identical (channel, fingerprint) suppressed for {@value #DEDUP_MS}
 * - sanitize: strips session tokens, mojang/MS bearer strings, keys
 * - anonymize: identity is the salted HWID hash, never a raw id/ip/path
 * - 8 KB payload cap
 * - signed: HMAC-SHA256 over body, sent as X-Nox-Sig so the relay bot can
 *   verify authenticity (key: NOX_SEC_KEY env, absent → unsigned ok=false)
 * - retry ≤3 silently (429/5xx only), daemon threads only
 */
public final class WebhookSec {
    private static final int MAX_PER_MIN = 5;
    private static final int MAX_QUEUE = 20;
    private static final int MAX_BODY = 8 * 1024;
    private static final long DEDUP_MS = 60_000;
    private static final int MAX_RETRY = 3;

    private static final Deque<Long> sentAt = new ArrayDeque<>();
    private static final Deque<JsonObject> queue = new ArrayDeque<>();
    private static final ConcurrentHashMap<String, Long> dedup = new ConcurrentHashMap<>();
    private static volatile boolean draining;
    private static final String SEC_KEY = System.getenv("NOX_SEC_KEY");
    public static final String ALERT_WEBHOOK = System.getenv().getOrDefault(
        "NOX_SECURITY_WEBHOOK", "https://discord.com/api/webhooks/REPLACE_ME");

    /** Enqueue a security alert. fingerprint should be stable per incident type. */
    public static void alert(String title, String detail, String fingerprint) {
        if (ALERT_WEBHOOK.contains("REPLACE_ME")) return;
        String fp = sha256((title + "|" + fingerprint).getBytes(StandardCharsets.UTF_8));
        Long last = dedup.get(fp);
        long now = System.currentTimeMillis();
        if (last != null && now - last < DEDUP_MS) return;
        dedup.put(fp, now);
        if (dedup.size() > 64) dedup.clear();

        JsonObject o = new JsonObject();
        o.addProperty("username", "NOX Security");
        o.addProperty("content",
            "**" + esc(title) + "**\n```\n" + sanitize(detail) + "\n```");
        o.addProperty("allowed_mentions", "");
        if (queue.size() >= MAX_QUEUE) queue.pollLast();
        queue.addLast(o);
        drain();
    }

    /** True while a security channel is configured (env webhook set). */
    public static boolean available() { return !ALERT_WEBHOOK.contains("REPLACE_ME"); }

    private static void drain() {
        synchronized (WebhookSec.class) {
            if (draining) return;
            draining = true;
        }
        Thread t = new Thread(() -> {
            try {
                while (true) {
                    JsonObject o;
                    long now = Instant.now().toEpochMilli();
                    synchronized (WebhookSec.class) {
                        while (!sentAt.isEmpty() && now - sentAt.peekFirst() > 60_000)
                            sentAt.pollFirst();
                        if (queue.isEmpty()) { draining = false; return; }
                        if (sentAt.size() >= MAX_PER_MIN) {
                            // rate-limited → reschedule, don't drop
                            scheduleDrain();
                            return;
                        }
                        o = queue.pollFirst();
                        sentAt.addLast(now);
                    }
                    post(o, 0);
                }
            } finally {
                synchronized (WebhookSec.class) { draining = false; }
            }
        }, "nox-sec-webhook");
        t.setDaemon(true); t.start();
    }

    private static void scheduleDrain() {
        Thread t = new Thread(() -> {
            try { Thread.sleep(15_000); } catch (InterruptedException ignored) {}
            synchronized (WebhookSec.class) { draining = false; }
            drain();
        }, "nox-sec-webhook-wait");
        t.setDaemon(true); t.start();
    }

    private static void post(JsonObject body, int attempt) {
        try {
            byte[] raw = body.toString().getBytes(StandardCharsets.UTF_8);
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(ALERT_WEBHOOK))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofByteArray(raw));
            if (SEC_KEY != null && !SEC_KEY.isEmpty())
                b.header("X-Nox-Sig", hmac(raw, SEC_KEY));
            int code = HttpClient.newHttpClient().send(b.build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
            if ((code == 429 || code >= 500) && attempt < MAX_RETRY)
                retry(body, attempt + 1);
        } catch (Throwable t) {
            if (attempt < MAX_RETRY) retry(body, attempt + 1);
        }
    }

    private static void retry(JsonObject body, int attempt) {
        Thread t = new Thread(() -> {
            try { Thread.sleep(2000L * attempt); } catch (InterruptedException ignored) {}
            post(body, attempt);
        }, "nox-sec-retry");
        t.setDaemon(true); t.start();
    }

    /** Strip secrets before they ever leave the process. */
    public static String sanitize(String s) {
        if (s == null) return "";
        String out = s
            .replaceAll("(?i)(token|key|secret|password|sig)[\"']?\\s*[:=]\\s*[\"']?[\\w.\\-+/=]{8,}", "$1=[redacted]")
            .replaceAll("[A-Za-z0-9_\\-]{20,}\\.[A-Za-z0-9_\\-]{20,}\\.[A-Za-z0-9_\\-]{8,}", "[redacted-jwt]")
            .replaceAll("mfa\\.[\\w\\-]{20,}", "[redacted-mfa]")
            .replaceAll("NOX1\\.[\\w\\-]+\\.[\\w\\-]+", "NOX1.[redacted]");
        if (out.length() > MAX_BODY) out = out.substring(0, MAX_BODY) + "…";
        return out;
    }

    /** Anonymized actor id — salted HWID hash, 16 hex chars. */
    public static String anonId() {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256")
                .digest(("nox-actor|" + dev.noxclient.ncore.NativeCore.hwid())
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", h[i]));
            return sb.toString();
        } catch (Throwable t) { return "unknown"; }
    }

    private static String hmac(byte[] data, String key) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return sha256b(m.doFinal(data));
        } catch (Throwable t) { return ""; }
    }

    private static String sha256(byte[] d) {
        try { return sha256b(MessageDigest.getInstance("SHA-256").digest(d)); }
        catch (Throwable t) { return "0"; }
    }

    private static String sha256b(byte[] d) {
        StringBuilder sb = new StringBuilder(64);
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String esc(String s) { return s.replace("`", "'"); }
}
