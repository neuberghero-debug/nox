package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Mines every block of the chosen kind around you — farms, tree farms, etc. */
public class Nuker extends Module {
    public final Setting.Str filter = add(new Setting.Str("Block contains", ""));
    public final Setting.Num range = add(new Setting.Num("Range", 4, 2, 6));
    public final Setting.Bool legit = add(new Setting.Bool("Swing arm", true));
    public final Setting.Num bps = add(new Setting.Num("Blocks/s", 4, 1, 12));
    private BlockPos working;

    public Nuker() { super("Nuker", "Mass-break around you", Category.DONUTSMP); }

    @Override public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        var eye = mc.player.blockPosition();
        int r = range.intVal();
        String f = filter.get().toLowerCase();
        BlockPos best = null; double bd = Double.MAX_VALUE;
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            var p = eye.offset(x, y - 1, z);
            var st = mc.level.getBlockState(p);
            if (st.isAir() || st.getDestroySpeed(mc.level, p) < 0) continue;
            if (!f.isEmpty() && !st.getBlock().toString().toLowerCase().contains(f)) continue;
            double d = p.distSqr(eye);
            if (d < bd) { bd = d; best = p; }
        }
        if (best == null) { working = null; return; }
        if (!Legitifier.ready(this, bps.get(), 0.15)) return;
        working = best;
        var hit = new BlockHitResult(Vec3.atCenterOf(best), Direction.UP, best, false);
        mc.gameMode.startDestroyBlock(best, Direction.UP);
        mc.gameMode.continueDestroyBlock(best, Direction.UP);
        if (legit.on())
            mc.player.swing(InteractionHand.MAIN_HAND,
                net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
    }
}
