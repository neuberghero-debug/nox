package dev.noxclient.media;

/**
 * Now-playing tracker, cross-platform:
 *   Linux   → playerctl (MPRIS/dbus: Spotify + most players)
 *   macOS   → osascript "tell application Spotify" (or nowplaying-cli)
 *   Windows → playerctl if present, else Spotify window-title scrape
 * Polls in a daemon thread; silently no-ops with no player.
 */
public final class MediaService {
    private static volatile String artist = "", title = "", status = "Stopped";
    private static volatile long posUs, lenUs;
    private static volatile boolean started;
    private static final String OS = System.getProperty("os.name").toLowerCase();

    public static void start() {
        if (started) return;
        started = true;
        Thread t = new Thread(MediaService::loop, "nox-media");
        t.setDaemon(true);
        t.start();
    }

    private static void loop() {
        while (true) {
            try {
                if (OS.contains("mac")) pollMac();
                else if (OS.contains("win")) pollWindows();
                else pollLinux();
            } catch (Throwable e) {
                clear();
            }
            try { Thread.sleep(2_000); } catch (InterruptedException ignored) {}
        }
    }

    private static void pollLinux() {
        // prefer the real Spotify player — bare `playerctl` picks whichever
        // MPRIS source was last active (browsers steal it constantly)
        String out = run("playerctl", "-p", "spotify", "metadata", "--format",
            "{{artist}}\u0001{{title}}\u0001{{status}}\u0001{{position}}\u0001{{mpris:length}}");
        if (out == null || !out.contains("\u0001"))
            out = run("playerctl", "metadata", "--format",
            "{{artist}}\u0001{{title}}\u0001{{status}}\u0001{{position}}\u0001{{mpris:length}}");
        if (out == null || !out.contains("\u0001")) { clear(); return; }
        String[] f = out.split("\u0001", -1);
        artist = f[0]; title = f.length > 1 ? f[1] : "";
        status = f.length > 2 ? f[2] : "Stopped";
        posUs = f.length > 3 ? parse(f[3]) : 0;
        lenUs = f.length > 4 ? parse(f[4]) : 0;
    }

    private static void pollMac() {
        // Spotify.app first (most common), then nowplaying-cli for any app
        String out = run("osascript", "-e",
            "if application \"Spotify\" is running then tell application \"Spotify\" "
            + "to (artist of current track) & \"\u0001\" & (name of current track) & \"\u0001\" "
            + "& (player state as string) & \"\u0001\" & (player position) & \"\u0001\" "
            + "& (duration of current track)");
        if (out == null)
            out = run("nowplaying-cli", "get", "artist", "title", "playbackRate", "elapsedTime", "duration");
        if (out == null) { clear(); return; }
        String[] f = out.split("\u0001|\n", -1);
        artist = f[0].trim(); title = f.length > 1 ? f[1].trim() : "";
        status = (f.length > 2 && f[2].toLowerCase().contains("play")
                  && !f[2].trim().equals("0")) ? "Playing" : "Paused";
        posUs = f.length > 3 ? (long) (parseD(f[3]) * 1e6) : 0;
        lenUs = f.length > 4 ? (long) (parseD(f[4]) * 1e3) : 0;   // osascript duration = ms
        if (title.isEmpty()) clear();
    }

    private static void pollWindows() {
        // playerctl exists on some setups (scoop); else Spotify window title
        String out = run("playerctl", "metadata", "--format",
            "{{artist}}\u0001{{title}}\u0001{{status}}\u0001{{position}}\u0001{{mpris:length}}");
        if (out != null && out.contains("\u0001")) { applyPlayerctl(out); return; }
        out = run("powershell", "-NoProfile", "-Command",
            "(Get-Process -Name Spotify -ErrorAction SilentlyContinue "
            + "| Where-Object {$_.MainWindowTitle -ne ''} "
            + "| Select-Object -First 1 -ExpandProperty MainWindowTitle)");
        if (out == null || out.isEmpty() || out.startsWith("Spotify")
            || out.equalsIgnoreCase("Spotify Premium") || out.equalsIgnoreCase("Spotify Free")) {
            if (out == null || !status.equals("Paused")) clear();
            return;   // playing state → title is just "Spotify"
        }
        // paused → title is "Artist - Title"
        int sep = out.indexOf(" - ");
        if (sep > 0) {
            artist = out.substring(0, sep).trim();
            title = out.substring(sep + 3).trim();
            status = "Paused"; posUs = lenUs = 0;
        } else clear();
    }

    private static void applyPlayerctl(String out) {
        String[] f = out.split("\u0001", -1);
        artist = f[0]; title = f.length > 1 ? f[1] : "";
        status = f.length > 2 ? f[2] : "Stopped";
        posUs = f.length > 3 ? parse(f[3]) : 0;
        lenUs = f.length > 4 ? parse(f[4]) : 0;
    }

    private static void clear() { artist = title = ""; status = "Stopped"; posUs = lenUs = 0; }

    private static String run(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            // a hung playerctl/wmic would block the media thread forever —
            // hard-kill after 2s
            if (!p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            String out = new String(p.getInputStream().readAllBytes()).trim();
            return p.exitValue() == 0 ? out : null;
        } catch (Throwable e) { return null; }
    }

    private static long parse(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }
    private static double parseD(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
    }

    public static String artist() { return artist; }
    public static String title() { return title; }
    public static boolean playing() { return "Playing".equalsIgnoreCase(status); }
    public static boolean active() { return !title.isEmpty(); }
    public static double progress() { return lenUs > 0 ? Math.min(1, posUs / (double) lenUs) : 0; }
    public static double positionSec() { return posUs / 1e6; }
    public static double lengthSec() { return lenUs / 1e6; }
}
