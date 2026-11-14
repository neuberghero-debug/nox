package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Breaks mature crops in reach and replants — paced like a player farming. */
public class AutoHarvest extends Module {
    private final Setting.Num range = add(new Setting.Num("Range", 4, 2, 6));
    private final Setting.Bool replant = add(new Setting.Bool("Replant", true));

    public AutoHarvest() { super("AutoHarvest", "Harvests + replants crops", Category.DONUTSMP); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || !Legitifier.ready(this, 4, 0.4)) return;
        BlockPos me = mc.player.blockPosition();
        int r = range.intVal();
        for (BlockPos p : BlockPos.betweenClosed(me.offset(-r, -1, -r), me.offset(r, 2, r))) {
            BlockState s = mc.level.getBlockState(p);
            if (!mature(s)) continue;
            mc.gameMode.startDestroyBlock(p, Direction.UP);
            mc.gameMode.continueDestroyBlock(p, Direction.UP);
            if (replant.on()) replant(p);
            return;
        }
    }

    /** Plant matching seeds back onto the harvested spot (hotbar only). */
    private void replant(BlockPos p) {
        var inv = mc.player.getInventory();
        int seeds = -1;
        for (int i = 0; i < 9; i++) {
            var it = inv.getItem(i).getItem().toString();
            if (it.endsWith("_seeds") || it.equals("minecraft:carrot")
                || it.equals("minecraft:potato") || it.equals("minecraft:nether_wart")) { seeds = i; break; }
        }
        if (seeds < 0) return;
        int prev = inv.getSelectedSlot();
        inv.setSelectedSlot(seeds);
        mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
            new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atBottomCenterOf(p), Direction.UP, p, false));
        inv.setSelectedSlot(prev);
    }

    private boolean mature(BlockState s) {
        var b = s.getBlock();
        if (b instanceof CropBlock c) return c.isMaxAge(s);
        if (b instanceof NetherWartBlock) return s.getValue(NetherWartBlock.AGE) >= 3;
        return false;
    }
}
