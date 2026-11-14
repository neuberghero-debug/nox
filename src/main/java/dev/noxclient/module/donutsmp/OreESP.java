package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Highlights cave-visible blocks of a chosen type (ores, spawners, lava). */
public class OreESP extends Module {
    public final Setting.Str block = add(new Setting.Str("Block id contains", "diamond_ore"));
    public final Setting.Num radius = add(new Setting.Num("Radius (chunks)", 4, 1, 10));
    public final Setting.Num rangeY = add(new Setting.Num("Y range", 32, 8, 64));
    public final Setting.Col color = add(new Setting.Col("Color", 0xFF40E0FF));

    public static final Map<BlockPos, Integer> HITS = new ConcurrentHashMap<>();
    private final java.util.Set<ChunkPos> done = new java.util.HashSet<>();

    public OreESP() { super("OreESP", "Shows cave-visible ores through walls", Category.RENDER); }

    @Override public void onTick() {
        if (mc.level == null || mc.player == null) return;
        String f = block.get().toLowerCase();
        if (f.isEmpty()) return;
        ChunkPos pc = mc.player.chunkPosition();
        int r = radius.intVal(), y0 = mc.player.getBlockY() - rangeY.intVal(),
            y1 = mc.player.getBlockY() + rangeY.intVal();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            ChunkPos cp = new ChunkPos(pc.x() + dx, pc.z() + dz);
            if (done.contains(cp) || !mc.level.hasChunk(cp.x(), cp.z())) continue;
            done.add(cp);
            var chunk = mc.level.getChunk(cp.x(), cp.z());
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++)
                for (int y = Math.max(y0, mc.level.getMinY()); y <= Math.min(y1, mc.level.getMaxY()); y++) {
                    pos.set(cp.getMinBlockX() + x, y, cp.getMinBlockZ() + z);
                    var st = chunk.getBlockState(pos);
                    // registry id lookup — st.toString() per block over ~1.3M
                    // positions was the lag source
                    if (!net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .getKey(st.getBlock()).getPath().contains(f)) continue;
                    // only flag blocks exposed to air/fluid (visible, not deep-hidden)
                    if (!mc.level.getBlockState(pos.above()).isAir()
                        && !mc.level.getBlockState(pos.below()).isAir()
                        && !mc.level.getBlockState(pos.north()).isAir()
                        && !mc.level.getBlockState(pos.south()).isAir()
                        && !mc.level.getBlockState(pos.east()).isAir()
                        && !mc.level.getBlockState(pos.west()).isAir()) continue;
                    HITS.put(pos.immutable(), color.get() | 0xFF000000);
                }
            return; // one chunk per tick — don't stall the render thread
        }
    }

    @Override public void onDisable() { HITS.clear(); done.clear(); }
    @Override public String status() { return HITS.size() + " ore marks"; }

}
