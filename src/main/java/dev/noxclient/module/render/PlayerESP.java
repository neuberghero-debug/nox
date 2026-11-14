package dev.noxclient.module.render;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Player ESP data — drawn by NoxOverlay (box + name + distance + health). */
public class PlayerESP extends Module {
    public final Setting.Bool box = add(new Setting.Bool("Box", true));
    public final Setting.Bool name = add(new Setting.Bool("Nametag", true));
    public final Setting.Bool distance = add(new Setting.Bool("Distance", true));
    public final Setting.Bool health = add(new Setting.Bool("Health", true));
    public final Setting.Num maxDist = add(new Setting.Num("Max distance", 256, 32, 1024));

    public PlayerESP() { super("PlayerESP", "Players through walls", Category.RENDER); }
}
