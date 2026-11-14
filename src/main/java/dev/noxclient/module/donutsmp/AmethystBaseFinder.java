package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * DonutSMP bases are commonly marked/claimed near amethyst (geode blocks are
 * used decoratively + buds signal an untouched geode = unlooted base area).
 * Scans chunk surfaces for budding amethyst / amethyst clusters and records
 * candidate base coordinates.
 */
public class AmethystBaseFinder extends Module {
    private final Setting.Num radius = add(new Setting.Num("Radius (chunks)", 10, 2, 48));
    private final Set<BlockPos> hits = new LinkedHashSet<>();
    private final Set<ChunkPos> done = new java.util.HashSet<>();

    public AmethystBaseFinder() { super("AmethystBaseFinder", "Finds bases near amethyst", Category.DONUTSMP); }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        ChunkPos pc = mc.player.chunkPosition();
        int r = radius.intVal();
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            ChunkPos cp = new ChunkPos(pc.x() + dx, pc.z() + dz);
            if (done.contains(cp) || !mc.level.hasChunk(cp.x(), cp.z())) continue;
            done.add(cp);
            scan(cp);
            return; // one chunk-column per tick — full scans froze the game
        }
    }

    private void scan(ChunkPos cp) {
        for (BlockPos pos : BlockPos.betweenClosed(cp.getMinBlockX(), mc.level.getMinY(),
                cp.getMinBlockZ(), cp.getMaxBlockX(), mc.level.getMaxY(), cp.getMaxBlockZ())) {
            var b = mc.level.getBlockState(pos).getBlock();
            if (b == Blocks.BUDDING_AMETHYST || b == Blocks.AMETHYST_CLUSTER
                || b == Blocks.LARGE_AMETHYST_BUD || b == Blocks.AMETHYST_BLOCK) {
                hits.add(pos.immutable());
            }
        }
    }

    public Set<BlockPos> hits() { return hits; }
    @Override protected void onDisable() { hits.clear(); done.clear(); }
}
