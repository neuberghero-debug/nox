package dev.noxclient.module.render;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

public class Tracers extends Module {
    public final Setting.Bool players = add(new Setting.Bool("Players", true));
    public final Setting.Num maxDist = add(new Setting.Num("Max distance", 256, 16, 1024));
    public final Setting.Col color = add(new Setting.Col("Color", 0x66FFAA00));

    public Tracers() { super("Tracers", "Lines to entities", Category.RENDER); }
}
