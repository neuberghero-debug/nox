package dev.noxclient.util;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Shared anti-ESP mark map — lives in the free jar so render code can
 *  read/write it while the AntiESPBypass module itself ships in the
 *  encrypted premium pack. */
public final class EspMarks {
    public static final Map<BlockPos, Integer> MARKS = new ConcurrentHashMap<>();

    private EspMarks() {}
}
