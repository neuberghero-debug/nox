package dev.noxclient.module.combat;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Boxes the nearest enemy in obsidian. */
public class AutoTrap extends Module {
    public final Setting.Num range = add(new Setting.Num("Range", 4.5, 3, 6));
    public final Setting.Bool autoOff = add(new Setting.Bool("Disable when done", false));
    public final Setting.Num cps = add(new Setting.Num("CPS", 8, 1, 20));
    private int slot = -1;

    public AutoTrap() { super("AutoTrap", "Obsidian trap on target", Category.COMBAT); }

    @Override public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        var inv = mc.player.getInventory();
        if (slot < 0 || !inv.getItem(slot).is(Items.OBSIDIAN)) {
            slot = -1;
            for (int i = 0; i < 9; i++)
                if (inv.getItem(i).is(Items.OBSIDIAN)) { slot = i; break; }
            if (slot < 0) return;
        }
        Player target = null; double best = range.get() * range.get();
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Player p) || p == mc.player) continue;
            if (dev.noxclient.module.misc.Friends.isFriend(p)) continue;
            double d = p.distanceToSqr(mc.player);
            if (d < best) { best = d; target = p; }
        }
        if (target == null) { if (autoOff.on()) setEnabled(false); return; }
        BlockPos feet = target.blockPosition();
        int[][] ring = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{-1,-1},{1,-1},{-1,1}};
        int[][] spots = new int[ring.length + 1][];
        for (int i = 0; i < ring.length; i++) spots[i] = new int[]{ring[i][0], 0, ring[i][1]};
        spots[ring.length] = new int[]{0, 2, 0}; // roof
        inv.setSelectedSlot(slot);
        for (int[] o : spots) {
            BlockPos t = feet.offset(o[0], o[1], o[2]);
            var st = mc.level.getBlockState(t);
            if (!st.isAir() && !st.canBeReplaced()) continue;
            BlockPos support = t.below();
            if (!mc.level.getBlockState(support).isSolid()) support = t.offset(1, 0, 0);
            if (!Legitifier.ready(this, cps.get(), 0.2)) return;
            var hit = new BlockHitResult(Vec3.atCenterOf(support),
                Direction.UP, support, false);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            return;
        }
        if (autoOff.on()) setEnabled(false);
    }
}
