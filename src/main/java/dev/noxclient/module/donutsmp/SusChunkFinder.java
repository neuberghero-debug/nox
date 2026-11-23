package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;

/**
 * Heuristic "sus chunk" scorer: combines signals from the other finders plus
 * tile-entity density and block-change rate to rank chunks most likely to
 * contain stashes, traps, or hidden farms. Outputs a scored heatmap.
 */
public class SusChunkFinder extends Module {
    private final Setting.Num radius = add(new Setting.Num("Radius (chunks)", 12, 2, 48));
    private final Map<ChunkPos, Double> scores = new HashMap<>();
    private final Set<ChunkPos> done = new java.util.HashSet<>();

    public SusChunkFinder() { super("SusChunkFinder", "Scores chunks by suspiciousness", Category.DONUTSMP); }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        ChunkPos pc = mc.player.chunkPosition();
        int r = radius.intVal();
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            ChunkPos cp = new ChunkPos(pc.x() + dx, pc.z() + dz);
            if (done.contains(cp) || !mc.level.hasChunk(cp.x(), cp.z())) continue;
            done.add(cp);
            // re-score chunks later too — intel arrives asynchronously
            if (done.size() > 400 && (System.currentTimeMillis() & 15) == 0) done.remove(cp);
            var chunk = mc.level.getChunk(cp.x(), cp.z());
            double score = 0;
            score += chunk.getBlockEntities().size() * 2.0;      // dense tile entities = builds
            // cross-signal: flagged by ChunkFinder
            var cf = dev.noxclient.NoxClient.INSTANCE.modules.get("ChunkFinder");
            if (cf instanceof ChunkFinder f && f.flagged().contains(cp)) score += 10;
            score += dev.noxclient.util.BaseIntel.score(cp);      // packet-intel hits
            score += chunkAge(cp);                              // kelp/cane/bamboo growth ≈ chunk age
            // ChunkAger: fresh terrain can't hold a base — old/disk chunks can
            int ag = ChunkAger.age(cp);
            if (ag == 1) score -= 5;       // fresh: nobody's been there
            else if (ag == 4) score += 4;  // old-generation: prime habitat
            if (score > 0) scores.put(cp, score);
        }
    }

    /**
     * Chunk-age proxy: kelp/sugarcane/bamboo/cactus only grow while a chunk is
     * loaded by a nearby player. Tall growth = the chunk has been ticked for
     * hours = someone lives/farms there. The classic "how old is this chunk"
     * tell — zero packets, pure observation.
     */
    private double chunkAge(ChunkPos cp) {
        int grown = 0;
        var m = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int lx = 0; lx < 16; lx += 4) for (int lz = 0; lz < 16; lz += 4) {
            m.set(cp.getMinBlockX() + lx, mc.level.getMaxY(), cp.getMinBlockZ() + lz);
            int y = mc.level.getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, m).getY();
            // scan down a few blocks from the surface for growth columns
            for (int dy = 0; dy < 12; dy++) {
                var b = mc.level.getBlockState(m.setY(y - dy)).getBlock();
                if (b == net.minecraft.world.level.block.Blocks.KELP) {
                    // kelp column: walk up, height > 4 = aged
                    int h = 0;
                    while (mc.level.getBlockState(m.setY(y - dy + h)).getBlock()
                            == net.minecraft.world.level.block.Blocks.KELP && h < 26) h++;
                    if (h >= 5) grown++;
                    break;
                }
                if (b == net.minecraft.world.level.block.Blocks.SUGAR_CANE
                    || b == net.minecraft.world.level.block.Blocks.BAMBOO
                    || b == net.minecraft.world.level.block.Blocks.CACTUS) {
                    int h = 0;
                    while (mc.level.getBlockState(m.setY(y - dy + h)).getBlock() == b && h < 8) h++;
                    if (h >= 3) grown++;
                    break;
                }
            }
        }
        return grown >= 3 ? 4.0 : grown * 1.0;
    }

    public Map<ChunkPos, Double> scores() { return scores; }
    @Override protected void onDisable() { scores.clear(); done.clear(); }
}
