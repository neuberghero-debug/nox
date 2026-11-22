package dev.noxclient.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;

/**
 * Synced lyrics via lrclib.net — watches MediaService for track changes,
 * fetches LRC, exposes the line matching the current playback position.
 *   current()   → active line (karaoke highlight)
 *   prev()/next() → context lines
 *   found()     → whether lyrics exist for the track
 */
public final class LyricsService {
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(6)).build();
    private static volatile TreeMap<Integer, String> lines = new TreeMap<>();
    private static volatile String lastKey = "", plain = "";
    private static volatile boolean started;

    public static void start() {
        if (started) return;
        started = true;
        Thread t = new Thread(LyricsService::loop, "nox-lyrics");
        t.setDaemon(true);
        t.start();
    }

    private static void loop() {
        while (true) {
            try {
                String key = MediaService.artist() + " - " + MediaService.title();
                if (!key.equals(lastKey)) {
                    lastKey = key;
                    fetch(MediaService.artist(), MediaService.title(),
                        MediaService.lengthSec());
                }
            } catch (Throwable ignored) {}
            try { Thread.sleep(1_500); } catch (InterruptedException ignored) {}
        }
    }

    private static void fetch(String artist, String title, double durSec) {
        lines = new TreeMap<>(); plain = "";
        if (artist.isEmpty() || title.isEmpty()) return;
        try {
            JsonObject hit = null;
            // exact match endpoint first
            String url = "https://lrclib.net/api/get?artist_name=" + enc(artist)
                + "&track_name=" + enc(title);
            if (durSec > 0) url += "&duration=" + (int) durSec;
            JsonObject r = get(url);
            if (r != null && (r.has("syncedLyrics") || r.has("plainLyrics"))) hit = r;
            if (hit == null) {
                JsonArray rs = getArr("https://lrclib.net/api/search?track_name="
                    + enc(title) + "&artist_name=" + enc(artist));
                if (rs != null)
                    for (var e : rs) {
                        JsonObject o = e.getAsJsonObject();
                        if (o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull()) { hit = o; break; }
                        if (hit == null) hit = o;
                    }
            }
            if (hit == null) return;
            String lrc = str(hit, "syncedLyrics");
            if (lrc != null) parseLrc(lrc);
            else {
                String p = str(hit, "plainLyrics");
                plain = p == null ? "" : p;
            }
        } catch (Throwable ignored) {}
    }

    private static void parseLrc(String lrc) {
        TreeMap<Integer, String> m = new TreeMap<>();
        for (String row : lrc.split("\n")) {
            int idx = 0;
            while (idx < row.length() && row.charAt(idx) == '[') {
                int end = row.indexOf(']', idx);
                if (end < 0) break;
                int ms = stamp(row.substring(idx + 1, end));
                if (ms < 0) { idx = end + 1; continue; }
                String text = row.substring(row.lastIndexOf(']') + 1).trim();
                if (!text.isEmpty()) m.put(ms, text);
                idx = end + 1;
            }
        }
        lines = m;
    }

    private static int stamp(String s) {
        try {
            String[] mmss = s.split(":");
            double sec = Double.parseDouble(mmss[1]);
            return (int) ((Integer.parseInt(mmss[0]) * 60 + sec) * 1000);
        } catch (Exception e) { return -1; }
    }

    /** Line active at the current playback position, or "" */
    public static String current() {
        var snapshot = lines;
        if (snapshot.isEmpty()) return "";
        Map.Entry<Integer, String> e =
            snapshot.floorEntry((int) (MediaService.positionSec() * 1000));
        return e == null ? "" : e.getValue();
    }

    public static String prev() { return neighbor(-1); }
    public static String next() { return neighbor(1); }

    private static String neighbor(int dir) {
        // snapshot — a mid-read track switch must not pair a cue from the old
        // track with a neighbor from the freshly fetched one
        var snapshot = lines;
        if (snapshot.isEmpty()) return "";
        var cur = snapshot.floorEntry((int) (MediaService.positionSec() * 1000));
        if (cur == null) return "";
        var e = dir < 0 ? snapshot.lowerEntry(cur.getKey())
                        : snapshot.higherEntry(cur.getKey());
        return e == null ? "" : e.getValue();
    }

    public static boolean found() { return !lines.isEmpty() || !plain.isEmpty(); }
    public static String plain() { return plain; }

    private static JsonObject get(String url) {
        try {
            var resp = HTTP.send(HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "NOX-Client/0.1 (https://noxclient.dev)")
                    .timeout(Duration.ofSeconds(8)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200 ? JsonParser.parseString(resp.body()).getAsJsonObject() : null;
        } catch (Throwable e) { return null; }
    }

    private static JsonArray getArr(String url) {
        try {
            var resp = HTTP.send(HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "NOX-Client/0.1 (https://noxclient.dev)")
                    .timeout(Duration.ofSeconds(8)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200 ? JsonParser.parseString(resp.body()).getAsJsonArray() : null;
        } catch (Throwable e) { return null; }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }
    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
