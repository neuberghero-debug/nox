package dev.noxclient.media;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Now-playing tracker, cross-platform:
 *   Linux   → playerctl (MPRIS/dbus: Spotify + most players)
 *   macOS   → osascript "tell application Spotify" (or nowplaying-cli)
 *   Windows → playerctl if present, else Spotify window-title scrape
 * Polls in a daemon thread; silently no-ops with no player.
 */
public final class MediaService {
    public enum Consumer { SPOTIFY_HUD, LYRICS_SKY }

    record Playback(String artist, String title, boolean playing, long positionUs, long durationUs) {
        boolean active() { return !title.isEmpty(); }
    }

    private static final String OS = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    private static final String FORMAT =
        "{{artist}}\u0001{{title}}\u0001{{status}}\u0001{{position}}\u0001{{mpris:length}}";
    private static final Playback EMPTY = new Playback("", "", false, 0, 0);
    private static final EnumSet<Consumer> consumers = EnumSet.noneOf(Consumer.class);
    private static volatile Playback playback = EMPTY;
    private static volatile Thread worker;

    public static synchronized void start(Consumer consumer) {
        consumers.add(consumer);
        if (worker != null) return;
        worker = new Thread(MediaService::loop, "nox-media");
        worker.setDaemon(true);
        worker.start();
    }

    public static synchronized void stop(Consumer consumer) {
        if (!consumers.remove(consumer) || !consumers.isEmpty()) return;
        Thread t = worker;
        worker = null;
        playback = EMPTY;
        if (t != null) t.interrupt();
    }

    private static void loop() {
        Thread self = Thread.currentThread();
        while (worker == self && !self.isInterrupted()) {
            Playback polled = OS.contains("mac") ? pollMac()
                : OS.contains("win") ? pollWindows() : pollLinux();
            synchronized (MediaService.class) {
                if (worker != self) return;
                playback = polled;
            }
            try { Thread.sleep(2_000); }
            catch (InterruptedException e) { return; }
        }
    }

    private static Playback pollLinux() {
        Playback spotify = parsePlayerctl(run("playerctl", "-p", "spotify", "metadata", "--format", FORMAT));
        return spotify.active() ? spotify
            : parsePlayerctl(run("playerctl", "metadata", "--format", FORMAT));
    }

    private static Playback pollMac() {
        Playback spotify = parseAppleScript(run("osascript", "-e",
            "if application \"Spotify\" is running then tell application \"Spotify\" "
            + "to (artist of current track) & \"\u0001\" & (name of current track) & \"\u0001\" "
            + "& (player state as string) & \"\u0001\" & (player position) & \"\u0001\" "
            + "& (duration of current track)"));
        return spotify.active() ? spotify : parseNowPlaying(run("nowplaying-cli", "get",
            "artist", "title", "playbackRate", "elapsedTime", "duration"));
    }

    private static Playback pollWindows() {
        Playback mpris = parsePlayerctl(run("playerctl", "metadata", "--format", FORMAT));
        if (mpris.active()) return mpris;
        return parseWindowTitle(run("powershell", "-NoProfile", "-Command",
            "(Get-Process -Name Spotify -ErrorAction SilentlyContinue "
            + "| Where-Object {$_.MainWindowTitle -ne ''} "
            + "| Select-Object -First 1 -ExpandProperty MainWindowTitle)"));
    }

    static Playback parsePlayerctl(String out) {
        String[] f = fields(out, "\u0001");
        if (f == null || f[1].isBlank() || "Stopped".equalsIgnoreCase(f[2].strip())) return EMPTY;
        return new Playback(f[0].strip(), f[1].strip(), "Playing".equalsIgnoreCase(f[2].strip()),
            micros(f[3], 1), micros(f[4], 1));
    }

    static Playback parseAppleScript(String out) {
        String[] f = fields(out, "\u0001");
        if (f == null || f[1].isBlank() || "stopped".equalsIgnoreCase(f[2].strip())) return EMPTY;
        return new Playback(f[0].strip(), f[1].strip(), "playing".equalsIgnoreCase(f[2].strip()),
            micros(f[3], 1_000_000), micros(f[4], 1_000));
    }

    static Playback parseNowPlaying(String out) {
        String[] f = fields(out, "\\R");
        if (f == null || f[1].isBlank() || "null".equals(f[1].strip())) return EMPTY;
        String artist = f[0].strip();
        return new Playback("null".equals(artist) ? "" : artist, f[1].strip(), positive(f[2]) > 0,
            micros(f[3], 1_000_000), micros(f[4], 1_000_000));
    }

    private static Playback parseWindowTitle(String out) {
        if (out == null) return EMPTY;
        int sep = out.indexOf(" - ");
        if (sep <= 0 || out.substring(sep + 3).isBlank()) return EMPTY;
        return new Playback(out.substring(0, sep).strip(), out.substring(sep + 3).strip(), false, 0, 0);
    }

    private static String[] fields(String out, String separator) {
        if (out == null) return null;
        String[] f = out.split(separator, -1);
        return f.length >= 5 ? f : null;
    }

    private static String run(String... cmd) {
        if (Thread.currentThread().isInterrupted()) return null;
        Process p = null;
        try {
            p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            try (var output = p.getInputStream()) {
                if (!p.waitFor(2, TimeUnit.SECONDS) || p.exitValue() != 0) return null;
                // Leave leading empty fields and field separators intact.
                return new String(output.readNBytes(16_384), StandardCharsets.UTF_8).stripTrailing();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException e) {
            return null;
        } finally {
            if (p != null && p.isAlive()) p.destroyForcibly();
        }
    }

    private static double positive(String s) {
        try {
            double v = Double.parseDouble(s.strip());
            return Double.isFinite(v) && v > 0 ? v : 0;
        } catch (NumberFormatException e) { return 0; }
    }

    private static long micros(String s, int scale) {
        return Math.round(positive(s) * scale);
    }

    static Playback snapshot() { return playback; }
    public static String artist() { return snapshot().artist(); }
    public static String title() { return snapshot().title(); }
    public static boolean playing() { return snapshot().playing(); }
    public static boolean active() { return snapshot().active(); }
    public static double progress() {
        Playback p = snapshot();
        return p.durationUs() > 0 ? Math.min(1, p.positionUs() / (double) p.durationUs()) : 0;
    }
    public static double positionSec() { return snapshot().positionUs() / 1e6; }
    public static double lengthSec() { return snapshot().durationUs() / 1e6; }
}
