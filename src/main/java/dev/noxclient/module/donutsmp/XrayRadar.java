package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.BaseIntel;
import dev.noxclient.util.BaseSigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Xray Radar [PREMIUM] — turns the server's OWN anti-xray against it.
 *
 * Paper engine-mode-2 anti-xray has two fingerprints you can see from the
 * surface:
 *
 *  1. FAKE ORES — hidden blocks are sent as ore inside solid stone. A lone
 *     ore sealed on all 6 sides is how the server disguises what it won't
 *     show you. Counting them per chunk maps out exactly WHERE the server
 *     is hiding something — fake-ore density = concealed-base probability.
 *
 *  2. REVEAL FLIPS — when you walk close to an obfuscated block the server
 *     flips it to the real state with a live block-update. We diff every
 *     update against the state the chunk claimed:
 *       ore → stone/air  = a fake ore just got revealed (proof of xray here)
 *       stone → storage/ = a hidden chest/redstone just leaked its real state
 *        mech/portal
 *     Every flip is a confirmed "the server is hiding THIS position".
 *
 * Zero outbound packets — pure interception. The "movement bypass" part:
 * just walking near a suspected zone forces the server to send the flips;
 * BaseIntel marks tell you where to sweep.
 */
public class XrayRadar extends Module {
    private final Setting.Num radius = add(new Setting.Num("Scan radius (chunks)", 10, 2, 32));
    private final Setting.Num fakeOreMin = add(new Setting.Num("Fake-ore cluster min", 3, 1, 20));
    private final Setting.Num maxScanY = add(new Setting.Num("Max scan Y", 60, -64, 256));
    private final Setting.Bool chatNotify = add(new Setting.Bool("Chat notify", true));

    /** fake-ore signature set — everything Paper ships as default disguise */
    private static final Set<Block> FAKE_ORES = Set.of(
        Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.COPPER_ORE, Blocks.GOLD_ORE,
        Blocks.REDSTONE_ORE, Blocks.LAPIS_ORE, Blocks.DIAMOND_ORE, Blocks.EMERALD_ORE,
        Blocks.DEEPSLATE_COAL_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.DEEPSLATE_COPPER_ORE,
        Blocks.DEEPSLATE_GOLD_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
        Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.DEEPSLATE_EMERALD_ORE, Blocks.ANCIENT_DEBRIS,
        Blocks.NETHER_GOLD_ORE, Blocks.NETHER_QUARTZ_ORE);
    private static final Set<Block> STONE_FAMILY = Set.of(
        Blocks.STONE, Blocks.DEEPSLATE, Blocks.TUFF, Blocks.ANDESITE, Blocks.DIORITE,
        Blocks.GRANITE, Blocks.BASALT, Blocks.BLACKSTONE, Blocks.NETHERRACK,
        Blocks.END_STONE, Blocks.GRAVEL, Blocks.DIRT);

    private final Set<ChunkPos> scanned = new HashSet<>();
    private final Map<Long, int[]> flips = new HashMap<>(); // chunkKey → [flips, window]
    private long lastSweep;

    public XrayRadar() {
        super("XrayRadar", "Maps where anti-xray is hiding blocks", Category.DONUTSMP);
        premium = true;
    }

    /* ---------- part 1: the reveal-flip diff ---------- */

    @Override
    public void onPacket(Packet<?> packet) {
        if (mc.player == null || mc.level == null) return;
        if (packet instanceof ClientboundBlockUpdatePacket p) {
            diff(p.getPos(), p.getBlockState());
        } else if (packet instanceof ClientboundSectionBlocksUpdatePacket p) {
            p.runUpdates(this::diff);
        } else if (packet instanceof ClientboundAddTransientBlockPacket p) {
            diff(p.getPos(), p.getBlockState());
        }
    }

    /** Runs at packet HEAD — mc.level still holds the state the chunk
        claimed. Comparing old vs new exposes what the server just told the
        truth about. */
    private void diff(BlockPos pos, net.minecraft.world.level.block.state.BlockState next) {
        var prev = mc.level.getBlockState(pos);
        Block pb = prev.getBlock(), nb = next.getBlock();
        if (pb == nb) return;

        if (FAKE_ORES.contains(pb) && (STONE_FAMILY.contains(nb) || next.isAir()
            || nb == Blocks.LAVA || nb == Blocks.WATER || nb == Blocks.BEDROCK)) {
            // the ore the chunk showed us just became mundane — it was a
            // disguise. The server IS hiding this exact spot.
            emit("flip:fakeore", pos, BaseSigs.Cat.INFRA,
                "FAKE ORE confirmed — hidden zone", 8);
            flip(pos);
        } else if ((STONE_FAMILY.contains(pb) || FAKE_ORES.contains(pb) || prev.isAir())
            && !next.isAir()) {
            var cat = BaseSigs.cat(next);
            if (cat != null && cat != BaseSigs.Cat.ACTIVITY) {
                var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(nb);
                emit("flip:reveal", pos, cat,
                    "revealed: " + (key == null ? nb.toString() : key.getPath()), 8);
                flip(pos);
            }
        }
    }

    /** A flip marks the chunk as obfuscation-active: the more flips, the
        more the server is hiding there. */
    private void flip(BlockPos pos) {
        long now = System.currentTimeMillis();
        long key = ChunkPos.pack(pos);
        int[] st = flips.computeIfAbsent(key, k -> new int[]{0, (int) (now / 1000)});
        if (now / 1000 - st[1] > 300) { st[0] = 0; st[1] = (int) (now / 1000); }
        st[0]++;
        if (st[0] == 3) {
            emit("zone:xray", pos, BaseSigs.Cat.INFRA,
                "XRAY-HIDDEN ZONE — server concealing this chunk", 9);
        }
        if (now - lastSweep > 120_000) {
            lastSweep = now;
            flips.values().removeIf(v -> now / 1000 - v[1] > 600);
        }
    }

    /* ---------- part 2: the enclosed-ore chunk scan ---------- */

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        ChunkPos pc = mc.player.chunkPosition();
        int r = radius.intVal();
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            ChunkPos cp = new ChunkPos(pc.x() + dx, pc.z() + dz);
            if (scanned.contains(cp) || !mc.level.hasChunk(cp.x(), cp.z())) continue;
            scanned.add(cp);
            scan(cp);
            return; // one column per tick
        }
    }

    /** Count lone fully-sealed ores — the tell-tale disguise pattern. Real
        veins generate in clusters and usually touch a cavity somewhere; a
        SINGLE ore with all 6 faces against opaque stone at depth is the
        shape anti-xray stamps over a hidden block. */
    private void scan(ChunkPos cp) {
        int enclosed = 0;
        BlockPos first = null;
        int topY = Math.min(maxScanY.intVal(), mc.level.getMaxY() - 1);
        int minY = mc.level.getMinY();
        var chunk = mc.level.getChunk(cp.x(), cp.z());
        var sections = chunk.getSections();
        for (int si = 0; si < sections.length; si++) {
            var sec = sections[si];
            if (sec == null || sec.hasOnlyAir()) continue;
            int y0 = mc.level.getSectionYFromSectionIndex(si) * 16;
            if (y0 > topY || y0 + 15 < minY) continue;
            var states = sec.getStates();
            var m = new BlockPos.MutableBlockPos();
            for (int ly = 0; ly < 16; ly++) {
                int wy = y0 + ly;
                if (wy < minY || wy > topY) continue;
                for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
                    var st = states.get(lx, ly, lz);
                    if (!FAKE_ORES.contains(st.getBlock())) continue;
                    m.set(cp.getMinBlockX() + lx, wy, cp.getMinBlockZ() + lz);
                    if (sealed(m) && lone(m, st.getBlock())) {
                        if (first == null) first = m.immutable();
                        enclosed++;
                    }
                }
            }
        }
        if (enclosed >= fakeOreMin.intVal() && first != null) {
            emit("fakeores:" + cp, first, BaseSigs.Cat.INFRA,
                "fake-ore field (" + enclosed + ") — hidden zone", 7);
        }
    }

    /** All six neighbors opaque — the disguise only happens inside stone. */
    private boolean sealed(BlockPos pos) {
        for (Direction d : Direction.values()) {
            var s = mc.level.getBlockState(pos.relative(d));
            if (!s.isSolidRender()) return false;
        }
        return true;
    }

    /** No same-type ore neighbor — real veins cluster, disguises don't. */
    private boolean lone(BlockPos pos, Block ore) {
        for (Direction d : Direction.values())
            if (mc.level.getBlockState(pos.relative(d)).getBlock() == ore) return false;
        return true;
    }

    private void emit(String src, BlockPos pos, BaseSigs.Cat cat, String detail, int score) {
        var h = BaseIntel.hit(src, pos, cat.color, detail, score);
        mark(detail);
        if (chatNotify.on() && h.count == 1) {
            BaseIntel.notify("xr" + pos.toShortString().hashCode(),
                String.format("§d[NOX] §f%s §7(%s) §8@ %s", detail, cat.label, pos.toShortString()));
        }
    }

    @Override protected void onDisable() { scanned.clear(); flips.clear(); }
    @Override public String status() {
        int zones = 0;
        for (var v : flips.values()) if (v[0] > 0) zones++;
        return zones + " xray zones · " + scanned.size() + " scanned";
    }
}
