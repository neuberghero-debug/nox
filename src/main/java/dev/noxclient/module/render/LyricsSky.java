package dev.noxclient.module.render;

import dev.noxclient.media.LyricsService;
import dev.noxclient.media.MediaService;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/**
 * LyricsSky — synced lyrics floated over the NOX sky (title screen) and
 * optionally as a karaoke strip in-game. Powered by lrclib.net + MediaService.
 */
public class LyricsSky extends Module {
    public final Setting.Bool inGame = add(new Setting.Bool("In-game strip", true));
    public final Setting.Bool titleScreen = add(new Setting.Bool("On title sky", true));
    public final Setting.Num lines = add(new Setting.Num("Context lines", 1, 0, 3));

    public LyricsSky() { super("LyricsSky", "Synced lyrics over the sky", Category.RENDER); }

    @Override public void onEnable() {
        MediaService.start(MediaService.Consumer.LYRICS_SKY);
        LyricsService.start();
    }

    @Override protected void onDisable() {
        LyricsService.stop();
        MediaService.stop(MediaService.Consumer.LYRICS_SKY);
    }
}
