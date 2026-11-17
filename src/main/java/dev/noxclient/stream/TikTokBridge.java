package dev.noxclient.stream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TikTok Live bridge — client side (Modul 1.3). Connects to the Python
 * sidecar at ws://127.0.0.1:8890 which normalizes TikTok events into
 *   {type,user,userId,value,giftName,coinValue,timestamp}
 * with type in GIFT|LIKE|FOLLOW|SHARE|COMMENT|SUBSCRIBE (likes bundled).
 * Reconnect: exponential backoff 1s→30s. Health: sidecar sends ping.
 */
public final class TikTokBridge {
    public record Event(String type, String user, String userId, long value,
                        String giftName, long coinValue, long timestamp) {}

    public interface Listener { void onEvent(Event e); }

    private static final String URL = System.getProperty("nox.tiktok.ws",
        System.getenv().getOrDefault("NOX_TIKTOK_WS", "ws://127.0.0.1:8890"));
    private static final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private static final AtomicLong received = new AtomicLong();
    private static volatile WebSocket ws;
    private static volatile boolean connected, wantConnect;
    private static volatile String lastEvent = "";
    private static long retryAt, backoff = 1000;
    private static int failures;

    public static boolean connected() { return connected; }
    public static long received() { return received.get(); }
    public static String lastEvent() { return lastEvent; }
    public static int failures() { return failures; }

    public static void addListener(Listener l) { listeners.add(l); }
    public static void removeListener(Listener l) { listeners.remove(l); }

    public static void connect() { wantConnect = true; retryAt = 0; }
    public static void disconnect() {
        wantConnect = false;
        WebSocket w = ws;
        if (w != null) try { w.sendClose(WebSocket.NORMAL_CLOSURE, "bye"); } catch (Throwable ignored) {}
        connected = false; ws = null;
    }

    /** Called every client tick — reconnect with exp backoff. */
    public static void tick() {
        if (!wantConnect || connected) return;
        long now = System.currentTimeMillis();
        if (now < retryAt) return;
        retryAt = now + backoff;
        backoff = Math.min(30_000, backoff * 2);
        try {
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build()
                .newWebSocketBuilder()
                .buildAsync(URI.create(URL), new WebSocket.Listener() {
                    @Override public void onOpen(WebSocket webSocket) {
                        connected = true; ws = webSocket; backoff = 1000; failures = 0;
                        webSocket.request(16);
                    }
                    @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        webSocket.request(1);
                        handle(String.valueOf(data));
                        return null;
                    }
                    @Override public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        connected = false; ws = null; return null;
                    }
                    @Override public void onError(WebSocket webSocket, Throwable error) {
                        connected = false; ws = null; failures++;
                    }
                });
        } catch (Throwable t) { failures++; }
    }

    static void handle(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            if (o.has("type") && "ping".equals(o.get("type").getAsString())) return;
            Event e = new Event(
                str(o, "type", "?"), str(o, "user", "?"), str(o, "userId", ""),
                num(o, "value"), str(o, "giftName", ""), num(o, "coinValue"),
                o.has("timestamp") ? o.get("timestamp").getAsLong() : System.currentTimeMillis());
            received.incrementAndGet();
            lastEvent = e.type() + " " + e.user() + (e.giftName().isEmpty() ? "" : " " + e.giftName());
            for (Listener l : listeners) l.onEvent(e);
        } catch (Throwable ignored) {}
    }

    private static String str(JsonObject o, String k, String d) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : d;
    }
    private static long num(JsonObject o, String k) {
        try { return o.has(k) ? o.get(k).getAsLong() : 0; } catch (Throwable t) { return 0; }
    }
}
