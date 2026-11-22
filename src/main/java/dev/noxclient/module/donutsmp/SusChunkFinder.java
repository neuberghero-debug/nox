package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.*;

import java.util.*;

/**
 * BaseRadar — weighted sus-chunk scoring, best-of-breed:
 *  · BepHax-style storage count + shulker/crafter instant-hit + spawn
 *    distance floor + teleport-disable (no coord leaks on stream)
 *  · 67Client-style full-honey beehive signal + flag merging
 *  · Anubis-style chunk settle age + sound/notify
 * Rescans loaded chunks on an interval — scores decay as loot leaves.
 */
public class SusChunkFinder extends Module {
    private final Setting.Num radius = add(new Setting.Num("Radius (chunks)", 12, 2, 48));
    private final Setting.Num rescanSec = add(new Setting.Num("Rescan (s)", 45, 10, 300));
    private final Setting.Num minDist = add(new Setting.Num("Min dist from 0,0", 256, 0, 10000));
    private final Setting.Num merge = add(new Setting.Num("Merge radius (ch)", 3, 0, 10));
    private final Setting.Num settleTicks = add(new Setting.Num("Settle ticks", 40, 0, 400));
    private final Setting.Num minScore = add(new Setting.Num("Min score", 8, 1, 50));
    private final Setting.Bool shulkerHit = add(new Setting.Bool("Shulker instant-hit", true));
    private final Setting.Bool crafterHit = add(new Setting.Bool("Crafter instant-hit", true));
    private final Setting.Bool notify = add(new Setting.Bool("Chat notify", true));
    private final Setting.Bool antiLeak = add(new Setting.Bool("Disable on teleport", false));

    private final Map<ChunkPos, Double> scores = new HashMap<>();
    private final Map<ChunkPos, Long> seenAt = new HashMap<>();
    private final Map<ChunkPos, Long> scannedAt = new HashMap<>();
    private final Set<ChunkPos> notified = new HashSet<>();
    private final Set<ChunkPos> instant = new HashSet<>();

    public SusChunkFinder() { super("SusChunkFinder", "BaseRadar — weighted base signals", Category.DONUTSMP); }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        // BepHax-style coord-leak guard: a >64-block instant jump = teleport —
        // auto-disable so flagged coords don't get screenshotted mid-hunt.
        // NOT while AutoScout is up — its RTPs ARE the authorized jumps.
        var scout = dev.noxclient.NoxClient.INSTANCE.modules.get("AutoScout");
        boolean scouting = scout != null && scout.isEnabled();
        if (antiLeak.on() && !scouting && lastPos != null
            && lastPos.distanceToSqr(mc.player.position()) > 64 * 64) {
            setEnabled(false); return;
        }
        lastPos = mc.player.position();
        long now = System.currentTimeMillis();
        ChunkPos pc = mc.player.chunkPosition();
        int r = radius.intVal();
        // long-session bound: drop bookkeeping for chunks far outside radius
        if (seenAt.size() > 8192) {
            int lim = r + 4;
            seenAt.keySet().removeIf(c -> Math.abs(c.x() - pc.x()) > lim
                || Math.abs(c.z() - pc.z()) > lim);
            scannedAt.keySet().removeIf(c -> Math.abs(c.x() - pc.x()) > lim
                || Math.abs(c.z() - pc.z()) > lim);
            notified.removeIf(c -> Math.abs(c.x() - pc.x()) > lim
                || Math.abs(c.z() - pc.z()) > lim);
        }
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            ChunkPos cp = new ChunkPos(pc.x() + dx, pc.z() + dz);
            if (!mc.level.hasChunk(cp.x(), cp.z())) continue;
            if (Math.abs(cp.getMiddleBlockX()) < minDist.get() && Math.abs(cp.getMiddleBlockZ()) < minDist.get()) continue;
            seenAt.putIfAbsent(cp, now);
            if (now - seenAt.get(cp) < settleTicks.get() * 50L) continue;
            Long last = scannedAt.get(cp);
            if (last != null && now - last < rescanSec.get() * 1000L) continue;
            scannedAt.put(cp, now);
            double score = score(mc.level.getChunk(cp.x(), cp.z()), cp);
            if (score >= minScore.get()) {
                scores.put(cp, score);
                if (notify.on() && notified.add(cp)) flag(cp, score);
            } else scores.remove(cp);
        }
    }

    private net.minecraft.world.phys.Vec3 lastPos;

    private double score(net.minecraft.world.level.chunk.LevelChunk chunk, ChunkPos cp) {
        double s = 0;
        boolean trialOnly = true;
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof ShulkerBoxBlockEntity) { if (shulkerHit.on()) { instant.add(cp); return 100; } s += 8; trialOnly = false; }
            else if (be instanceof CrafterBlockEntity) { if (crafterHit.on()) { instant.add(cp); return 100; } s += 6; trialOnly = false; }
            else if (be instanceof ChestBlockEntity) { s += 2; trialOnly = false; }
            else if (be instanceof BarrelBlockEntity) { s += 2; trialOnly = false; }
            else if (be instanceof HopperBlockEntity) { s += 1.5; trialOnly = false; }
            else if (be instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity) {
                // natural spawners sit in cobweb/mossy-cobble rooms — a
                // dungeon + its 2 loot chests used to cross minScore alone.
                if (!naturalSpawner(be.getBlockPos())) { s += 4; trialOnly = false; }
            }
            else if (be instanceof net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity) { s += 0; }   // trial chambers → ignored
            else if (be instanceof BeehiveBlockEntity) {
                // 67Client signal: a FULL honey hive means an active, tended farm
                try {
                    var st = be.getBlockState();
                    int lvl = st.getValue(net.minecraft.world.level.block.state.properties
                        .BlockStateProperties.LEVEL_HONEY);
                    if (lvl >= 5) { s += 6; trialOnly = false; }
                    else if (lvl > 0) { s += 1.5; trialOnly = false; }
                } catch (Exception ignored) { s += 1; }
            }
            else if (be instanceof BellBlockEntity) { return 0; }   // village — consistent with BaseIntel/ChunkFinder
            else if (be instanceof LecternBlockEntity) { s += 1.5; trialOnly = false; }
            else if (be instanceof BeaconBlockEntity) { s += 5; trialOnly = false; }
            else { s += 0.5; }   // misc tile-entity density
        }
        // natural-spawner check: dungeon rooms = mossy cobblestone walls,
        // mineshaft spawners sit inside cobwebs. ~12 probes, cheap.
        if (trialOnly) return 0;   // pure trial-chamber content is natural
        // cross-signals: ChunkFinder flag + live activity + entity hits
        var cf = dev.noxclient.NoxClient.INSTANCE.modules.get("ChunkFinder");
        if (cf instanceof ChunkFinder f && f.flagged().contains(cp)) s += 10;
        long ck = ((long) cp.x() << 32) | (cp.z() & 0xFFFFFFFFL);
        if (ActivityFind.flagged().contains(ck)) s += 8;
        if (EntityFind.flagged().contains(ck)) s += 12;
        return s;
    }

    /** True when a spawner is surrounded by mossy cobblestone or cobwebs —
     *  the dungeon/mineshaft signature, not a base. */
    private boolean naturalSpawner(net.minecraft.core.BlockPos pos) {
        if (mc.level == null) return false;
        for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) for (int dz = -2; dz <= 2; dz++) {
            if (dx == 0 && dy == 0 && dz == 0) continue;
            var b = mc.level.getBlockState(pos.offset(dx, dy, dz)).getBlock();
            if (b == net.minecraft.world.level.block.Blocks.MOSSY_COBBLESTONE
                || b == net.minecraft.world.level.block.Blocks.COBWEB) return true;
        }
        return false;
    }

    private void flag(ChunkPos cp, double score) {
        // merge into a nearby existing flag (67Client middle-flag)
        ChunkPos mid = cp;
        int m = merge.intVal();
        if (m > 0) {
            double sx = 0, sz = 0; int n = 0;
            for (ChunkPos o : scores.keySet())
                if (Math.abs(o.x() - cp.x()) <= m && Math.abs(o.z() - cp.z()) <= m) { sx += o.x(); sz += o.z(); n++; }
            if (n > 0) { sx += cp.x(); sz += cp.z(); n++; mid = new ChunkPos((int) Math.round(sx / n), (int) Math.round(sz / n)); }
        }
        if (mc.player != null)
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§7[NOX] §dSus chunk §f" + mid.x() + " " + mid.z() + " §7score §e" + (int) score
                + (instant.contains(cp) ? " §c(instant-hit)" : "")));
        // world pin at the merged flag so it's visible while flying
        dev.noxclient.util.EspMarks.MARKS.put(
            new net.minecraft.core.BlockPos(mid.getMiddleBlockX(), 80, mid.getMiddleBlockZ()),
            0xFF7A2BE2);
        dev.noxclient.util.StashLogger.log("baseradar",
            new net.minecraft.core.BlockPos(mid.getMiddleBlockX(), 70, mid.getMiddleBlockZ()),
            "score=" + (int) score + (instant.contains(cp) ? " INSTANT" : ""));
    }

    public Map<ChunkPos, Double> scores() { return scores; }
    public Set<ChunkPos> instant() { return instant; }

    @Override protected void onDisable() {
        scores.clear(); seenAt.clear(); scannedAt.clear(); notified.clear(); instant.clear();
    }
}
