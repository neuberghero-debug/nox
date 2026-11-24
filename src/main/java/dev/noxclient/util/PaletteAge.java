package dev.noxclient.util;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/**
 * PaletteExploit — tells freshly generated chunks from disk-loaded ones
 * (technique: Trouser-Streak NewChunks → XaeroPlus PaletteNewChunks).
 *
 * Chunk gen mutates palettes in steps (air → stone → water …). When the
 * server SAVES a chunk it rebuilds ("compacts") every section palette:
 * entries are dropped if unused and reordered by actual storage position.
 * A chunk sent to a player before its first save therefore has palettes
 * that were never compacted:
 *
 *   LinearPalette:  palette order ≠ first-appearance order in the bit storage
 *   HashMapPalette: palette contains entries absent from the storage
 *
 *   uncompacted → generated THIS session → nobody has ever been there
 *   compacted   → existed before → possible base location
 *
 * PalettedContainer.Data is package-private in 26.3, so we grab it once via
 * reflection (per-section cost is one field read).
 */
public final class PaletteAge {
    private static MethodHandle DATA;
    private static boolean init;

    private static MethodHandle data() {
        if (!init) {
            init = true;
            try {
                Field f = PalettedContainer.class.getDeclaredField("data");
                f.setAccessible(true);
                DATA = MethodHandles.lookup().unreflectGetter(f);
            } catch (Throwable t) {
                DATA = null;
            }
        }
        return DATA;
    }

    /** Positive if the section's palette was never compacted (fresh gen). */
    private static boolean uncompacted(LevelChunkSection sec) {
        try {
            @SuppressWarnings("unchecked")
            PalettedContainer<BlockState> states =
                (PalettedContainer<BlockState>) sec.getStates();
            var data = data().invoke(states);
            if (data == null) return false;
            Palette<BlockState> palette = (Palette<BlockState>)
                data.getClass().getMethod("palette").invoke(data);
            var storage = (net.minecraft.util.BitStorage)
                data.getClass().getMethod("storage").invoke(data);
            if (palette == null || storage == null) return false;

            String pName = palette.getClass().getSimpleName();
            if (pName.equals("LinearPalette") || palette.getSize() <= 16) {
                return linearUncompacted(palette, storage);
            }
            return hashUncompacted(palette, storage);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Order check: a compacted palette is sorted by first storage
        appearance — any out-of-order entry means it was never saved. */
    private static boolean linearUncompacted(Palette<BlockState> palette,
                                             net.minecraft.util.BitStorage storage) {
        var paletteIds = new int[palette.getSize()];
        for (int i = 0; i < paletteIds.length; i++)
            paletteIds[i] = Block.getId(palette.valueFor(i));
        Set<Integer> seen = new HashSet<>();
        int[] next = {0};
        boolean[] fresh = {false};
        storage.getAll(dataId -> {
            if (fresh[0] || next[0] >= paletteIds.length) return;
            int id = Block.getId(palette.valueFor(dataId));
            if (!seen.add(id)) return;
            if (id == paletteIds[next[0]]) next[0]++;
            else fresh[0] = true;
        });
        return fresh[0];
    }

    /** Leftover entries: a compacted palette only holds states that exist in
        the storage — any extra = fresh gen remnants. */
    private static boolean hashUncompacted(Palette<BlockState> palette,
                                           net.minecraft.util.BitStorage storage) {
        Set<Integer> present = new HashSet<>();
        storage.getAll(dataId -> present.add(Block.getId(palette.valueFor(dataId))));
        for (int i = 0; i < palette.getSize(); i++)
            if (!present.contains(Block.getId(palette.valueFor(i)))) return true;
        return false;
    }

    /** ≥2 uncompacted sections = the chunk was generated this session. */
    public static boolean isFresh(net.minecraft.world.level.chunk.LevelChunk chunk) {
        if (data() == null || chunk == null) return false;
        int positive = 0;
        for (var sec : chunk.getSections()) {
            if (sec == null || sec.hasOnlyAir()) continue;
            if (uncompacted(sec) && ++positive >= 2) return true;
        }
        return false;
    }

    private PaletteAge() {}
}
