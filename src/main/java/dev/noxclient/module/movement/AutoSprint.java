package dev.noxclient.module.movement;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Keeps sprinting whenever you move — no double-tap needed. */
public class AutoSprint extends Module {
    public final Setting.Bool onlyForward = add(new Setting.Bool("Only forward", false));

    public AutoSprint() { super("AutoSprint", "Always sprint", Category.MOVEMENT); }

    @Override public void onTick() {
        if (mc.player == null || mc.player.isSprinting()) return;
        var mv = mc.player.input.getMoveVector();
        if (onlyForward.on() && mv.y <= 0) return;
        if (mv.x != 0 || mv.y != 0) mc.player.setSprinting(true);
    }
}
