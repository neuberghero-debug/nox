package dev.noxclient.module.movement;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Jump higher. */
public class HighJump extends Module {
    public final Setting.Num boost = add(new Setting.Num("Boost", 0.4, 0.1, 1.5));
    private boolean wasGround = true;

    public HighJump() { super("HighJump", "Jump higher", Category.MOVEMENT); }

    @Override public void onTick() {
        if (mc.player == null) return;
        // Grim strict: any impulse above the vanilla 0.42 fails simulation.
        if (dev.noxclient.module.donutsmp.DonutGuard.strict()) return;
        boolean ground = mc.player.onGround();
        if (wasGround && !ground && mc.player.getDeltaMovement().y > 0) {
            var d = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(d.x, d.y + boost.get(), d.z);
        }
        wasGround = ground;
    }
}
