package dev.noxclient.module.render;

import dev.noxclient.media.TwitchService;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Twitch chat HUD — anonymous read (justinfan IRC), stream status dot. */
public class TwitchChat extends Module {
    public final Setting.Str channel = add(new Setting.Str("Channel", "shroud"));
    public final Setting.Num lines = add(new Setting.Num("Lines", 5, 2, 10));
    public final Setting.Bool liveDot = add(new Setting.Bool("Live indicator", true));
    public final Setting.Num posX = add(new Setting.Num("X", 4, 0, 2000));
    public final Setting.Num posY = add(new Setting.Num("Y", 300, 0, 1200));

    private String lastChan = "";

    public TwitchChat() { super("TwitchChat", "Twitch chat overlay", Category.RENDER); }

    @Override
    public void onTick() {
        String ch = channel.get();
        if (!ch.equals(lastChan) || isEnabled()) {
            TwitchService.start(ch, lines.intVal());
            lastChan = ch;
        }
    }
}
