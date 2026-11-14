package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * SellWand helper — auto-clicks a sell wand onto every chest in reach, so bulk
 * selling a storage room takes zero clicks.
 */
public class SellWand extends Module {
    private final Setting.Num range = add(new Setting.Num("Range", 4, 2, 5));
    private final Setting.Num cps = add(new Setting.Num("Clicks/s", 4, 1, 10));
    private final java.util.Set<BlockPos> used = new java.util.HashSet<>();

    public SellWand() { super("SellWand", "Auto sell-wand chests", Category.DONUTSMP); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || !Legitifier.ready(this, cps.get(), 0.35)) return;
        BlockPos me = mc.player.blockPosition();
        int r = range.intVal();
        for (BlockPos p : BlockPos.betweenClosed(me.offset(-r, -2, -r), me.offset(r, 2, r))) {
            if (used.contains(p) || mc.level.getBlockState(p).getBlock() != Blocks.CHEST) continue;
            used.add(p.immutable());
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(p), Direction.UP, p, false));
            return;
        }
    }

    @Override
    protected void onDisable() { used.clear(); }
}
