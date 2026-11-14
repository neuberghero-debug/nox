package dev.noxclient.module.movement;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.core.BlockPos;

/** Sneaks before you walk off an edge — legit bridge building. */
public class SafeWalk extends Module {
    public final Setting.Bool onlyAir = add(new Setting.Bool("Sneak only at edge", true));
    private boolean holding;

    public SafeWalk() { super("SafeWalk", "Sneaks at block edges", Category.MOVEMENT); }

    @Override public void onTick() {
        if (mc.player == null || mc.level == null) return;
        if (!mc.player.onGround()) return;
        boolean edge = true;
        if (onlyAir.on()) {
            double dx = mc.player.getDeltaMovement().x, dz = mc.player.getDeltaMovement().z;
            if (dx * dx + dz * dz < 0.0004) { release(); return; }
            BlockPos ahead = BlockPos.containing(
                mc.player.getX() + dx * 4, mc.player.getY() - 1.5, mc.player.getZ() + dz * 4);
            edge = mc.level.getBlockState(ahead).isAir();
        }
        if (edge) { mc.options.keyShift.setDown(true); holding = true; }
        else release();
    }

    private void release() { if (holding) { mc.options.keyShift.setDown(false); holding = false; } }
    @Override public void onDisable() { release(); }
}
