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
            var chunk = mc.level.getChunk(cp.x(), cp.z());
            double score = 0;
            score += chunk.getBlockEntities().size() * 2.0;      // dense tile entities = builds
            // cross-signal: flagged by ChunkFinder
            var cf = dev.noxclient.NoxClient.INSTANCE.modules.get("ChunkFinder");
            if (cf instanceof ChunkFinder f && f.flagged().contains(cp)) score += 10;
            if (score > 0) scores.put(cp, score);
        }
    }

    public Map<ChunkPos, Double> scores() { return scores; }
    @Override protected void onDisable() { scores.clear(); done.clear(); }
}
