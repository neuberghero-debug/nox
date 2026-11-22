package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Geode marker — flags budding amethyst / bud clusters, i.e. geodes that were
 * never harvested. Pure utility module: a geode is NOT base evidence on its
 * own (the old version flagged the amethyst-block shell → every geode became
 * a "base" false positive; SusChunkFinder covers real stash signals).
 * Scans ONE 16³ section per tick inside the geode Y band — a full column of
 * ~98k getBlockState calls in a single tick froze the client.
 */
public class AmethystBaseFinder extends Module {
    private final Setting.Num radius = add(new Setting.Num("Radius (chunks)", 10, 2, 48));
    private static final int GEODE_MIN_Y = -70, GEODE_MAX_Y = 30;
    private static final int MAX_HITS = 2048;

    private final Set<BlockPos> hits = new LinkedHashSet<>();
    private final Set<ChunkPos> queued = new java.util.HashSet<>();
    /** Sections waiting to be scanned: [chunkX, chunkZ, sectionY]. */
    private final Deque<int[]> pending = new ArrayDeque<>();

    public AmethystBaseFinder() { super("AmethystBaseFinder", "Marks unharvested geodes", Category.DONUTSMP); }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        if (pending.isEmpty()) enqueue();
        if (pending.isEmpty()) return;
        int[] s = pending.poll();
        scanSection(s[0], s[1], s[2]);
        while (hits.size() > MAX_HITS) {
            var it = hits.iterator(); it.next(); it.remove();
        }
    }

    private void enqueue() {
        ChunkPos pc = mc.player.chunkPosition();
        int r = radius.intVal();
        int minSec = Math.max(mc.level.getMinSectionY(), (GEODE_MIN_Y - 4) >> 4);
        int maxSec = Math.min(mc.level.getMaxSectionY() - 1, GEODE_MAX_Y >> 4);
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            ChunkPos cp = new ChunkPos(pc.x() + dx, pc.z() + dz);
            if (queued.contains(cp) || !mc.level.hasChunk(cp.x(), cp.z())) continue;
            queued.add(cp);
            for (int sy = minSec; sy <= maxSec; sy++)
                pending.add(new int[]{cp.x(), cp.z(), sy});
            return;   // one chunk's sections per enqueue — 24 sections max
        }
    }

    private void scanSection(int cx, int cz, int sy) {
        LevelChunk c = mc.level.getChunk(cx, cz);
        if (c.getMinY() > (sy << 4) + 15 || c.getMaxY() < (sy << 4)) return;
        for (BlockPos pos : BlockPos.betweenClosed(cx << 4, sy << 4, cz << 4,
                (cx << 4) + 15, (sy << 4) + 15, (cz << 4) + 15)) {
            var b = c.getBlockState(pos).getBlock();
            if (b == Blocks.BUDDING_AMETHYST || b == Blocks.AMETHYST_CLUSTER
                || b == Blocks.LARGE_AMETHYST_BUD || b == Blocks.MEDIUM_AMETHYST_BUD
                || b == Blocks.SMALL_AMETHYST_BUD) {
                hits.add(pos.immutable());
            }
        }
    }

    public Set<BlockPos> hits() { return hits; }
    @Override protected void onDisable() { hits.clear(); queued.clear(); pending.clear(); }
}
