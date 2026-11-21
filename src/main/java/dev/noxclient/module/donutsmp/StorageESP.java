package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Highlights storage blocks through walls via the NOX overlay:
 * chests red, shulkers purple, barrels orange, furnaces gray, ender chests cyan.
 * Clusters nearby hits into scored base candidates with verdict cards
 * (Prestige BaseESP style: "Unclear / 98 score / 14 containers, 14 hoppers").
 */
public class StorageESP extends Module {
    private final Setting.Num radius = add(new Setting.Num("Radius (chunks)", 6, 1, 16));
    private final Setting.Bool chests = add(new Setting.Bool("Chests", true));
    private final Setting.Bool shulkers = add(new Setting.Bool("Shulkers", true));
    private final Setting.Bool barrels = add(new Setting.Bool("Barrels", false));
    private final Setting.Bool furnaces = add(new Setting.Bool("Furnaces", false));
    private final Setting.Bool hoppers = add(new Setting.Bool("Hoppers etc", true,
        "Hoppers, droppers, dispensers, crafters — farm/stash evidence"));
    public final Setting.Bool cards = add(new Setting.Bool("Verdict cards", true,
        "Floating score cards over the hottest clusters"));

    /** pos -> ARGB color, read by the HUD overlay every frame */
    public static final Map<BlockPos, Integer> HITS = new ConcurrentHashMap<>();
    /** pos -> kind bucket for cluster scoring */
    public static final Map<BlockPos, String> KIND = new ConcurrentHashMap<>();
    private final java.util.Map<ChunkPos, Long> scanned = new java.util.HashMap<>();
    private static final long RESCAN_MS = 15_000;
    private long pruneAt;

    /** Scored storage cluster — the Prestige "base verdict" card model. */
    public static final class Cluster {
        public BlockPos pos;                 // centroid
        public int containers, hoppers;
        public double score;
        public int verdict() { return score >= 60 ? 2 : score >= 25 ? 1 : 0; }
        public String verdictName() {
            return switch (verdict()) { case 2 -> "Likely base"; case 1 -> "Suspicious"; default -> "Unclear"; };
        }
    }

    public StorageESP() { super("StorageESP", "Storage blocks through walls", Category.RENDER); }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        ChunkPos pc = mc.player.chunkPosition();
        int r = radius.intVal();
        long now = System.currentTimeMillis();
        if (now - pruneAt > 2000) {
            pruneAt = now;
            int lim = r + 1;
            HITS.keySet().removeIf(p -> Math.abs((p.getX() >> 4) - pc.x()) > lim
                || Math.abs((p.getZ() >> 4) - pc.z()) > lim);
            KIND.keySet().removeIf(p -> Math.abs((p.getX() >> 4) - pc.x()) > lim
                || Math.abs((p.getZ() >> 4) - pc.z()) > lim);
            scanned.keySet().removeIf(c -> Math.abs(c.x() - pc.x()) > lim
                || Math.abs(c.z() - pc.z()) > lim);
        }
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            ChunkPos cp = new ChunkPos(pc.x() + dx, pc.z() + dz);
            Long t = scanned.get(cp);
            if ((t != null && now - t < RESCAN_MS) || !mc.level.hasChunk(cp.x(), cp.z())) continue;
            scanned.put(cp, now);
            HITS.keySet().removeIf(p -> (p.getX() >> 4) == cp.x() && (p.getZ() >> 4) == cp.z());
            KIND.keySet().removeIf(p -> (p.getX() >> 4) == cp.x() && (p.getZ() >> 4) == cp.z());
            var chunk = mc.level.getChunk(cp.x(), cp.z());
            for (var entry : chunk.getBlockEntities().entrySet()) {
                var b = entry.getValue().getBlockState().getBlock();
                int col = 0; String kind = null;
                if (b == Blocks.CHEST && chests.on()) { col = 0xFFFF4040; kind = "container"; }
                else if (b == Blocks.TRAPPED_CHEST && chests.on()) { col = 0xFFFF6040; kind = "container"; }
                else if (b == Blocks.ENDER_CHEST && chests.on()) { col = 0xFF40E0FF; kind = "container"; }
                else if (b instanceof net.minecraft.world.level.block.ShulkerBoxBlock && shulkers.on()) { col = 0xFFB44DFF; kind = "shulker"; }
                else if (b == Blocks.BARREL && barrels.on()) { col = 0xFFFFAA00; kind = "container"; }
                else if ((b == Blocks.FURNACE || b == Blocks.BLAST_FURNACE || b == Blocks.SMOKER) && furnaces.on()) { col = 0xFFAAAAAA; kind = "container"; }
                else if (hoppers.on() && (b == Blocks.HOPPER || b == Blocks.DROPPER
                    || b == Blocks.DISPENSER || b == Blocks.CRAFTER)) { col = 0xFF7ADF8A; kind = "hopper"; }
                if (col != 0) {
                    var ip = entry.getKey().immutable();
                    HITS.put(ip, col); KIND.put(ip, kind);
                }
            }
        }
    }

    /** Greedy 40-block clustering of hits, scored like the Prestige card. */
    public List<Cluster> clusters() {
        var pts = new ArrayList<>(KIND.entrySet());
        var out = new ArrayList<Cluster>();
        boolean[] used = new boolean[pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            if (used[i]) continue;
            var cl = new Cluster();
            long sx = 0, sy = 0, sz = 0; int n = 0;
            var stack = new ArrayList<int[]>();
            stack.add(new int[]{i});
            while (!stack.isEmpty()) {
                int idx = stack.remove(stack.size() - 1)[0];
                if (used[idx]) continue;
                used[idx] = true;
                var e = pts.get(idx);
                sx += e.getKey().getX(); sy += e.getKey().getY(); sz += e.getKey().getZ(); n++;
                if ("hopper".equals(e.getValue())) cl.hoppers++;
                else { cl.containers++; if ("shulker".equals(e.getValue())) cl.score += 8; }
                for (int j = 0; j < pts.size(); j++) {
                    if (used[j]) continue;
                    var p2 = pts.get(j).getKey();
                    if (e.getKey().distSqr(p2) < 40 * 40) stack.add(new int[]{j});
                }
            }
            cl.pos = new BlockPos((int) (sx / n), (int) (sy / n), (int) (sz / n));
            cl.score += cl.containers * 1.5 + cl.hoppers * 2;
            if (cl.pos.getY() < 0) cl.score += 10;   // below Y 0 = deliberate burial
            // BaseIntel evidence on the same chunk upgrades the verdict
            var ie = dev.noxclient.util.BaseIntel.entries().get(
                new ChunkPos(cl.pos.getX() >> 4, cl.pos.getZ() >> 4));
            if (ie != null) cl.score += ie.score * 0.4;
            out.add(cl);
        }
        out.sort((a, b) -> Double.compare(b.score, a.score));
        return out;
    }

    @Override
    protected void onDisable() { HITS.clear(); KIND.clear(); scanned.clear(); }
    @Override public String status() { return HITS.size() + " storage marks"; }
}
