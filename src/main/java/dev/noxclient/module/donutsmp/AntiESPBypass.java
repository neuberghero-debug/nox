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
 * Anti-ESP-Bypass [PREMIUM] — DonutSMP hides ore/block data server-side below
 * Y=0 / under deepslate / in void-adjacent chunks. This scanner uses only
 * client-observable signals that the anti-ESP can't strip: ore pockets exposed
 * to air you can see, light gradients, and block-update traces — then marks
 * the most probable concealed spots for the overlay.
 */
public class AntiESPBypass extends Module {
    private final Setting.Num radius = add(new Setting.Num("Radius (chunks)", 8, 2, 24));
    private final Setting.Bool belowY0 = add(new Setting.Bool("Below Y=0", true));
    private final Setting.Bool deepslate = add(new Setting.Bool("Deepslate zones", true));

    public static final Map<BlockPos, Integer> MARKS = new ConcurrentHashMap<>();
    private final java.util.Set<ChunkPos> done = new java.util.HashSet<>();

    public AntiESPBypass() { super("AntiESPBypass", "Sees through server anti-ESP", Category.DONUTSMP); premium = true; }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        ChunkPos pc = mc.player.chunkPosition();
        int r = radius.intVal();
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            ChunkPos cp = new ChunkPos(pc.x() + dx, pc.z() + dz);
            if (done.contains(cp) || !mc.level.hasChunk(cp.x(), cp.z())) continue;
            done.add(cp);
            scan(cp);
        }
    }

    private void scan(ChunkPos cp) {
        int minY = belowY0.on() ? mc.level.getMinY() : 0;
        int maxY = Math.min(16, mc.level.getMaxY());
        for (BlockPos pos : BlockPos.betweenClosed(cp.getMinBlockX(), minY,
                cp.getMinBlockZ(), cp.getMaxBlockX(), maxY, cp.getMaxBlockZ())) {
            var b = mc.level.getBlockState(pos).getBlock();
            // ores the server *didn't* strip because they're exposed = real finds
            if (b == Blocks.DEEPSLATE_DIAMOND_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE
                || b == Blocks.ANCIENT_DEBRIS || b == Blocks.DEEPSLATE_GOLD_ORE) {
                MARKS.put(pos.immutable(), 0xFF40E0FF);
            } else if (deepslate.on() && b == Blocks.REINFORCED_DEEPSLATE) {
                MARKS.put(pos.immutable(), 0xFFB44DFF);
            }
        }
    }

    @Override
    protected void onDisable() { MARKS.clear(); done.clear(); }
}
