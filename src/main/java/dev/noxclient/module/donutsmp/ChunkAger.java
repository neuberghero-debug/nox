package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.EspMarks;
import dev.noxclient.util.PaletteAge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chunk Ager — knows whether a chunk can even contain a base.
 *
 * Two orthogonal age tests, both passive:
 *
 *  1. PALETTE AGE (util/PaletteAge): freshly generated chunks ship
 *     uncompacted palettes — nobody has ever stood there → NO base
 *     possible → any activity hit inside a fresh chunk means someone is
 *     building RIGHT NOW. A disk-loaded (compacted) chunk existed
 *     before → valid base habitat.
 *
 *  2. GENERATION ERA (block-set check, XaeroPlus OldChunks): a chunk
 *     missing every 1.17+/1.16+ signature block (copper, deepslate,
 *     amethyst, tuff, kelp, dripstone, moss, cave vines / nether:
 *     debris, blackstone, basalt, nylium) was generated in an old
 *     version and upgraded — explored for years = prime old-base
 *     territory.
 *
 * SusChunkFinder folds {@link #age(ChunkPos)} into its score.
 */
public class ChunkAger extends Module {
    private final Setting.Num radius = add(new Setting.Num("Radius (chunks)", 12, 2, 48));
    private final Setting.Bool chatNotify = add(new Setting.Bool("Chat notify", false));
    private final Set<ChunkPos> done = new HashSet<>();
    /** chunkPos.pack() → 1 fresh / 2 disk / 4 old-gen — SusChunkFinder reads this */
    private static final Map<Long, Integer> AGES = new ConcurrentHashMap<>();

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

    /** 1 fresh · 2 disk-loaded · 4 old-generation · 0 unknown */
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

        // fake-ore field: anti-xray stamps hidden blocks as ores fully
        // enclosed by stone — several in one chunk = the server is hiding
        // something here (a zone to probe, not noise)
        int fakes = fakeOres(chunk);
        if (fakes >= 6) {
            BlockPos mid = cp.getMiddleBlockPosition(0);
            EspMarks.MARKS.put(new BlockPos(mid.getX(), 64, mid.getZ()), 0xFFFF3355);
            if (chatNotify.on() && mc.player != null)
                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§7[NOX] §cXRAY-HIDDEN ZONE §8@ chunk " + cp.x() + "," + cp.z()
                    + " §7(" + fakes + " enclosed ores)"));
            mark("xray-zone " + cp);
        }

        if (fresh) {
            // virgin terrain — any finder flag here means a player is present NOW
            long ck = ((long) cp.x() << 32) | (cp.z() & 0xFFFFFFFFL);
            if (ActivityFind.flagged().contains(ck) || EntityFind.flagged().contains(ck)) {
                BlockPos mid = cp.getMiddleBlockPosition(0);
                EspMarks.MARKS.put(new BlockPos(mid.getX(), 80, mid.getZ()), 0xFFFF4444);
                if (chatNotify.on() && mc.player != null)
                    mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§7[NOX] §cLIVE activity on virgin terrain §8@ chunk " + cp.x() + "," + cp.z()));
                mark("live-build " + cp);
            }
        } else if (oldGen && chatNotify.on() && mc.player != null) {
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§7[NOX] §7old-generation chunk §8@ " + cp.x() + "," + cp.z()));
        }
        mark(a == 1 ? "fresh" : a == 4 ? "old" : "disk");
    }

    /** True if the chunk contains ≥5 blocks of the new-gen set — a chunk
        WITHOUT them was generated before those blocks existed. */
    private boolean hasAny(net.minecraft.world.level.chunk.LevelChunk chunk, Set<Block> sig) {
        int found = 0;
        for (var sec : chunk.getSections()) {
            if (sec == null || sec.hasOnlyAir()) continue;
            var states = sec.getStates();
            for (int ly = 0; ly < 16; ly += 2)
                for (int lx = 0; lx < 16; lx += 2)
                    for (int lz = 0; lz < 16; lz += 2)
                        if (sig.contains(states.get(lx, ly, lz).getBlock()) && ++found >= 5)
                            return true;
        }
        return false;
    }

    /** Ores enclosed on all 6 sides by stone-family blocks and with no ore
        neighbor — the anti-xray stamp signature. Sampled per 2 blocks to
        stay cheap (a real field has hundreds). */
    private int fakeOres(net.minecraft.world.level.chunk.LevelChunk chunk) {
        int fakes = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        ChunkPos cpz = chunk.getPos();
        var sections = chunk.getSections();
        for (int si = 0; si < sections.length; si++) {
            var sec = sections[si];
            if (sec == null || sec.hasOnlyAir()) continue;
            var states = sec.getStates();
            int y0 = mc.level.getSectionYFromSectionIndex(si) * 16;
            for (int ly = 1; ly < 15; ly += 2)
                for (int lx = 1; lx < 15; lx += 2)
                    for (int lz = 1; lz < 15; lz += 2) {
                        if (!isOre(states.get(lx, ly, lz).getBlock())) continue;
                        m.set(cpz.getMinBlockX() + lx, y0 + ly, cpz.getMinBlockZ() + lz);
                        if (enclosed(m)) fakes++;
                    }
        }
        return fakes;
    }

    private boolean enclosed(BlockPos pos) {
        for (var d : net.minecraft.core.Direction.values()) {
            var b = mc.level.getBlockState(pos.relative(d)).getBlock();
            if (!stoneFamily(b) && !isOre(b)) return false;
        }
        return true;
    }

    private static boolean stoneFamily(Block b) {
        return b == Blocks.STONE || b == Blocks.DEEPSLATE || b == Blocks.TUFF
            || b == Blocks.ANDESITE || b == Blocks.DIORITE || b == Blocks.GRANITE
            || b == Blocks.NETHERRACK || b == Blocks.BLACKSTONE || b == Blocks.BASALT;
    }

    private static boolean isOre(Block b) {
        return b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE
            || b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE
            || b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE
            || b == Blocks.COAL_ORE || b == Blocks.DEEPSLATE_COAL_ORE
            || b == Blocks.REDSTONE_ORE || b == Blocks.DEEPSLATE_REDSTONE_ORE
            || b == Blocks.LAPIS_ORE || b == Blocks.DEEPSLATE_LAPIS_ORE
            || b == Blocks.COPPER_ORE || b == Blocks.DEEPSLATE_COPPER_ORE
            || b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE
            || b == Blocks.ANCIENT_DEBRIS;
    }

    @Override protected void onDisable() { done.clear(); AGES.clear(); }
    @Override public String status() {
        long f = AGES.values().stream().filter(v -> v == 1).count();
        long o = AGES.values().stream().filter(v -> v == 4).count();
        return f + " fresh · " + o + " old · " + AGES.size() + " classified";
    }
}
