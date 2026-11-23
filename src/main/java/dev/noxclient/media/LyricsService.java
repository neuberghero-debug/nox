package dev.noxclient.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

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
    private static final Pattern CUE = Pattern.compile("\\[(\\d{1,6}):([0-5]?\\d(?:\\.\\d+)?)\\]");
    private static volatile Catalog catalog;
    private static volatile Thread worker;

    record Track(String artist, String title) {
        boolean matches(MediaService.Playback p) {
            return artist.equals(p.artist()) && title.equals(p.title());
        }
    }

    private record Catalog(Track track, TreeMap<Integer, String> lines, String plain) {}

    public static synchronized void start() {
        if (worker != null) return;
        worker = new Thread(LyricsService::loop, "nox-lyrics");
        worker.setDaemon(true);
        worker.start();
    }

    public static synchronized void stop() {
        Thread t = worker;
        worker = null;
        catalog = null;
        if (t != null) t.interrupt();
    }

    private static void loop() {
        Thread self = Thread.currentThread();
        Track last = null;
        while (worker == self && !self.isInterrupted()) {
            var p = MediaService.snapshot();
            if (p.active() && !p.artist().isEmpty()) {
                if (last == null || !last.matches(p)) {
                    last = new Track(p.artist(), p.title());
                    Catalog fetched = fetch(last, p.durationUs() / 1e6);
                    synchronized (LyricsService.class) {
                        if (worker != self) return;
                        if (last.matches(MediaService.snapshot())) catalog = fetched;
                        else last = null;
                    }
                }
            } else {
                last = null;
                synchronized (LyricsService.class) {
                    if (worker != self) return;
                    catalog = null;
                }
            }
            try { Thread.sleep(1_500); }
            catch (InterruptedException e) { return; }
        }
    }

    private static Catalog fetch(Track track, double durSec) {
        String artist = track.artist(), title = track.title();
        try {
            JsonObject hit = null;
            String url = "https://lrclib.net/api/get?artist_name=" + enc(artist)
                + "&track_name=" + enc(title);
            if (durSec > 0) url += "&duration=" + (int) durSec;
            JsonObject r = get(url);
            if (r != null && (r.has("syncedLyrics") || r.has("plainLyrics"))) hit = r;
            if (hit == null && !Thread.currentThread().isInterrupted()) {
                JsonArray rs = getArr("https://lrclib.net/api/search?track_name="
                    + enc(title) + "&artist_name=" + enc(artist));
                if (rs != null) {
                    for (var e : rs) {
                        if (!e.isJsonObject()) continue;
                        JsonObject o = e.getAsJsonObject();
                        if (str(o, "syncedLyrics") != null) { hit = o; break; }
                        if (hit == null) hit = o;
                    }
                }
            }
            if (hit == null) return null;
            String lrc = str(hit, "syncedLyrics");
            String plain = str(hit, "plainLyrics");
            return new Catalog(track, lrc == null ? new TreeMap<>() : parseLrc(lrc),
                plain == null ? "" : plain);
        } catch (RuntimeException e) { return null; }
    }

    static TreeMap<Integer, String> parseLrc(String lrc) {
        TreeMap<Integer, String> cues = new TreeMap<>();
        for (String row : lrc.split("\\R")) {
            var matcher = CUE.matcher(row);
            int end = 0;
            while (matcher.region(end, row.length()).lookingAt()) end = matcher.end();
            if (end == 0) continue;
            String text = row.substring(end).strip();
            if (text.isEmpty()) continue;
            matcher.region(0, end);
            while (matcher.find()) {
                long ms = 60_000L * Integer.parseInt(matcher.group(1))
                    + Math.round(1_000 * Double.parseDouble(matcher.group(2)));
                if (ms <= Integer.MAX_VALUE) cues.put((int) ms, text);
            }
        }
        return cues;
    }

    private static Catalog forTrack(MediaService.Playback p) {
        Catalog c = catalog;
        return c != null && c.track().matches(p) ? c : null;
    }

    public static String current() { return line(0); }
    public static String prev() { return line(-1); }
    public static String next() { return line(1); }

    private static String line(int direction) {
        var p = MediaService.snapshot();
        Catalog c = forTrack(p);
        if (c == null) return "";
        var cues = c.lines();
        Map.Entry<Integer, String> cue = cues.floorEntry((int) Math.min(Integer.MAX_VALUE, p.positionUs() / 1_000));
        if (cue == null) return "";
        if (direction < 0) cue = cues.lowerEntry(cue.getKey());
        else if (direction > 0) cue = cues.higherEntry(cue.getKey());
        return cue == null ? "" : cue.getValue();
    }

    public static boolean found() {
        Catalog c = forTrack(MediaService.snapshot());
        return c != null && (!c.lines().isEmpty() || !c.plain().isEmpty());
    }

    public static String plain() {
        Catalog c = forTrack(MediaService.snapshot());
        return c == null ? "" : c.plain();
    }

    private static JsonObject get(String url) {
        try {
            var resp = HTTP.send(HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "NOX-Client/0.1 (https://noxclient.dev)")
                    .timeout(Duration.ofSeconds(8)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200 ? JsonParser.parseString(resp.body()).getAsJsonObject() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static JsonArray getArr(String url) {
        try {
            var resp = HTTP.send(HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "NOX-Client/0.1 (https://noxclient.dev)")
                    .timeout(Duration.ofSeconds(8)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200 ? JsonParser.parseString(resp.body()).getAsJsonArray() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }
    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
