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
        var engine = mc.level.getChunkSource().getLightEngine();
        var blocks = engine.getLayerListener(LightLayer.BLOCK);
        var sky = engine.getLayerListener(LightLayer.SKY);
        // section prefilter: a section with NO block-light data can't flag —
        // prunes ~90% of the column before the per-block loop
        int s0 = mc.level.getMinSectionY(), s1 = mc.level.getMaxSectionY();
        for (int sy = s0; sy < s1; sy++) {
            var sec = net.minecraft.core.SectionPos.of(cp, sy);
            if (blocks.getDataLayerData(sec) == null) continue;
            int y0 = Math.max(mc.level.getMinY(), sy << 4);
            int y1 = Math.min(mc.level.getMaxY(), (sy << 4) + 15);
            for (int x = cp.getMinBlockX(); x <= cp.getMaxBlockX(); x++)
                for (int z = cp.getMinBlockZ(); z <= cp.getMaxBlockZ(); z++)
                    for (int y = y0; y <= y1; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (blocks.getLightValue(pos) >= minLight.get()
                            && sky.getLightValue(pos) == 0
                            && !lavaSourced(pos, blocks)) hits.add(pos);
                    }
        }
    }

    /** Uphill walk in light space to the emitting block — if the source is
     *  lava the light is natural. Lava emits 15, so a ±1 probe could not
     *  catch light 5 blocks off a pool; following the gradient always can. */
    private boolean lavaSourced(BlockPos start,
            net.minecraft.world.level.lighting.LayerLightEventListener blocks) {
        BlockPos.MutableBlockPos p = start.mutable();
        for (int step = 0; step < 12; step++) {
            var st = mc.level.getBlockState(p);
            if (st.getLightEmission() > 0)
                return st.is(net.minecraft.world.level.block.Blocks.LAVA);
            int lv = blocks.getLightValue(p);
            BlockPos next = null;
            for (var d : net.minecraft.core.Direction.values()) {
                var n = p.relative(d);
                if (blocks.getLightValue(n) > lv) { lv = blocks.getLightValue(n); next = n; }
            }
            if (next == null) return false;   // lit but no visible source — flag it
            p.set(next);
        }
        return false;
    }

    public Set<BlockPos> hits() { return hits; }
    @Override protected void onDisable() { hits.clear(); scanned.clear(); }
}
