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
    private final java.util.Set<ChunkPos> done = new java.util.HashSet<>();

    public StorageESP() { super("StorageESP", "Storage blocks through walls", Category.RENDER); }

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
    protected void onDisable() { HITS.clear(); done.clear(); }
    @Override public String status() { return HITS.size() + " storage marks"; }

}
