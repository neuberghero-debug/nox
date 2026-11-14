package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashSet;
import java.util.Set;

/**
 * Flags "unnatural" chunks around the player: newly loaded chunks containing
 * player-made structures (chests, furnaces, claimed blocks, beacons) are logged
 * to a heatmap rendered in-world. Used for base hunting on DonutSMP.
 */
public class ChunkFinder extends Module {
    private final Setting.Num radius = add(new Setting.Num("Radius (chunks)", 8, 2, 32));
    private final Setting.Bool notify = add(new Setting.Bool("Chat notify", true));
    private final Set<ChunkPos> flagged = new HashSet<>();
    private final Set<ChunkPos> seen = new HashSet<>();

    public ChunkFinder() { super("ChunkFinder", "Detects player-built chunks", Category.DONUTSMP); }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        ChunkPos pc = mc.player.chunkPosition();
        int r = radius.intVal();
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            ChunkPos cp = new ChunkPos(pc.x() + dx, pc.z() + dz);
            if (seen.contains(cp) || !mc.level.hasChunk(cp.x(), cp.z())) continue;
            seen.add(cp);
            LevelChunk chunk = mc.level.getChunk(cp.x(), cp.z());
            if (scan(chunk)) {
                flagged.add(cp);
                if (notify.on() && mc.player != null)
                    mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§e[NOX] Suspicious chunk at " + cp.x() + ", " + cp.z()));
            }
            return; // one chunk-column per tick — full scans froze the game
        }
    }

    private boolean scan(LevelChunk c) {
        int hits = 0;
        for (BlockPos pos : BlockPos.betweenClosed(c.getPos().getMinBlockX(), c.getMinY(),
                c.getPos().getMinBlockZ(), c.getPos().getMaxBlockX(), c.getMaxY(), c.getPos().getMaxBlockZ())) {
            var b = mc.level.getBlockState(pos).getBlock();
            if (b == Blocks.CHEST || b == Blocks.BARREL || b == Blocks.FURNACE
                || b == Blocks.BLAST_FURNACE || b == Blocks.BEACON || b == Blocks.SPAWNER
                || b == Blocks.END_PORTAL_FRAME || b == Blocks.OBSIDIAN) {
                if (++hits >= 4) return true;
            }
        }
        return false;
    }

    public Set<ChunkPos> flagged() { return flagged; }
    @Override protected void onDisable() { flagged.clear(); seen.clear(); }
}
