package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ChunkPos;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Scans underground for block-light where there is no sky light — torches,
 * lanterns, glowstone placed by players reveal hidden bases and strip mines.
 */
public class ArtificialLightFinder extends Module {
    private final Setting.Num radius = add(new Setting.Num("Radius", 64, 16, 256));
    private final Setting.Num minLight = add(new Setting.Num("Min light", 10, 1, 15));
    private final Set<BlockPos> hits = new LinkedHashSet<>();
    private final Set<ChunkPos> done = new java.util.HashSet<>();

    public ArtificialLightFinder() { super("ArtificialLightFinder", "Detects player light sources", Category.DONUTSMP); }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        ChunkPos pc = mc.player.chunkPosition();
        int r = (int) Math.ceil(radius.get() / 16.0);
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            ChunkPos cp = new ChunkPos(pc.x() + dx, pc.z() + dz);
            if (done.contains(cp) || !mc.level.hasChunk(cp.x(), cp.z())) continue;
            done.add(cp);
            scan(cp);
        }
    }

    private void scan(ChunkPos cp) {
        int sea = mc.level.getSeaLevel();
        for (BlockPos pos : BlockPos.betweenClosed(cp.getMinBlockX(), mc.level.getMinY(),
                cp.getMinBlockZ(), cp.getMaxBlockX(), sea - 1, cp.getMaxBlockZ())) {
            var engine = mc.level.getChunkSource().getLightEngine();
            if (engine.getLayerListener(LightLayer.BLOCK).getLightValue(pos) >= minLight.get()
                && engine.getLayerListener(LightLayer.SKY).getLightValue(pos) == 0) {
                hits.add(pos.immutable());
            }
        }
    }

    public Set<BlockPos> hits() { return hits; }
    @Override protected void onDisable() { hits.clear(); done.clear(); }
}
