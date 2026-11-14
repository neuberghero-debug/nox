package dev.noxclient.module.movement;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Legit step: auto-jumps at 1-block ledges while running (looks like parkour). */
public class StepAssist extends Module {
    private final Setting.Bool onlySprinting = add(new Setting.Bool("Only sprinting", true));

    public StepAssist() { super("StepAssist", "Auto-jump at ledges", Category.MOVEMENT); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || !mc.player.onGround()) return;
        if (onlySprinting.on() && !mc.player.isSprinting()) return;
        if (mc.player.input.getMoveVector().y <= 0) return;
        var dir = mc.player.getDirection();
        var ahead = mc.player.blockPosition().relative(dir);
        if (!mc.level.getBlockState(ahead).isAir()
            && mc.level.getBlockState(ahead.above()).isAir()
            && mc.level.getBlockState(ahead.above(2)).isAir()) {
            mc.player.jumpFromGround();
        }
    }
}
