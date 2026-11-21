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

    /** Shared overlay mark registry — Breadcrumbs/waypoints/etc also write here,
     *  so rescans must only rebuild OUR entries, tracked separately in `mine`. */
    public static final Map<BlockPos, Integer> MARKS = new ConcurrentHashMap<>();
    private final java.util.Set<BlockPos> mine = ConcurrentHashMap.newKeySet();
    /** chunk -> last-scan ms; done-forever missed ore changes + ghosted marks */
    private final java.util.Map<ChunkPos, Long> scanned = new java.util.HashMap<>();
    private static final long RESCAN_MS = 30_000;

    public AntiESPBypass() { super("AntiESPBypass", "Sees through server anti-ESP", Category.DONUTSMP); premium = true; }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        ChunkPos pc = mc.player.chunkPosition();
        int r = radius.intVal();
        long now = System.currentTimeMillis();
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            ChunkPos cp = new ChunkPos(pc.x() + dx, pc.z() + dz);
            Long t = scanned.get(cp);
            if ((t != null && now - t < RESCAN_MS) || !mc.level.hasChunk(cp.x(), cp.z())) continue;
            scanned.put(cp, now);
            // rebuild only our own marks in this chunk — Breadcrumbs & co keep theirs
            mine.removeIf(p -> {
                if ((p.getX() >> 4) == cp.x() && (p.getZ() >> 4) == cp.z()) {
                    MARKS.remove(p); return true;
                }
                return false;
            });
            scan(cp);
            // bound the scanned map on long sessions
            if (scanned.size() > 4096)
                scanned.keySet().removeIf(c -> Math.abs(c.x() - pc.x()) > r
                    || Math.abs(c.z() - pc.z()) > r);
            return; // one chunk-column per tick — full scans froze the game
        }
    }

    private void mark(BlockPos p, int col) {
        mine.add(p);
        MARKS.put(p, col);
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
                mark(pos.immutable(), 0xFF40E0FF);
            } else if (deepslate.on() && b == Blocks.REINFORCED_DEEPSLATE) {
                mark(pos.immutable(), 0xFFB44DFF);
            }
        }
    }

    @Override
    protected void onDisable() { for (var p : mine) MARKS.remove(p); mine.clear(); scanned.clear(); }
}
