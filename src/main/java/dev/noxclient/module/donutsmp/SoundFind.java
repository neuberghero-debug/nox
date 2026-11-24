package dev.noxclient.module.donutsmp;

import dev.noxclient.gui.Toasts;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.render.NoxRender;
import dev.noxclient.setting.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SoundFind — hears bases you can't see. Verified on a Grim-protected
 * DonutSMP-style server (live test, 2026-11):
 *   BlockEvent packets  → broadcast to every chunk tracker, cylindrical
 *     (depth-blind): piston fired 56 blocks below and marked correctly;
 *     chest/barrel/shulker/furnace opens ride the same channel.
 *   LevelEvent packets  → same broadcast: a chest destroyed 45 blocks down
 *     marked "storage broken" DEEP.
 *   Sound packets       → code path verified (block.chest.open marked),
 *     BUT the server only sends them inside the sound's ~16-32-block
 *     audible range — near-field activity only, not a deep vector.
 *   Entity-sound packets → same server-side distance limit (~16 blocks);
 *     hurt sounds moved to ClientboundDamageEventPacket in modern MC.
 *   Particle packets    → the UltimateDonutSmp stack culls them outright
 *     ("not visible for anybody") — dead vector on that server.
 * Net: block + level events are the real depth-blind bypass; sounds are
 * near-field confirmation. Pure listener — sends nothing, Grim can't see it.
 */
public class SoundFind extends Module {
    private final Setting.Bool blockEvents = add(new Setting.Bool("Storage events", true,
        "Chest/barrel/shulker/furnace/piston events — chunk broadcast, depth-blind (verified)"));
    private final Setting.Bool levelEvents = add(new Setting.Bool("Level events", true,
        "Anvils, brewing, block breaks — chunk broadcast, depth-blind (verified)"));
    private final Setting.Bool sounds = add(new Setting.Bool("Sound packets", true,
        "Positioned sounds — server only sends within audible range (~16-32 blocks)"));
    private final Setting.Bool entitySounds = add(new Setting.Bool("Entity sounds", true,
        "Villagers, golems, players — server-side distance limited, near-field only"));
    private final Setting.Bool particles = add(new Setting.Bool("Particles", false,
        "Enchanting/portals/campfires — culled by the server on DonutSMP-style stacks"));
    private final Setting.Bool deepOnly = add(new Setting.Bool("Deep only", false,
        "Only mark events far below you — filters surface noise"));
    private final Setting.Num deepDrop = add(new Setting.Num("Deep below", 18, 5, 64,
        "How far below you an event must be to count as deep"));
    private final Setting.Num ignoreNear = add(new Setting.Num("Ignore radius", 12, 0, 64,
        "Skip events this close to you (your own noise)"));
    private final Setting.Bool chatNotify = add(new Setting.Bool("Chat notify", true));
    private final Setting.Num ttl = add(new Setting.Num("Marker TTL s", 300, 30, 1800));

    /** kind → display label → color; marks keyed by kind:chunkX:chunkZ. */
    public static final Map<String, Mark> MARKS = new LinkedHashMap<>();
    private static SoundFind inst;
    private NoxRender.Emitter emitter;

    public static final class Mark {
        public double x, y, z;
        public int count;
        public long lastMs;
        public boolean deep;
        public final String label;
        public int color;
        Mark(String label, int color, double x, double y, double z) {
            this.label = label; this.color = color;
            this.x = x; this.y = y; this.z = z; this.count = 1;
            this.lastMs = System.currentTimeMillis();
        }
        void bump(double nx, double ny, double nz, boolean d) {
            // running average pulls the marker toward the real activity center
            x = x + (nx - x) / count; y = y + (ny - y) / count; z = z + (nz - z) / count;
            count++; lastMs = System.currentTimeMillis(); deep |= d;
        }
    }

    public SoundFind() {
        super("SoundFind", "Marks storage/redstone events in tracked chunks — even 60+ blocks below", Category.DONUTSMP);
        inst = this;
    }

    @Override protected void onEnable() {
        MARKS.clear();
        emitter = this::emit;
        NoxRender.add(emitter);
    }
    @Override protected void onDisable() { MARKS.clear(); NoxRender.remove(emitter); }
    @Override public String status() { return MARKS.size() + " marks"; }

    @Override public void onTick() {
        long cut = System.currentTimeMillis() - (long) (ttl.get() * 1000);
        MARKS.values().removeIf(m -> m.lastMs < cut);
    }

    // ---------- classification ----------

    private static String blockEventLabel(Block b) {
        if (b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST) return "chest";
        if (b == Blocks.BARREL) return "barrel";
        if (b == Blocks.SHULKER_BOX) return "shulker";
        if (b == Blocks.ENDER_CHEST) return "ender chest";
        if (b == Blocks.FURNACE || b == Blocks.BLAST_FURNACE || b == Blocks.SMOKER) return "furnace";
        if (b == Blocks.DISPENSER || b == Blocks.DROPPER) return "dispenser";
        if (b == Blocks.PISTON || b == Blocks.STICKY_PISTON) return "piston";
        if (b == Blocks.JUKEBOX) return "jukebox";
        if (b == Blocks.NOTE_BLOCK) return "note block";
        if (b == Blocks.BELL) return "bell";
        return null;
    }

    private static String levelEventLabel(int type) {
        if (type == LevelEvent.SOUND_ANVIL_USED || type == LevelEvent.SOUND_ANVIL_LAND
            || type == LevelEvent.SOUND_ANVIL_BROKEN) return "anvil";
        if (type == LevelEvent.SOUND_BREWING_STAND_BREW) return "brewing";
        if (type == LevelEvent.SOUND_GRINDSTONE_USED) return "grindstone";
        if (type == LevelEvent.SOUND_SMITHING_TABLE_USED) return "smithing";
        if (type == LevelEvent.SOUND_ZOMBIE_WOODEN_DOOR || type == LevelEvent.SOUND_ZOMBIE_IRON_DOOR
            || type == LevelEvent.SOUND_ZOMBIE_DOOR_CRASH) return "door siege";
        if (type == LevelEvent.SOUND_END_PORTAL_SPAWN) return "end portal";
        if (type == LevelEvent.END_PORTAL_FRAME_FILL) return "end portal";
        return null;
    }

    /** Blocks worth reporting when broken (LevelEvent destroy-block data). */
    private static String destroyLabel(BlockState s) {
        Block b = s.getBlock();
        if (b == Blocks.SPAWNER || b == Blocks.TRIAL_SPAWNER || b == Blocks.VAULT) return "spawner/vault";
        if (b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.BARREL
            || b == Blocks.SHULKER_BOX || b == Blocks.ENDER_CHEST) return "storage broken";
        if (b == Blocks.BEACON) return "beacon broken";
        if (b == Blocks.ENCHANTING_TABLE) return "enchant table";
        if (b == Blocks.DECORATED_POT) return "pot";
        return null;
    }

    private static String soundLabel(String path) {
        if (path.contains("chest")) return "chest";
        if (path.contains("barrel")) return "barrel";
        if (path.contains("shulker_box")) return "shulker";
        if (path.contains("anvil")) return "anvil";
        if (path.contains("brewing_stand")) return "brewing";
        if (path.contains("grindstone")) return "grindstone";
        if (path.contains("smithing_table")) return "smithing";
        if (path.contains("bell")) return "bell";
        if (path.contains("jukebox")) return "jukebox";
        if (path.contains("note_block")) return "note block";
        if (path.contains("door")) return "door";
        if (path.contains("trapdoor")) return "trapdoor";
        if (path.contains("fence_gate")) return "gate";
        if (path.contains("lever") || path.contains("button") || path.contains("pressure_plate"))
            return "switch";
        if (path.contains("beacon")) return "beacon";
        if (path.contains("respawn_anchor") || path.contains("lodestone")) return "respawn";
        return null;
    }

    // ---------- entry points (called from SoundSniffMixin, main thread) ----------

    public static void onBlockEvent(ClientboundBlockEventPacket p) {
        if (inst == null || !inst.isEnabled() || !inst.blockEvents.on()) return;
        String label = blockEventLabel(p.getBlock());
        if (label != null) inst.mark("storage", label, p.getPos());
    }

    public static void onLevelEvent(ClientboundLevelEventPacket p) {
        if (inst == null || !inst.isEnabled() || !inst.levelEvents.on()) return;
        String label = levelEventLabel(p.getType());
        if (label == null && p.getType() == LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK)
            label = destroyLabel(Block.stateById(p.getData()));
        if (label != null) inst.mark("level", label, p.getPos());
    }

    public static void onSound(ClientboundSoundPacket p) {
        if (inst == null || !inst.isEnabled() || !inst.sounds.on()) return;
        if (p.getSource() != SoundSource.BLOCKS && p.getSource() != SoundSource.PLAYERS) return;
        String label = soundLabel(p.getSound().value().location().getPath());
        if (label != null) inst.mark("sound", label, p.getX(), p.getY(), p.getZ());
    }

    public static void onEntitySound(ClientboundSoundEntityPacket p) {
        if (inst == null || !inst.isEnabled() || !inst.entitySounds.on() || inst.mc.level == null) return;
        Entity e = inst.mc.level.getEntity(p.getId());
        if (e == null || e == inst.mc.player) return;
        double x = e.getX(), y = e.getY(), z = e.getZ();
        if (e instanceof Player pl) {
            inst.mark("player", "PLAYER " + pl.getName().getString(),
                x, y, z);
        } else if (e instanceof Villager) inst.mark("entity", "villager", x, y, z);
        else if (e instanceof WanderingTrader) inst.mark("entity", "trader", x, y, z);
        else if (e instanceof IronGolem) inst.mark("entity", "iron golem", x, y, z);
        else if (e instanceof Animal)
            inst.mark("entity", "farm", x, y, z);
    }

    public static void onParticles(ClientboundLevelParticlesPacket p) {
        if (inst == null || !inst.isEnabled() || !inst.particles.on()) return;
        var t = p.particle().getType();
        String label =
            t == ParticleTypes.ENCHANT ? "enchanting" :
            t == ParticleTypes.PORTAL ? "portal" :
            t == ParticleTypes.HAPPY_VILLAGER ? "villager" :
            t == ParticleTypes.NOTE ? "note block" :
            t == ParticleTypes.WITCH ? "witch" :
            t == ParticleTypes.ELECTRIC_SPARK ? "redstone" :
            t == ParticleTypes.CAMPFIRE_COSY_SMOKE || t == ParticleTypes.CAMPFIRE_SIGNAL_SMOKE
                ? "campfire" : null;
        if (label != null) inst.mark("particle", label, p.x(), p.y(), p.z());
    }

    // ---------- marking ----------

    private void mark(String kind, String label, BlockPos pos) {
        mark(kind, label, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    private void mark(String kind, String label, double x, double y, double z) {
        if (mc.player == null) return;
        double dx = x - mc.player.getX(), dy = y - mc.player.getY(), dz = z - mc.player.getZ();
        double d2 = dx * dx + dy * dy + dz * dz;
        // ignoreNear = own-noise filter. Player marks can't BE own noise
        // (self already excluded in onEntitySound) — a sneaking player 10m
        // away is exactly what this radar exists for.
        if (!"player".equals(kind) && d2 < ignoreNear.get() * ignoreNear.get()) return;
        boolean deep = y < mc.player.getY() - deepDrop.get();
        if (deepOnly.on() && !deep) return;

        String key = kind + ":" + ((int) x >> 4) + ":" + ((int) z >> 4) + ":" + label;
        Mark m = MARKS.get(key);
        int color = "player".equals(kind) ? 0xFFFF5555
            : deep ? 0xFFFFAA00 : 0xFF5AC8FF;
        if (m == null) {
            m = new Mark(label, color, x, y, z);
            m.deep = deep;
            MARKS.put(key, m);
            if (MARKS.size() > 256) MARKS.remove(MARKS.keySet().iterator().next());
            if (chatNotify.on()) {
                long gap = switch (kind) {
                    case "player" -> 2_000;
                    case "storage", "level" -> 4_000;
                    case "sound" -> 8_000;
                    case "entity" -> 12_000;
                    default -> 15_000;
                };
                Toasts.pushKeyed("sf:" + kind, "◈ " + (deep ? "DEEP " : "") + label
                    + " " + String.format("%.0fm", Math.sqrt(d2)) + (deep ? " ↓" + (int) -dy : ""),
                    deep ? 0xFFFFAA00 : 0xFF9AD5FF, gap);
            }
            mark("found " + label);
        } else {
            m.bump(x, y, z, deep);
            if (m.deep) m.color = 0xFFFFAA00;
        }
    }

    // ---------- render ----------

    private void emit() {
        if (mc.level == null || mc.player == null || MARKS.isEmpty()) return;
        double py = mc.player.getY();
        for (Mark m : MARKS.values()) {
            Vec3 c = new Vec3(m.x, m.y, m.z);
            AABB box = new AABB(c, c).inflate(0.55, 0.4, 0.55);
            int col = m.color & 0xFFFFFF;
            Gizmos.cuboid(box, GizmoStyle.strokeAndFill(col | 0xFF000000, 2f, col | 0x33000000))
                .setAlwaysOnTop();
            // depth beam: shoot a line up toward player level so deep hits are findable
            if (m.deep)
                Gizmos.line(c, new Vec3(m.x, Math.min(m.y + 96, py), m.z),
                    col | 0xAA000000, 1.5f).setAlwaysOnTop();
            int dy = (int) (py - m.y);
            String txt = m.label + " ×" + m.count + (dy > 4 ? "  ↓" + dy : dy < -4 ? "  ↑" + -dy : "");
            Gizmos.billboardText(txt, c.add(0, 1.35, 0),
                TextGizmo.Style.forColorAndCentered(col | 0xFF000000).withScale(1.3f))
                .setAlwaysOnTop();
        }
    }
}
