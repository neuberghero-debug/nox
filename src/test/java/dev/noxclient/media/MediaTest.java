package dev.noxclient.media;

import java.util.Map;

/** Check player metadata formats and timed lyrics without spawning players or making HTTP requests. */
public final class MediaTest {
    private static void expect(boolean good, String message) {
        if (!good) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var apple = MediaService.parseAppleScript("Artist\u0001Song\u0001playing\u000112.5\u0001210000");
        expect(apple.playing() && apple.positionUs() == 12_500_000 && apple.durationUs() == 210_000_000,
            "AppleScript: textual status, seconds position, milliseconds duration");
        var playing = MediaService.parseNowPlaying("Artist\r\nSong\r\n1.0\r\n12.5\r\n210\r\n");
        expect(playing.equals(apple), "nowplaying-cli: numeric rate and both times in seconds");
        var paused = MediaService.parseNowPlaying("Artist\nSong\n0\n12.5\n210");
        expect(paused.active() && !paused.playing(), "nowplaying-cli: paused track remains active");
        expect(!MediaService.parseAppleScript("").active(), "empty Spotify response must allow fallback");
        expect(!MediaService.parseNowPlaying("null\nnull\nnull\nnull\nnull").active(), "missing track is inactive");
        var mpris = MediaService.parsePlayerctl("\u0001Song\u0001Playing\u000112500000\u0001210000000");
        expect(mpris.active() && mpris.artist().isEmpty() && mpris.durationUs() == 210_000_000,
            "MPRIS preserves a missing leading artist field and uses microseconds");
        expect(!MediaService.parsePlayerctl("Artist\u0001Song\u0001Stopped\u00010\u00010").active(),
            "stopped MPRIS track is inactive");
        var lines = LyricsService.parseLrc("[ar:Artist]\r\n[00:01.20][01:03.45] Keep [this]\r\n"
            + "[00:10.000]Following\n[00:NaN]Ignore");
        expect(lines.equals(Map.of(1200, "Keep [this]", 63450, "Keep [this]", 10_000, "Following")),
            "LRC supports multiple timestamps and preserves brackets in lyric text");
        var oldTrack = new LyricsService.Track("X - Y", "Z");
        expect(!oldTrack.matches(new MediaService.Playback("X", "Y - Z", true, 0, 0)),
            "structured track identity distinguishes colliding string concatenations");
        System.out.println("Media checks passed: MPRIS, macOS formats, track identity, LRC.");
    }
}
