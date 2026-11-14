package dev.noxclient.module.movement;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Walks forward hands-free — highways and farms. */
public class AutoWalk extends Module {
    public final Setting.Bool sprint = add(new Setting.Bool("Sprint", true));
    public final Setting.Bool jump = add(new Setting.Bool("Auto jump", false));

    public AutoWalk() { super("AutoWalk", "Walks forward for you", Category.MOVEMENT); }

    @Override public void onTick() {
        if (mc.player == null) return;
        mc.options.keyUp.setDown(true);
        if (sprint.on()) mc.player.setSprinting(true);
        if (jump.on() && mc.player.onGround()) mc.player.jumpFromGround();
    }

    @Override public void onDisable() {
        if (mc.player != null) mc.options.keyUp.setDown(false);
    }
}
