package dev.noxclient.module.combat;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Surrounds your feet with obsidian — anti-crystal/burrow protection. */
public class Surround extends Module {
    public final Setting.Bool autoOff = add(new Setting.Bool("Disable when done", true));
    public final Setting.Bool center = add(new Setting.Bool("Center player", true));
    public final Setting.Num cps = add(new Setting.Num("CPS", 8, 1, 20));
    private int slot = -1;

    public Surround() { super("Surround", "Obsidian ring at your feet", Category.COMBAT); }

    @Override public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        var inv = mc.player.getInventory();
        if (slot < 0 || !inv.getItem(slot).is(Items.OBSIDIAN)) {
            slot = -1;
            for (int i = 0; i < 9; i++)
                if (inv.getItem(i).is(Items.OBSIDIAN)) { slot = i; break; }
            if (slot < 0) { setEnabled(false); return; }
        }
        BlockPos feet = mc.player.blockPosition();
        if (center.on()) {
            var c = Vec3.atCenterOf(feet);
            mc.player.setPos(c.x, mc.player.getY(), c.z);
            mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0);
        }
        int[][] off = {{1,0},{-1,0},{0,1},{0,-1}};
        inv.setSelectedSlot(slot);
        for (int[] o : off) {
            BlockPos p = feet.offset(o[0], -1, o[1]);
            BlockPos target = feet.offset(o[0], 0, o[1]);
            var ts = mc.level.getBlockState(target);
            if (!ts.isAir() && !ts.canBeReplaced()) continue;
            if (!Legitifier.ready(this, cps.get(), 0.2)) return;
            var hit = new BlockHitResult(Vec3.atCenterOf(p), Direction.UP, p, false);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            return;
        }
        if (autoOff.on()) setEnabled(false);
    }
}
