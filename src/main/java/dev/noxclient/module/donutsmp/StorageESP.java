package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Highlights storage blocks through walls via the NOX overlay:
 * chests red, shulkers purple, barrels orange, furnaces gray, ender chests cyan.
 */
public class StorageESP extends Module {
    private final Setting.Num radius = add(new Setting.Num("Radius (chunks)", 6, 1, 16));
    private final Setting.Bool chests = add(new Setting.Bool("Chests", true));
    private final Setting.Bool shulkers = add(new Setting.Bool("Shulkers", true));
    private final Setting.Bool barrels = add(new Setting.Bool("Barrels", false));
    private final Setting.Bool furnaces = add(new Setting.Bool("Furnaces", false));

    /** pos -> ARGB color, read by the HUD overlay every frame */
    public static final Map<BlockPos, Integer> HITS = new ConcurrentHashMap<>();
    /** chunk -> last-scan ms; a done-forever set never picks up chests placed
     *  after the first scan and ghosts the ones that get mined. */
    private final java.util.Map<ChunkPos, Long> scanned = new java.util.HashMap<>();
    private static final long RESCAN_MS = 15_000;
    private long pruneAt;

    public StorageESP() { super("StorageESP", "Storage blocks through walls", Category.RENDER); }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        ChunkPos pc = mc.player.chunkPosition();
        int r = radius.intVal();
        long now = System.currentTimeMillis();
        // drop marks left behind / outside radius once in a while
        if (now - pruneAt > 2000) {
            pruneAt = now;
            int lim = r + 1;
            HITS.keySet().removeIf(p -> Math.abs((p.getX() >> 4) - pc.x()) > lim
                || Math.abs((p.getZ() >> 4) - pc.z()) > lim);
            scanned.keySet().removeIf(c -> Math.abs(c.x() - pc.x()) > lim
                || Math.abs(c.z() - pc.z()) > lim);
        }
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            ChunkPos cp = new ChunkPos(pc.x() + dx, pc.z() + dz);
            Long t = scanned.get(cp);
            if ((t != null && now - t < RESCAN_MS) || !mc.level.hasChunk(cp.x(), cp.z())) continue;
            scanned.put(cp, now);
            // rebuild this chunk's marks — mined chests must stop ghosting
            HITS.keySet().removeIf(p -> (p.getX() >> 4) == cp.x() && (p.getZ() >> 4) == cp.z());
            var chunk = mc.level.getChunk(cp.x(), cp.z());
            for (var entry : chunk.getBlockEntities().entrySet()) {
                var b = entry.getValue().getBlockState().getBlock();
                int col = 0;
                if (b == Blocks.CHEST && chests.on()) col = 0xFFFF4040;
                else if (b == Blocks.ENDER_CHEST && chests.on()) col = 0xFF40E0FF;
                else if (b instanceof net.minecraft.world.level.block.ShulkerBoxBlock && shulkers.on()) col = 0xFFB44DFF;
                else if (b == Blocks.BARREL && barrels.on()) col = 0xFFFFAA00;
                else if ((b == Blocks.FURNACE || b == Blocks.BLAST_FURNACE || b == Blocks.SMOKER) && furnaces.on()) col = 0xFFAAAAAA;
                if (col != 0) HITS.put(entry.getKey().immutable(), col);
            }
        }
    }

    @Override
    protected void onDisable() { HITS.clear(); scanned.clear(); }
    @Override public String status() { return HITS.size() + " storage marks"; }

}
