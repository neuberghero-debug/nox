package dev.noxclient.module.movement;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Places blocks under your feet while you walk. */
public class Scaffold extends Module {
    public final Setting.Num cps = add(new Setting.Num("CPS", 8, 1, 20));
    public final Setting.Bool tower = add(new Setting.Bool("Tower on jump", true));
    public final Setting.Bool keepY = add(new Setting.Bool("Stay at Y", false));
    public final Setting.Bool down = add(new Setting.Bool("Down on sneak", false));
    private int slot = -1; private double lockY;

    public Scaffold() { super("Scaffold", "Bridges under your feet", Category.MOVEMENT); }

    @Override public void onEnable() { lockY = mc.player == null ? 0 : mc.player.getY(); }

    @Override public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        var inv = mc.player.getInventory();
        if (slot < 0 || !(inv.getItem(slot).getItem() instanceof BlockItem)) {
            slot = -1;
            for (int i = 0; i < 9; i++)
                if (inv.getItem(i).getItem() instanceof BlockItem
                    && !inv.getItem(i).is(net.minecraft.world.item.Items.SAND)
                    && !inv.getItem(i).is(net.minecraft.world.item.Items.GRAVEL)) { slot = i; break; }
            if (slot < 0) return;
        }
        if (keepY.on() && mc.player.getY() > lockY + 0.4)
            mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, -0.28,
                mc.player.getDeltaMovement().z);
        int dy = tower.on() && mc.options.keyJump.isDown() ? -1 : 0;
        if (down.on() && mc.player.isShiftKeyDown()) dy = -2;
        for (int depth = 1 + Math.max(0, -dy); depth >= 1; depth--) {
            BlockPos under = mc.player.blockPosition().below(depth + (dy < -1 ? 1 : 0));
            if (dy == -1) under = mc.player.blockPosition().below();       // tower: feet
            if (dy == -2) under = mc.player.blockPosition().below(2);      // down
            var st = mc.level.getBlockState(under);
            if (!st.isAir() && !st.canBeReplaced()) continue;
            // find a solid face to click
            BlockPos support = under.below();
            Direction face = Direction.UP;
            if (!mc.level.getBlockState(support).isSolid()) {
                support = null;
                for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH,
                        Direction.EAST, Direction.WEST}) {
                    BlockPos s = under.relative(d);
                    if (mc.level.getBlockState(s).isSolid()) {
                        support = s; face = d.getOpposite(); break;
                    }
                }
                if (support == null) return;
            }
            if (!Legitifier.ready(this, cps.get(), 0.2)) return;
            inv.setSelectedSlot(slot);
            var hit = new BlockHitResult(Vec3.atCenterOf(support), face, support, false);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            mc.player.swing(InteractionHand.MAIN_HAND,
                net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
            return;
        }
    }
    @Override public String status() {
        if (mc.player == null) return "no player";
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++)
            if (!inv.getItem(i).isEmpty() && inv.getItem(i).getItem()
                    instanceof net.minecraft.world.item.BlockItem)
                return "holding blocks — bridging";
        return "idle — no blocks in hotbar";
    }

}
