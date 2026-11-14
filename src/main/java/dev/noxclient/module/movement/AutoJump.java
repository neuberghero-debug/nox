package dev.noxclient.module.movement;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Jumps automatically while moving — no spacebar. */
public class AutoJump extends Module {
    public final Setting.Bool onlyMoving = add(new Setting.Bool("Only when moving", true));

    public AutoJump() { super("AutoJump", "Jumps for you", Category.MOVEMENT); }

    @Override public void onTick() {
        if (mc.player == null || !mc.player.onGround()) return;
        var mv = mc.player.input.getMoveVector();
        if (onlyMoving.on() && mv.x == 0 && mv.y == 0) return;
        mc.player.jumpFromGround();
    }
}
