package dev.noxclient.module.movement;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Climbs ladders and vines faster. */
public class FastLadder extends Module {
    public final Setting.Num speed = add(new Setting.Num("Speed", 0.3, 0.1, 1.0));

    public FastLadder() { super("FastLadder", "Climbs ladders faster", Category.MOVEMENT); risky = true; }

    @Override public void onTick() {
        if (mc.player == null || !mc.player.onClimbable()) return;
        var dm = mc.player.getDeltaMovement();
        if (dm.y > 0.02 || mc.player.input.getMoveVector().y > 0)
            mc.player.setDeltaMovement(dm.x, speed.get(), dm.z);
    }
}
