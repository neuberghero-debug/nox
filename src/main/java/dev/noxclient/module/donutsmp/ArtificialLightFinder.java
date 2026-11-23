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
    private final java.util.Deque<ChunkPos> rescan = new java.util.ArrayDeque<>();

    public ArtificialLightFinder() { super("ArtificialLightFinder", "Detects player light sources", Category.DONUTSMP); }

    @Override
    public void onPacket(net.minecraft.network.protocol.Packet<?> p) {
        // a light-update packet = someone placed/removed a light source in a
        // chunk we already scanned — rescan that column instantly at any Y
        if (p instanceof net.minecraft.network.protocol.game.ClientboundLightUpdatePacket lp) {
            ChunkPos cp = new ChunkPos(lp.x(), lp.z());
            if (done.contains(cp)) { done.remove(cp); rescan.add(cp); }
        }
    }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        var hot = rescan.poll();
        if (hot != null) { done.add(hot); scan(hot); return; }
        ChunkPos pc = mc.player.chunkPosition();
        int r = (int) Math.ceil(radius.get() / 16.0);
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            ChunkPos cp = new ChunkPos(pc.x() + dx, pc.z() + dz);
            if (done.contains(cp) || !mc.level.hasChunk(cp.x(), cp.z())) continue;
            done.add(cp);
            scan(cp);
            return; // one chunk-column per tick — full scans froze the game
        }
    }

    private void scan(ChunkPos cp) {
        // full column scan — light data is sent for the whole chunk height, so
        // torches under Y=0 still light up while you stand on the surface
        var engine = mc.level.getChunkSource().getLightEngine();
        var block = engine.getLayerListener(LightLayer.BLOCK);
        var sky = engine.getLayerListener(LightLayer.SKY);
        for (BlockPos pos : BlockPos.betweenClosed(cp.getMinBlockX(), mc.level.getMinY(),
                cp.getMinBlockZ(), cp.getMaxBlockX(), mc.level.getMaxY(), cp.getMaxBlockZ())) {
            if (block.getLightValue(pos) >= minLight.get()
                && sky.getLightValue(pos) == 0) {
                hits.add(pos.immutable());
                dev.noxclient.util.BaseIntel.hit("light", pos,
                    dev.noxclient.util.BaseSigs.Cat.LIGHT.color, "artificial light", 4);
            }
        }
    }

    public Set<BlockPos> hits() { return hits; }
    @Override protected void onDisable() { hits.clear(); done.clear(); rescan.clear(); }
}
