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
    /** chunk -> last-scan ms; done-forever never saw lights placed/broken later */
    private final java.util.Map<ChunkPos, Long> scanned = new java.util.HashMap<>();
    private static final long RESCAN_MS = 30_000;

    public ArtificialLightFinder() { super("ArtificialLightFinder", "Detects player light sources", Category.DONUTSMP); }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        ChunkPos pc = mc.player.chunkPosition();
        int r = (int) Math.ceil(radius.get() / 16.0);
        long now = System.currentTimeMillis();
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            ChunkPos cp = new ChunkPos(pc.x() + dx, pc.z() + dz);
            Long t = scanned.get(cp);
            if ((t != null && now - t < RESCAN_MS) || !mc.level.hasChunk(cp.x(), cp.z())) continue;
            scanned.put(cp, now);
            // rebuild this chunk's hits — broken torches must stop flagging
            hits.removeIf(p -> (p.getX() >> 4) == cp.x() && (p.getZ() >> 4) == cp.z());
            scan(cp);
            if (scanned.size() > 4096)
                scanned.keySet().removeIf(c -> Math.abs(c.x() - pc.x()) > r
                    || Math.abs(c.z() - pc.z()) > r);
            while (hits.size() > 4096) hits.remove(hits.iterator().next());  // cap oldest
            return; // one chunk-column per tick — full scans froze the game
        }
    }

    private void scan(ChunkPos cp) {
        int sea = mc.level.getSeaLevel();
        var engine = mc.level.getChunkSource().getLightEngine();
        var blocks = engine.getLayerListener(LightLayer.BLOCK);
        var sky = engine.getLayerListener(LightLayer.SKY);
        for (BlockPos pos : BlockPos.betweenClosed(cp.getMinBlockX(), mc.level.getMinY(),
                cp.getMinBlockZ(), cp.getMaxBlockX(), sea - 1, cp.getMaxBlockZ())) {
            if (blocks.getLightValue(pos) >= minLight.get()
                && sky.getLightValue(pos) == 0
                // lava emits block-light 15 at sky 0 — without the neighbor
                // probe every lava cave below sea level read as "player light"
                && !nearLava(pos)) {
                hits.add(pos.immutable());
            }
        }
    }

    /** 3³ probe — light sourced by lava (itself or an adjacent pool) is natural. */
    private boolean nearLava(BlockPos pos) {
        var lava = net.minecraft.world.level.block.Blocks.LAVA;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++)
                    if (mc.level.getBlockState(pos.offset(dx, dy, dz)).is(lava)) return true;
        return false;
    }

    public Set<BlockPos> hits() { return hits; }
    @Override protected void onDisable() { hits.clear(); scanned.clear(); }
}
