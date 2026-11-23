package dev.noxclient.module.render;

import dev.noxclient.media.MediaService;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/**
 * Spotify/MPRIS HUD widget — shows current track + progress in the overlay.
 * Reads via playerctl so it works for any MPRIS player on Bluefin.
 */
public class SpotifyHud extends Module {
    public final Setting.Bool progressBar = add(new Setting.Bool("Progress bar", true));
    public final Setting.Bool pausedToo = add(new Setting.Bool("Show when paused", true));
    public final Setting.Num posX = add(new Setting.Num("X", 4, 0, 2000));
    public final Setting.Num posY = add(new Setting.Num("Y", 60, 0, 1200));

    public SpotifyHud() { super("SpotifyHud", "Spotify now-playing widget", Category.RENDER); }

    @Override public void onEnable() { MediaService.start(MediaService.Consumer.SPOTIFY_HUD); }
    @Override protected void onDisable() { MediaService.stop(MediaService.Consumer.SPOTIFY_HUD); }
}
