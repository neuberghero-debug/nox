package dev.noxclient.module.render;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** TargetHUD — live panel for the entity KillAura/Triggerbot is engaging. */
public class TargetHUD extends Module {
    public final Setting.Num posX = add(new Setting.Num("X", 300, 0, 4000));
    public final Setting.Num posY = add(new Setting.Num("Y", 160, 0, 2000));
    public final Setting.Bool armor = add(new Setting.Bool("Armor", true));

    public TargetHUD() { super("TargetHUD", "Shows your current combat target", Category.RENDER); }
}
