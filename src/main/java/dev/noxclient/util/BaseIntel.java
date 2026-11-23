package dev.noxclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central base-hunt intelligence store. Every finder writes hits here —
 * positions, a category color, a human label and a per-source score. The
 * overlay renders it, the stash logger persists it, the BaseFinder module
 * ranks chunks off it.
 *
 * Hit merge rule: same block position + same source bumps the counter; a hit
 * within `mergeRadius` of an existing mark with the same category merges into
 * it (the label keeps the loudest source). This keeps the map small without
 * losing cluster shape.
 */
public final class BaseIntel {
    public static final class Hit {
        public final String source;
        public final int color;
        public String label;
        public long at;
        public int count;
        public int score;
        Hit(String source, int color, String label, int score) {
            this.source = source; this.color = color; this.label = label;
            this.score = score; this.at = System.currentTimeMillis(); this.count = 1;
        }
    }

    private static final Map<BlockPos, Hit> HITS = new LinkedHashMap<>();
    private static final Map<String, Long> NOTIFY_CD = new HashMap<>();
    private static final int CAP = 6000;
    private static final long NOTIFY_MS = 10_000;
    private static final int MERGE_R = 3;

    /** Record a signal. Returns the stored hit (for extra notes). */
    public static synchronized Hit hit(String source, BlockPos pos, int color,
                                       String label, int score) {
        Hit h = HITS.get(pos);
        if (h == null) {
            // merge into a near-identical mark so we don't drown the map
            BlockPos mergeInto = null;
            for (var e : HITS.entrySet()) {
                if (e.getKey().distSqr(pos) <= MERGE_R * MERGE_R && e.getValue().source.equals(source)) {
                    mergeInto = e.getKey(); break;
                }
            }
            if (mergeInto != null) {
                h = HITS.get(mergeInto);
                h.count++; h.at = System.currentTimeMillis(); h.score = Math.max(h.score, score);
                if (score >= h.score) h.label = label;
                return h;
            }
            h = new Hit(source, color, label, score);
            if (HITS.size() >= CAP) {
                var it = HITS.entrySet().iterator();
                if (it.hasNext()) { it.next(); it.remove(); }
            }
            HITS.put(pos.immutable(), h);
            StashLogger.log(source, pos, label);
            return h;
        }
        h.count++; h.at = System.currentTimeMillis(); h.score = Math.max(h.score, score);
        return h;
    }

    /** Rate-limited client-side chat ping for a new signal. */
    public static void notify(String key, String msg) {
        long now = System.currentTimeMillis();
        Long last = NOTIFY_CD.get(key);
        if (last != null && now - last < NOTIFY_MS) return;
        NOTIFY_CD.put(key, now);
        var mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(msg));
    }

    /** Chunk-level score: sum of hit scores inside the chunk. */
    public static synchronized int score(ChunkPos cp) {
        int s = 0;
        for (var e : HITS.entrySet()) {
            if ((e.getKey().getX() >> 4) == cp.x() && (e.getKey().getZ() >> 4) == cp.z())
                s += e.getValue().score + Math.min(3, e.getValue().count - 1);
        }
        return s;
    }

    /** Ranked chunk summary — the BaseFinder "top leads" list. */
    public static synchronized List<String> topChunks(int n, ChunkPos near) {
        Map<Long, int[]> byChunk = new HashMap<>();
        for (var e : HITS.entrySet()) {
            long key = ChunkPos.containing(e.getKey()).pack();
            int[] agg = byChunk.computeIfAbsent(key, k -> new int[2]);
            agg[0] += e.getValue().score; agg[1]++;
        }
        var list = new ArrayList<>(byChunk.entrySet());
        list.sort(Comparator.<Map.Entry<Long, int[]>>comparingInt(e -> -e.getValue()[0]));
        var out = new ArrayList<String>();
        int i = 0;
        for (var e : list) {
            if (i++ >= n) break;
            ChunkPos cp = ChunkPos.unpack(e.getKey());
            int d = near == null ? 0 : (int) Math.sqrt(
                (cp.x() - near.x()) * (cp.x() - near.x())
              + (cp.z() - near.z()) * (cp.z() - near.z())) * 16;
            out.add(String.format("%d,%d  score=%d  marks=%d  ~%dm",
                cp.x() * 16 + 8, cp.z() * 16 + 8, e.getValue()[0], e.getValue()[1], d));
        }
        return out;
    }

    /** Snapshot views for the overlay. */
    public static synchronized Map<BlockPos, Integer> colors() {
        Map<BlockPos, Integer> m = new HashMap<>(HITS.size());
        for (var e : HITS.entrySet()) m.put(e.getKey(), e.getValue().color);
        return m;
    }

    /** Lock-held iteration — overlay calls this once per frame, no copies. */
    public static synchronized void forEach(java.util.function.BiConsumer<BlockPos, Hit> fn) {
        for (var e : HITS.entrySet()) fn.accept(e.getKey(), e.getValue());
    }

    public static synchronized List<Map.Entry<BlockPos, Hit>> hitsSnapshot() {
        return new ArrayList<>(HITS.entrySet());
    }

    public static synchronized void clear() { HITS.clear(); NOTIFY_CD.clear(); }
    public static synchronized int size() { return HITS.size(); }

    private BaseIntel() {}
}
