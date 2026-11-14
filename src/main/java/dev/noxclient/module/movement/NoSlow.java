package dev.noxclient.module.movement;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.world.phys.Vec3;

/** Cancels the 0.2x slowdown while eating/blocking/in webs — compensates motion. */
public class NoSlow extends Module {
    private final Setting.Num factor = add(new Setting.Num("Speed factor", 1.0, 0.3, 1.0));

    public NoSlow() { super("NoSlow", "No item/web slowdown", Category.MOVEMENT); }

    @Override
    public void onTick() {
        if (mc.player == null || !mc.player.isUsingItem()) return;
        Vec3 v = mc.player.getDeltaMovement();
        if (v.horizontalDistanceSqr() < 1e-4) return;
        double boost = 5.0 * factor.get(); // undo vanilla *0.2
        mc.player.setDeltaMovement(v.x * boost, v.y, v.z * boost);
    }
}
