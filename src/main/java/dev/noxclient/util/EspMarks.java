package dev.noxclient.util;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Shared anti-ESP mark map — lives in the free jar so render code can
 *  read/write it while the AntiESPBypass module itself ships in the
 *  encrypted premium pack. Marks carry a short reason label (rendered
 *  next to the square) and expire so one-shot flags don't linger forever. */
public final class EspMarks {
    public record Mark(int color, String label, long born) {}

    public static final Map<BlockPos, Mark> MARKS = new ConcurrentHashMap<>();

    /** One-shot marks fade after this; per-tick emitters keep refreshing. */
    private static final long TTL_MS = 300_000;

    private EspMarks() {}

    public static void put(BlockPos pos, int color, String label) {
        MARKS.put(pos, new Mark(color, label, System.currentTimeMillis()));
    }

    public static void put(BlockPos pos, int color) { put(pos, color, null); }

    /** Drop expired marks — called from the overlay render path. */
    public static void prune() {
        long now = System.currentTimeMillis();
        MARKS.values().removeIf(m -> now - m.born() > TTL_MS);
    }
}
