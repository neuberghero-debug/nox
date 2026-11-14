package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * Tunnel Base Finder [PREMIUM] — digs a straight tunnel in the facing direction
 * (mines the 2x1 corridor in front of you at eye/feet level) and flags any
 * base-signature block it exposes (chest, spawner, redstone, storage).
 */
public class TunnelBaseFinder extends Module {
    private final Setting.Num range = add(new Setting.Num("Dig reach", 4, 2, 6));
    private final Setting.Bool autoWalk = add(new Setting.Bool("Auto walk", false));

    public TunnelBaseFinder() { super("TunnelBaseFinder", "Digs tunnels, finds bases", Category.DONUTSMP); premium = true; }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        Direction dir = mc.player.getDirection();
        BlockPos base = mc.player.blockPosition().relative(dir);
        // mine the eye+feet block in front, then scan surroundings
        for (int i = 0; i < 2; i++) {
            BlockPos p = base.above(i);
            var state = mc.level.getBlockState(p);
            if (state.isAir()) continue;
            flagIfInteresting(p, state);
            mc.gameMode.startDestroyBlock(p, dir.getOpposite());
            mc.gameMode.continueDestroyBlock(p, dir.getOpposite());
            return; // one block at a time — safe pacing
        }
        if (autoWalk.on()) mc.options.keyUp.setDown(true);
    }

    private void flagIfInteresting(BlockPos p, net.minecraft.world.level.block.state.BlockState s) {
        var b = s.getBlock();
        if (b == Blocks.CHEST || b == Blocks.SPAWNER || b == Blocks.REDSTONE_WIRE
            || b == Blocks.BARREL || b == Blocks.BEACON || b == Blocks.END_PORTAL_FRAME) {
            dev.noxclient.util.StashLogger.log("tunnelbase", p, b.toString());
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§e[NOX] Base signature at " + p.toShortString()));
        }
    }

    @Override
    protected void onDisable() { if (mc.options != null) mc.options.keyUp.setDown(false); }
}
