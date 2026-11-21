package dev.noxclient.util;

import dev.noxclient.NoxClient;
import dev.noxclient.module.donutsmp.ActivityFind;
import dev.noxclient.module.donutsmp.ChunkFinder;
import dev.noxclient.module.donutsmp.EntityFind;
import dev.noxclient.module.donutsmp.SusChunkFinder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared base-detection registry — feeds the BaseFinder panel, BaseESP
 * clustering and the auto-hunt modules. Per chunk we census block entities
 * plus a sparse signature probe, then fold in every finder-module flag.
 * Each entry carries a type label, a 0..99 confidence and section coverage —
 * the Prestige-style "Dug Shaft / SW 92m / Y 8.6 / 9/13 sections / 35%".
 */
public final class BaseIntel {
    public static final class Entry {
        public ChunkPos cp;
        public String type = "Unknown";
        public double score;
        public int pct;
        public int sections;      // sections containing ≥1 signature
        public int scanned;       // sections actually sampled
        public int containers, hoppers, redstone, shulkers;
        public double avgY;
        public boolean instant;   // shulker/crafter/spawner — dead giveaway
        public long at;           // last scan ms
    }

    private static final Map<ChunkPos, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final Map<ChunkPos, Long> scannedAt = new HashMap<>();
    private static final long RESCAN_MS = 20_000;
    private static final long TTL_MS = 240_000;
    private static Iterator<ChunkPos> cycle;

    public static Map<ChunkPos, Entry> entries() { return ENTRIES; }

    /** Call once per client tick from a module. Scans a few chunks per tick. */
    public static void tick(Minecraft mc, int radiusChunks, int perTick) {
        if (mc.level == null || mc.player == null) { ENTRIES.clear(); scannedAt.clear(); cycle = null; return; }
        long now = System.currentTimeMillis();
        ChunkPos pc = mc.player.chunkPosition();
        ENTRIES.values().removeIf(e -> now - e.at > TTL_MS
            || Math.abs(e.cp.x() - pc.x()) > radiusChunks + 6
            || Math.abs(e.cp.z() - pc.z()) > radiusChunks + 6);
        scannedAt.keySet().removeIf(c -> Math.abs(c.x() - pc.x()) > radiusChunks + 6
            || Math.abs(c.z() - pc.z()) > radiusChunks + 6);

        if (cycle == null || !cycle.hasNext())
            cycle = ring(pc, radiusChunks).iterator();
        int n = 0;
        while (cycle.hasNext() && n < perTick) {
            ChunkPos cp = cycle.next();
            Long t = scannedAt.get(cp);
            if (t != null && now - t < RESCAN_MS) continue;
            if (!mc.level.hasChunk(cp.x(), cp.z())) continue;
            scannedAt.put(cp, now);
            n++;
            Entry e = scan(mc, mc.level.getChunk(cp.x(), cp.z()), now);
            if (e != null && e.score >= 3) ENTRIES.merge(cp, e, (a, b) -> b.score >= a.score ? b : a);
            else ENTRIES.remove(cp);
        }
    }

    private static List<ChunkPos> ring(ChunkPos pc, int r) {
        var out = new ArrayList<ChunkPos>((2 * r + 1) * (2 * r + 1));
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++)
            out.add(new ChunkPos(pc.x() + dx, pc.z() + dz));
        // nearest-first so the panel fills in around the player immediately
        out.sort(Comparator.comparingInt(c ->
            (c.x() - pc.x()) * (c.x() - pc.x()) + (c.z() - pc.z()) * (c.z() - pc.z())));
        return out;
    }

    // signature probe: every 4th block per axis inside each non-empty section
    private static Entry scan(Minecraft mc, LevelChunk chunk, long now) {
        ChunkPos cp = chunk.getPos();
        int minY = chunk.getMinY(), maxY = chunk.getMaxY();
        int bx = cp.getMinBlockX(), bz = cp.getMinBlockZ();

        int containers = 0, hoppers = 0, shulkers = 0, crafters = 0;
        int beehives = 0, beacons = 0, miscBe = 0;
        boolean bell = false, spawner = false, instant = false;
        var sectionY = new HashMap<Integer, Integer>();   // section idx → signature count
        var shaftSec = new HashSet<Integer>();            // sections w/ dig-shaft blocks
        var rsSec = new HashSet<Integer>();
        var airSec = new HashMap<Integer, Integer>();     // sections → air samples (excavation)
        var digSec = new HashMap<Integer, Integer>();     // sections → unnatural-block count
        double ySum = 0; int yN = 0;

        for (var be : chunk.getBlockEntities().values()) {
            int s = (be.getBlockPos().getY() - minY) >> 4;
            sectionY.merge(s, 1, Integer::sum);
            ySum += be.getBlockPos().getY(); yN++;
            if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity
                || be instanceof EnderChestBlockEntity) containers++;
            else if (be instanceof ShulkerBoxBlockEntity) { shulkers++; containers++; instant = true; }
            else if (be instanceof HopperBlockEntity || be instanceof DropperBlockEntity
                || be instanceof DispenserBlockEntity) { hoppers++; rsSec.add(s); }
            else if (be instanceof CrafterBlockEntity) { crafters++; hoppers++; rsSec.add(s); instant = true; }
            else if (be instanceof BeehiveBlockEntity) beehives++;
            else if (be instanceof BeaconBlockEntity) beacons++;
            else if (be instanceof BellBlockEntity) bell = true;
            else if (be instanceof SpawnerBlockEntity) { spawner = true; instant = true; }
            else miscBe++;
        }
        if (bell) return null;   // village — permanent false positive, skip entirely

        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int totalSec = 0, sIdx = -1;
        for (var sec : chunk.getSections()) {
            sIdx++;
            totalSec++;
            if (sec == null || !sec.maybeHas((st) -> !st.isAir())) continue;
            int sBase = minY + sIdx * 16;
            int air = 0;
            for (int x = 0; x < 16; x += 4) for (int z = 0; z < 16; z += 4)
                for (int y = 0; y < 16; y += 4) {
                    p.set(bx + x, sBase + y, bz + z);
                    var b = chunk.getBlockState(p);
                    if (b.isAir()) { air++; continue; }
                    var bl = b.getBlock();
                    boolean sig = false;
                    if (bl == Blocks.TORCH || bl == Blocks.WALL_TORCH || bl == Blocks.LADDER
                        || bl == Blocks.RAIL || bl == Blocks.POWERED_RAIL
                        || bl == Blocks.DETECTOR_RAIL || bl == Blocks.ACTIVATOR_RAIL
                        || bl == Blocks.LANTERN || bl == Blocks.SOUL_LANTERN
                        || bl == Blocks.VINE) { shaftSec.add(sIdx); sig = true; }
                    else if (bl == Blocks.REDSTONE_WIRE || bl == Blocks.REPEATER
                        || bl == Blocks.COMPARATOR || bl == Blocks.OBSERVER
                        || bl == Blocks.PISTON || bl == Blocks.STICKY_PISTON
                        || bl == Blocks.REDSTONE_BLOCK) { rsSec.add(sIdx); sig = true; }
                    else if ((bl == Blocks.OAK_PLANKS || bl == Blocks.SPRUCE_PLANKS
                        || bl == Blocks.BIRCH_PLANKS || bl == Blocks.DARK_OAK_PLANKS
                        || bl == Blocks.CRAFTING_TABLE || bl == Blocks.COBBLESTONE
                        || bl == Blocks.STRIPPED_OAK_LOG || bl == Blocks.STRIPPED_SPRUCE_LOG
                        || bl == Blocks.STRIPPED_DARK_OAK_LOG) && sBase + y < 40) {
                        // planks+stripped supports deep underground don't generate —
                        // classic dug-shaft / strip-mine reinforcement
                        digSec.merge(sIdx, 1, Integer::sum); sig = true;
                    }
                    if (sig) { sectionY.merge(sIdx, 1, Integer::sum); ySum += sBase + y; yN++; }
                }
            if (sBase < 32 && air > 40) airSec.merge(sIdx, air, Integer::sum);
        }

        int sections = sectionY.size();
        double score = containers * 2.0 + hoppers * 2.5 + shulkers * 6 + crafters * 8
            + beacons * 5 + beehives * 3 + miscBe * 0.5 + digSec.size() * 2
            + rsSec.size() * 1.5 + shaftSec.size();

        // cross-signals from the other finders — reuse their verdicts as evidence
        var cf = NoxClient.INSTANCE.modules.get("ChunkFinder");
        if (cf instanceof ChunkFinder f && f.flagged().contains(cp)) score += 10;
        var sus = NoxClient.INSTANCE.modules.get("SusChunkFinder");
        if (sus instanceof SusChunkFinder sf) {
            Double s = sf.scores().get(cp);
            if (s != null) score += s * 0.5;
        }
        long ck = ((long) cp.x() << 32) | (cp.z() & 0xFFFFFFFFL);
        boolean activity = ActivityFind.flagged().contains(ck);
        boolean entity = EntityFind.flagged().contains(ck);
        if (activity) score += 8;
        if (entity) score += 12;
        if (score < 3) return null;

        Entry e = new Entry();
        e.cp = cp; e.score = score; e.at = now;
        e.containers = containers; e.hoppers = hoppers; e.shulkers = shulkers;
        e.redstone = rsSec.size(); e.sections = sections; e.scanned = totalSec;
        e.instant = instant || spawner;
        e.avgY = yN > 0 ? ySum / yN : (minY + maxY) / 2.0;
        // type = dominant evidence, prestige-style labels
        int shaftHits = shaftSec.size() + digSec.size();
        if (shulkers > 0 || containers >= 8) e.type = "Stash";
        else if (rsSec.size() >= 2 || crafters > 0) e.type = "Redstone";
        else if (shaftHits >= 2) e.type = "Dug Shaft";
        else if (!airSec.isEmpty() && sections >= 2) e.type = "Excavated";
        else if (beehives > 0) e.type = "Farm";
        else if (entity || activity) e.type = "Unknown";
        else if (containers > 0 || hoppers > 0) e.type = "Stash";
        e.pct = Math.min(99, (int) Math.round(score * 100 / 42));
        return e;
    }

    /** Best entries within range of a position, sorted by confidence desc. */
    public static List<Entry> top(BlockPos from, int limit) {
        var out = new ArrayList<>(ENTRIES.values());
        out.sort(Comparator.comparingInt((Entry e) -> -e.pct));
        if (out.size() > limit) out = new ArrayList<>(out.subList(0, limit));
        return out;
    }

    public static void clear() { ENTRIES.clear(); scannedAt.clear(); cycle = null; }
}
