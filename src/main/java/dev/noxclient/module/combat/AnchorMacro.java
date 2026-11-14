package dev.noxclient.module.combat;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Anchor Macro [PREMIUM] — automates the respawn-anchor combo: places the
 * anchor near the enemy, charges it with glowstone, then clicks to detonate.
 * Paced through Legitifier.
 */
public class AnchorMacro extends Module {
    private final Setting.Num cps = add(new Setting.Num("Action CPS", 8, 1, 15));
    private BlockPos anchor;

    public AnchorMacro() { super("AnchorMacro", "Automated anchor explosions", Category.COMBAT); premium = true; }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;
        Player target = nearest();
        if (target == null || !Legitifier.ready(this, cps.get(), 0.35)) return;

        if (anchor == null) {
            BlockPos spot = findSpot(target);
            if (spot == null) return;
            int slot = find(Items.RESPAWN_ANCHOR);
            if (slot < 0) return;
            mc.player.getInventory().setSelectedSlot(slot);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(spot), Direction.UP, spot, false));
            anchor = spot.above();
            return;
        }
        var state = mc.level.getBlockState(anchor);
        if (state.getBlock() != Blocks.RESPAWN_ANCHOR) { anchor = null; return; }
        int charges = state.getValue(RespawnAnchorBlock.CHARGE);
        if (charges < RespawnAnchorBlock.MAX_CHARGES) {
            int gs = find(Items.GLOWSTONE);
            if (gs < 0) return;
            mc.player.getInventory().setSelectedSlot(gs);
            clickAnchor();
        } else {
            clickAnchor(); // detonate
            anchor = null;
        }
    }

    private void clickAnchor() {
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(anchor), Direction.UP, anchor, false));
    }

    private int find(net.minecraft.world.item.Item item) {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getItem(i).is(item)) return i;
        return -1;
    }

    private BlockPos findSpot(Player t) {
        BlockPos tb = t.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(tb.offset(-2, -1, -2), tb.offset(2, 0, 2))) {
            if (!mc.level.getBlockState(p).isAir()
                && mc.level.getBlockState(p.above()).isAir()
                && p.above().distSqr(tb) < 16 && p.above().distSqr(mc.player.blockPosition()) < 25)
                return p.immutable();
        }
        return null;
    }

    private Player nearest() {
        Player best = null; double bd = 64;
        for (Entity e : mc.level.entitiesForRendering())
            if (e instanceof Player p && e != mc.player && p.isAlive()) {
                double d = mc.player.distanceToSqr(p);
                if (d < bd) { bd = d; best = p; }
            }
        return best;
    }
}
