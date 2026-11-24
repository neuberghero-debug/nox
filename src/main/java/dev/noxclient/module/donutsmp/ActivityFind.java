package dev.noxclient.module.donutsmp;

import dev.noxclient.gui.Toasts;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.render.NoxRender;
import dev.noxclient.setting.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ActivityFind — live radar for what players are DOING right now in the
 * chunks your client already tracks. Every signal here is a passive
 * broadcast the server sends to all chunk trackers, depth-blind:
 *   block place/break   — building/digging activity, real-time
 *   dig bursts          — ≥4 solid→air updates per chunk per minute = mining
 *   storage removal     — chest/barrel/shulker replaced = someone looting
 *   explosions          — TNT/creepers in the wild = raid activity
 *   sign data writes    — players write signs, the world doesn't
 *   item pickups        — a non-self entity vacuuming drops
 *   damage events       — combat inside tracked range
 * No packets sent. Grim sees nothing; the events arrive anyway.
 */
public class ActivityFind extends Module {
    private final Setting.Bool placement = add(new Setting.Bool("Placement", true,
        "Player-signature blocks appearing (storage, redstone, doors, rails, signs…)"));
    private final Setting.Bool digging = add(new Setting.Bool("Digging", true,
        "Solid→air bursts in a chunk = live mining (≥4/min)"));
    private final Setting.Bool storageGone = add(new Setting.Bool("Storage removed", true,
        "A chest/barrel/shulker that vanished — someone is looting or moving"));
    private final Setting.Bool explosions = add(new Setting.Bool("Explosions", true));
    private final Setting.Bool signs = add(new Setting.Bool("Sign writes", true));
    private final Setting.Bool pickups = add(new Setting.Bool("Item pickups", false,
        "Non-self entities vacuuming drops — noisy near farms"));
    private final Setting.Bool combat = add(new Setting.Bool("Combat", true,
        "Damage events on other entities — fights in tracked chunks"));
    private final Setting.Num ignoreNear = add(new Setting.Num("Ignore radius", 12, 0, 64));
    private final Setting.Num deepDrop = add(new Setting.Num("Deep below", 18, 5, 64));
    private final Setting.Num ttl = add(new Setting.Num("Marker TTL s", 300, 30, 1800));
    private final Setting.Bool chatNotify = add(new Setting.Bool("Chat notify", true));

    public static final Map<String, Mark> MARKS = new LinkedHashMap<>();
    /** Chunks with recent strong activity — read by SusChunkFinder as
     *  cross-signal. Entries EXPIRE with the marker TTL — a one-off dig
     *  burst must not boost a chunk's score forever. */
    private static final Map<Long, Long> flagAt = new ConcurrentHashMap<>(); // chunkKey -> lastFlagMs
    private static final Map<Long, long[]> digWindow = new ConcurrentHashMap<>(); // chunkKey -> [windowStart,count]
    private static ActivityFind inst;
    private NoxRender.Emitter emitter;

    public static final class Mark {
        public double x, y, z;
        public int count, color;
        public long lastMs;
        public boolean deep;
        public final String label;
        Mark(String label, int color, double x, double y, double z) {
            this.label = label; this.color = color;
            this.x = x; this.y = y; this.z = z;
            this.count = 1; this.lastMs = System.currentTimeMillis();
        }
        void bump(double nx, double ny, double nz, boolean d) {
            x = x + (nx - x) / count; y = y + (ny - y) / count; z = z + (nz - z) / count;
            count++; lastMs = System.currentTimeMillis(); deep |= d;
        }
    }

    public ActivityFind() {
        super("ActivityFind", "Live radar — digging, placing, looting, combat in tracked chunks",
            Category.DONUTSMP);
        inst = this;
    }

    @Override protected void onEnable() {
        MARKS.clear(); flagAt.clear(); digWindow.clear();
        emitter = this::emit;
        NoxRender.add(emitter);
    }
    @Override protected void onDisable() {
        MARKS.clear(); flagAt.clear(); digWindow.clear();
        NoxRender.remove(emitter);
    }
    @Override public String status() { return MARKS.size() + " marks"; }
    public static Set<Long> flagged() { return flagAt.keySet(); }

    @Override public void onTick() {
        long cut = System.currentTimeMillis() - (long) (ttl.get() * 1000);
        MARKS.values().removeIf(m -> m.lastMs < cut);
        flagAt.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue() > 300_000);
        digWindow.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue()[0] > 120_000);
    }

    // ---------- classification ----------

    private static boolean signatureBlock(Block b) {
        return b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.BARREL
            || b == Blocks.SHULKER_BOX || b == Blocks.ENDER_CHEST
            || b == Blocks.FURNACE || b == Blocks.BLAST_FURNACE || b == Blocks.SMOKER
            || b == Blocks.HOPPER || b == Blocks.DROPPER || b == Blocks.DISPENSER
            || b == Blocks.OBSERVER || b == Blocks.COMPARATOR || b == Blocks.REPEATER
            || b == Blocks.PISTON || b == Blocks.STICKY_PISTON || b == Blocks.PISTON_HEAD
            || b == Blocks.REDSTONE_WIRE || b == Blocks.REDSTONE_TORCH || b == Blocks.REDSTONE_WALL_TORCH
            || b == Blocks.CRAFTING_TABLE || b == Blocks.SMITHING_TABLE || b == Blocks.CARTOGRAPHY_TABLE
            || b == Blocks.FLETCHING_TABLE || b == Blocks.LOOM || b == Blocks.STONECUTTER
            || b == Blocks.GRINDSTONE || b == Blocks.ANVIL || b == Blocks.CHIPPED_ANVIL
            || b == Blocks.DAMAGED_ANVIL || b == Blocks.ENCHANTING_TABLE || b == Blocks.BREWING_STAND
            || b == Blocks.BEACON || b == Blocks.CONDUIT || b == Blocks.LODESTONE
            || b == Blocks.RESPAWN_ANCHOR || b == Blocks.BELL || b == Blocks.JUKEBOX
            || b == Blocks.NOTE_BLOCK || b == Blocks.LEVER
            || b == Blocks.RAIL || b == Blocks.POWERED_RAIL || b == Blocks.DETECTOR_RAIL
            || b == Blocks.ACTIVATOR_RAIL
            || b == Blocks.SOUL_LANTERN || b == Blocks.LANTERN
            || b == Blocks.LECTERN || b == Blocks.CRAFTER || b == Blocks.DECORATED_POT;
    }

    private static boolean signFamily(Block b) {
        String n = b.toString();
        return n.contains("sign") || n.contains("_bed") || n.contains("_door")
            || n.contains("trapdoor") || n.contains("fence_gate");
    }

    private static boolean storageBlock(Block b) {
        return b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.BARREL
            || b == Blocks.SHULKER_BOX || b == Blocks.ENDER_CHEST
            || b == Blocks.FURNACE || b == Blocks.BLAST_FURNACE || b == Blocks.SMOKER
            || b == Blocks.HOPPER || b == Blocks.BEEHIVE;
    }

    private static boolean solidNatural(Block b) {
        return b == Blocks.STONE || b == Blocks.DEEPSLATE || b == Blocks.TUFF
            || b == Blocks.ANDESITE || b == Blocks.DIORITE || b == Blocks.GRANITE
            || b == Blocks.DIRT || b == Blocks.GRASS_BLOCK || b == Blocks.GRAVEL
            || b == Blocks.SAND || b == Blocks.SANDSTONE || b == Blocks.NETHERRACK
            || b == Blocks.BLACKSTONE || b == Blocks.BASALT || b == Blocks.CALCITE
            || b == Blocks.CLAY || b == Blocks.MUD || b == Blocks.PACKED_MUD
            || b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE
            || b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE
            || b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE
            || b == Blocks.COAL_ORE || b == Blocks.DEEPSLATE_COAL_ORE
            || b == Blocks.REDSTONE_ORE || b == Blocks.DEEPSLATE_REDSTONE_ORE
            || b == Blocks.LAPIS_ORE || b == Blocks.DEEPSLATE_LAPIS_ORE
            || b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE
            || b == Blocks.COPPER_ORE || b == Blocks.DEEPSLATE_COPPER_ORE
            || b == Blocks.ANCIENT_DEBRIS || b == Blocks.OBSIDIAN;
    }

    private static boolean isOre(Block b) {
        return b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE
            || b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE
            || b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE
            || b == Blocks.COAL_ORE || b == Blocks.DEEPSLATE_COAL_ORE
            || b == Blocks.REDSTONE_ORE || b == Blocks.DEEPSLATE_REDSTONE_ORE
            || b == Blocks.LAPIS_ORE || b == Blocks.DEEPSLATE_LAPIS_ORE
            || b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE
            || b == Blocks.COPPER_ORE || b == Blocks.DEEPSLATE_COPPER_ORE
            || b == Blocks.ANCIENT_DEBRIS;
    }

    private static boolean caveNoise(Block b) {
        // things that change on their own — fluids, falling blocks, plants, light
        String n = b.toString();
        return b == Blocks.WATER || b == Blocks.LAVA || b == Blocks.AIR
            || b == Blocks.CAVE_AIR || b == Blocks.VOID_AIR || b == Blocks.FIRE
            || b == Blocks.SOUL_FIRE || n.contains("sapling") || n.contains("leaves")
            || n.contains("crop") || n.contains("torch") && !n.contains("redstone")
            || b == Blocks.GLOW_LICHEN || b == Blocks.VINE || b == Blocks.POWDER_SNOW
            || n.contains("bubble") || n.contains("kelp") || n.contains("seagrass");
    }

    private static long chunkKey(int x, int z) {
        return ((long) (x >> 4) << 32) | (z >> 4 & 0xFFFFFFFFL);
    }

    // ---------- packet entry points (mixin, main thread) ----------

    public static void onBlockUpdate(ClientboundBlockUpdatePacket p, BlockState oldState) {
        if (inst == null || !inst.isEnabled() || inst.mc.level == null) return;
        inst.onBlockChange(p.getPos(), oldState, p.getBlockState());
    }

    public static void onSectionBlocks(ClientboundSectionBlocksUpdatePacket p) {
        if (inst == null || !inst.isEnabled() || inst.mc.level == null) return;
        p.runUpdates((pos, state) -> {
            try { inst.onBlockChange(pos, inst.mc.level.getBlockState(pos), state); }
            catch (Throwable ignored) {}
        });
    }

    private void onBlockChange(BlockPos pos, BlockState oldState, BlockState newState) {
        Block oldB = oldState.getBlock(), newB = newState.getBlock();
        if (oldB == newB) {
            // state-only change (e.g. door open, repeater tick) — signature blocks only
            if (placement.on() && signatureBlock(newB) && !caveNoise(newB))
                mark("activity", "used " + newB.getName().getString().toLowerCase(), pos, 0xFF8FD3FF);
            return;
        }
        boolean oldNoise = caveNoise(oldB), newNoise = caveNoise(newB);
        // anti-xray reveal: Paper swaps disguised blocks for their REAL
        // state when we get close — ore→stone = a decoy just got unmasked,
        // which only happens where the server is actively hiding something.
        // A natural ore never turns back into plain stone on its own.
        if (isOre(oldB) && !isOre(newB) && !newNoise && newB != Blocks.FIRE) {
            mark("xray", "REVEAL: " + oldB.getName().getString().toLowerCase()
                + " → " + newB.getName().getString().toLowerCase(), pos, 0xFFFF3355);
            flagAt.put(chunkKey(pos.getX(), pos.getZ()), System.currentTimeMillis());
            return;
        }
        // storage disappearing = looter / base being emptied
        if (storageGone.on() && storageBlock(oldB) && !storageBlock(newB)) {
            mark("storage", "STORAGE GONE", pos, 0xFFFF6666);
            flagAt.put(chunkKey(pos.getX(), pos.getZ()), System.currentTimeMillis());
            return;
        }
        // player-signature block placed
        if (placement.on() && oldNoise && (signatureBlock(newB) || signFamily(newB))) {
            mark("place", "placed " + newB.getName().getString().toLowerCase(), pos, 0xFF7AE2A8);
            flagAt.put(chunkKey(pos.getX(), pos.getZ()), System.currentTimeMillis());
            return;
        }
        // dig burst: natural solid → air, clustered per chunk per minute
        if (digging.on() && newB == Blocks.AIR && !oldNoise
            && (solidNatural(oldB) || signatureBlock(oldB))) {
            long key = chunkKey(pos.getX(), pos.getZ());
            long now = System.currentTimeMillis();
            long[] w = digWindow.compute(key, (k, v) ->
                v == null || now - v[0] > 60_000 ? new long[]{now, 1}
                    : new long[]{v[0], v[1] + 1});
            if (w[1] >= 4) {
                // stable label — count lives in Mark.count via bump(), else
                // every dig past 4 makes a NEW mark + toast each block
                mark("dig", "digging", pos, 0xFFFFB84D);
                flagAt.put(key, System.currentTimeMillis());
            }
        }
    }

    public static void onExplosion(ClientboundExplodePacket p) {
        if (inst == null || !inst.isEnabled() || !inst.explosions.on()) return;
        Vec3 c = p.center();
        inst.mark("blast", "explosion", c.x, c.y, c.z, 0xFFFF5555);
        flagAt.put(chunkKey((int) c.x, (int) c.z), System.currentTimeMillis());
    }

    public static void onBlockEntityData(ClientboundBlockEntityDataPacket p) {
        if (inst == null || !inst.isEnabled() || !inst.signs.on()) return;
        // sign text keys in the payload = someone is writing a sign right now
        var tag = p.getTag();
        if (tag == null) return;
        if (!tag.contains("front_text") && !tag.contains("back_text")
            && !tag.contains("Text1") && !tag.contains("messages")) return;
        inst.mark("sign", "sign written", p.getPos(), 0xFFD08FFF);
        flagAt.put(chunkKey(p.getPos().getX(), p.getPos().getZ()), System.currentTimeMillis());
    }

    public static void onTakeItem(ClientboundTakeItemEntityPacket p) {
        if (inst == null || !inst.isEnabled() || !inst.pickups.on() || inst.mc.level == null) return;
        Entity item = inst.mc.level.getEntity(p.getItemId());
        Entity taker = inst.mc.level.getEntity(p.getPlayerId());
        if (item == null || taker == inst.mc.player) return;
        inst.mark("pickup", "item picked up", item.getX(), item.getY(), item.getZ(), 0xFFAAAAFF);
    }

    public static void onDamage(ClientboundDamageEventPacket p) {
        if (inst == null || !inst.isEnabled() || !inst.combat.on() || inst.mc.level == null) return;
        Entity e = inst.mc.level.getEntity(p.entityId());
        if (e == null || e == inst.mc.player) return;
        String who = e instanceof Player pl ? "PLAYER HIT " + pl.getName().getString() : "combat";
        inst.mark("combat", who, e.getX(), e.getY(), e.getZ(),
            e instanceof Player ? 0xFFFF5555 : 0xFFDD9955);
        if (e instanceof Player) flagAt.put(chunkKey((int) e.getX(), (int) e.getZ()), System.currentTimeMillis());
    }

    // ---------- marking ----------

    private void mark(String kind, String label, BlockPos pos, int color) {
        mark(kind, label, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, color);
    }

    /** Kinds worth a persistent stash-index entry — real base evidence
     *  only. dig/activity/pickup marks are transient noise. */
    private static final Set<String> STASHABLE = Set.of(
        "xray", "storage", "place", "sign", "blast");

    /** Per-kind toast gap (ms). -1 = world mark only, never a toast —
     *  dig/activity/pickup used to push a card per new chunk because the
     *  distance in the text defeated toast dedup. Now: only real hits
     *  toast, and each kind has a cooldown. */
    private static long toastGap(String kind) {
        return switch (kind) {
            case "xray" -> 2_000;
            case "storage", "blast", "combat" -> 4_000;
            case "place", "sign" -> 8_000;
            case "dig" -> 15_000;
            default -> -1;
        };
    }

    private void mark(String kind, String label, double x, double y, double z, int color) {
        if (mc.player == null) return;
        double dx = x - mc.player.getX(), dy = y - mc.player.getY(), dz = z - mc.player.getZ();
        double d2 = dx * dx + dy * dy + dz * dz;
        if (d2 < ignoreNear.get() * ignoreNear.get()) return;
        boolean deep = y < mc.player.getY() - deepDrop.get();
        // dig marks aggregate into 64-block cells — a miner crossing
        // chunk borders used to spawn a fresh mark every 16 blocks;
        // bump() keeps the centroid tracking them instead
        int gx = "dig".equals(kind) ? (int) x >> 6 : (int) x >> 4;
        int gz = "dig".equals(kind) ? (int) z >> 6 : (int) z >> 4;
        String key = kind + ":" + gx + ":" + gz + ":" + label;
        Mark m = MARKS.get(key);
        int col = deep ? 0xFFFFAA00 : color;
        if (m == null) {
            m = new Mark(label, col, x, y, z);
            m.deep = deep;
            MARKS.put(key, m);
            if (MARKS.size() > 256) MARKS.remove(MARKS.keySet().iterator().next());
            long gap = toastGap(kind);
            if (chatNotify.on() && gap >= 0)
                Toasts.pushKeyed("af:" + kind, "◉ " + (deep ? "DEEP " : "") + label
                    + " " + String.format("%.0fm", Math.sqrt(d2)), col, gap);
            mark("found " + label);
            if (STASHABLE.contains(kind))
                dev.noxclient.util.StashIndex.add("activity", label,
                    new BlockPos((int) x, (int) y, (int) z));
        } else {
            m.bump(x, y, z, deep);
            if (m.deep) m.color = 0xFFFFAA00;
        }
    }

    // ---------- render ----------

    /** Marks idle longer than this expire — dig cells used to live forever. */
    private static final long MARK_TTL_MS = 150_000;

    private void emit() {
        if (mc.level == null || mc.player == null || MARKS.isEmpty()) return;
        long now = System.currentTimeMillis();
        MARKS.values().removeIf(m -> now - m.lastMs > MARK_TTL_MS);
        double py = mc.player.getY();
        // density-adaptive: many marks → smaller text, then boxes-only —
        // a fixed size either flooded the map or hid real hits
        int n = MARKS.size();
        float sc = n > 32 ? 0.5f : n > 16 ? 0.65f : 0.85f;
        boolean labels = n <= 48;
        var list = new java.util.ArrayList<>(MARKS.values());
        if (n > 60) {
            var pp = mc.player.position();
            list.sort(java.util.Comparator.comparingDouble(m ->
                (m.x - pp.x) * (m.x - pp.x) + (m.z - pp.z) * (m.z - pp.z)));
            list = new java.util.ArrayList<>(list.subList(0, 60));
        }
        for (Mark m : list) {
            Vec3 c = new Vec3(m.x, m.y, m.z);
            AABB box = new AABB(c, c).inflate(0.4, 0.28, 0.4);
            int col = m.color & 0xFFFFFF;
            Gizmos.cuboid(box, GizmoStyle.strokeAndFill(col | 0xFF000000, 1.5f, col | 0x28000000))
                .setAlwaysOnTop();
            if (m.deep)
                Gizmos.line(c, new Vec3(m.x, Math.min(m.y + 96, py), m.z),
                    col | 0xAA000000, 1.0f).setAlwaysOnTop();
            if (!labels) continue;
            int dy = (int) (py - m.y);
            // cap the counter — "digging x1407" reads like a bug while still
            // signalling an actively excavated area
            String cnt = m.count > 99 ? " x99+" : " x" + m.count;
            String txt = m.label + cnt + (dy > 4 ? "  v" + dy : dy < -4 ? "  ^" + -dy : "");
            Gizmos.billboardText(txt, c.add(0, 1.1, 0),
                TextGizmo.Style.forColorAndCentered(col | 0xFF000000).withScale(sc))
                .setAlwaysOnTop();
        }
    }
}
