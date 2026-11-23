package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.BaseIntel;
import dev.noxclient.util.BaseSigs;
import dev.noxclient.util.PaletteAge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.Set;

/**
 * Chunk Ager — knows whether a chunk can even contain a base.
 *
 * Two orthogonal age tests, both passive:
 *
 *  1. PALETTE AGE (PaletteAge): freshly generated chunks ship uncompacted
 *     palettes — nobody has ever stood there → NO base possible → other
 *     finders' hits in a fresh chunk mean someone is building RIGHT NOW.
 *     A disk-loaded (compacted) chunk existed before → valid base habitat.
 *
 *  2. GENERATION ERA (block-set check, XaeroPlus OldChunks): a chunk missing
 *     every 1.17+/1.16+ signature block (copper, deepslate, amethyst, tuff,
 *     kelp, dripstone, moss, cave vines / nether: debris, blackstone, basalt,
 *     nylium) was generated in an old version and upgraded — explored for
 *     years = prime old-base territory.
 *
 * Scores go into BaseIntel so RTPBaseFinder/SusChunkFinder rank them in.
 */
public class ChunkAger extends Module {
    private final Setting.Num radius = add(new Setting.Num("Radius (chunks)", 12, 2, 48));
    private final Setting.Bool chatNotify = add(new Setting.Bool("Chat notify", false));
    private final Set<ChunkPos> done = new HashSet<>();
    /** chunkPos → +1 fresh / +2 disk / +4 old-gen — SusChunkFinder reads this */
    private static final java.util.Map<Long, Integer> AGES = new java.util.concurrent.ConcurrentHashMap<>();

    private static final Set<Block> OVERWORLD_NEW = Set.of(
        Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE, Blocks.AMETHYST_BLOCK,
        Blocks.SMOOTH_BASALT, Blocks.TUFF, Blocks.KELP, Blocks.KELP_PLANT,
        Blocks.POINTED_DRIPSTONE, Blocks.DRIPSTONE_BLOCK, Blocks.DEEPSLATE,
        Blocks.AZALEA, Blocks.BIG_DRIPLEAF, Blocks.BIG_DRIPLEAF_STEM,
        Blocks.SMALL_DRIPLEAF, Blocks.MOSS_BLOCK, Blocks.CAVE_VINES,
        Blocks.CAVE_VINES_PLANT);
    private static final Set<Block> NETHER_NEW = Set.of(
        Blocks.ANCIENT_DEBRIS, Blocks.BLACKSTONE, Blocks.BASALT,
        Blocks.CRIMSON_NYLIUM, Blocks.WARPED_NYLIUM, Blocks.NETHER_GOLD_ORE);

    public ChunkAger() {
        super("ChunkAger", "Fresh vs old terrain — where bases can exist", Category.DONUTSMP);
    }

    /** +1 fresh · +2 disk-loaded · +4 old-generation · 0 unknown */
    public static int age(ChunkPos cp) {
        Integer v = AGES.get(cp.pack());
        return v == null ? 0 : v;
    }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        ChunkPos pc = mc.player.chunkPosition();
        int r = radius.intVal();
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            ChunkPos cp = new ChunkPos(pc.x() + dx, pc.z() + dz);
            if (done.contains(cp) || !mc.level.hasChunk(cp.x(), cp.z())) continue;
            done.add(cp);
            classify(cp);
            return; // one column per tick
        }
    }

    private void classify(ChunkPos cp) {
        var chunk = mc.level.getChunk(cp.x(), cp.z());
        boolean fresh = PaletteAge.isFresh(chunk);
        boolean nether = mc.level.dimension().identifier().getPath().contains("nether");
        boolean oldGen = !hasAny(chunk, nether ? NETHER_NEW : OVERWORLD_NEW);
        int a = fresh ? 1 : oldGen ? 4 : 2;
        AGES.put(cp.pack(), a);

        if (fresh) {
            // virgin terrain — any intel here means a player is present NOW
            int s = BaseIntel.score(cp);
            if (s > 0) {
                var h = BaseIntel.hit("livebuild", cp.getMiddleBlockPosition(0),
                    BaseSigs.Cat.PLAYER.color, "LIVE activity on virgin terrain (player there now)", 9);
                if (chatNotify.on() && h.count == 1)
                    BaseIntel.notify("live" + cp, "§c[NOX] §fLIVE build-activity §8@ chunk " + cp.x() + "," + cp.z());
            }
        } else if (oldGen) {
            var h = BaseIntel.hit("oldchunk", cp.getMiddleBlockPosition(0),
                BaseSigs.Cat.INFRA.color, "old-generation chunk (pre-1.17 era)", 3);
            if (chatNotify.on() && h.count == 1)
                BaseIntel.notify("old" + cp, "§9[NOX] §7old chunk §8@ " + cp.x() + "," + cp.z());
        }
        mark(a == 1 ? "fresh" : a == 4 ? "OLD" : "disk");
    }

    /** True if the chunk contains at least `need` blocks of the new-gen set —
        a chunk WITHOUT them was generated before those blocks existed. */
    private boolean hasAny(net.minecraft.world.level.chunk.LevelChunk chunk, Set<Block> sig) {
        int need = 5, found = 0;
        for (var sec : chunk.getSections()) {
            if (sec == null || sec.hasOnlyAir()) continue;
            var states = sec.getStates();
            for (int ly = 0; ly < 16; ly += 2)
                for (int lx = 0; lx < 16; lx += 2)
                    for (int lz = 0; lz < 16; lz += 2)
                        if (sig.contains(states.get(lx, ly, lz).getBlock()) && ++found >= need)
                            return true;
        }
        return false;
    }

    @Override protected void onDisable() { done.clear(); AGES.clear(); }
    @Override public String status() {
        long f = AGES.values().stream().filter(v -> v == 1).count();
        long o = AGES.values().stream().filter(v -> v == 4).count();
        return f + " fresh · " + o + " old · " + AGES.size() + " classified";
    }
}
