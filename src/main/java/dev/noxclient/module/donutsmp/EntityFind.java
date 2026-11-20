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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EntityFind — entity radar for things that cannot exist without a player.
 *   custom names        — name tags are a player-crafted item; a named mob
 *                         anywhere is proof someone was here
 *   armor stands        — craftable only, never natural
 *   item frames/paintings — placed by hand
 *   leash knots         — someone tied an animal
 *   storage minecarts   — chest/hopper/TNT carts = farms & rails
 *   tamed/saddled       — tamed wolves, cats, parrots, saddled mounts
 *   villagers/golems    — flag only below surface or clustered (villages are
 *                         natural above ground)
 * Scans loaded entities every few ticks — entity tracking range is limited,
 * but what it catches is unambiguous.
 */
public class EntityFind extends Module {
    private final Setting.Bool named = add(new Setting.Bool("Named mobs", true,
        "Custom names = name tags = 100% player-made"));
    private final Setting.Bool decor = add(new Setting.Bool("Decor", true,
        "Armor stands, item frames, paintings, leash knots"));
    private final Setting.Bool carts = add(new Setting.Bool("Minecarts", true,
        "Chest/hopper/TNT carts — farms and rail systems"));
    private final Setting.Bool tamed = add(new Setting.Bool("Tamed/saddled", true));
    private final Setting.Bool npcs = add(new Setting.Bool("Villagers/golems", true,
        "Only flagged below surface level or in clusters — skips natural villages"));
    private final Setting.Num villagerY = add(new Setting.Num("NPC below Y", 55, -64, 320));
    private final Setting.Num scanTicks = add(new Setting.Num("Scan every (t)", 20, 5, 100));
    private final Setting.Num ignoreNear = add(new Setting.Num("Ignore radius", 8, 0, 64));
    private final Setting.Bool chatNotify = add(new Setting.Bool("Chat notify", true));

    public static final Map<String, Mark> MARKS = new LinkedHashMap<>();
    private static final Set<Long> flagged = ConcurrentHashMap.newKeySet();
    private final Set<Integer> seen = ConcurrentHashMap.newKeySet();
    private final Map<Long, Integer> villagerCount = new ConcurrentHashMap<>();
    private NoxRender.Emitter emitter;
    private int tick;

    public static final class Mark {
        public double x, y, z;
        public int count, color;
        public long lastMs;
        public final String label;
        Mark(String label, int color, double x, double y, double z) {
            this.label = label; this.color = color;
            this.x = x; this.y = y; this.z = z;
            this.count = 1; this.lastMs = System.currentTimeMillis();
        }
    }

    public EntityFind() {
        super("EntityFind", "Entity radar — named mobs, stands, frames, tamed, cart farms",
            Category.DONUTSMP);
    }

    @Override protected void onEnable() {
        MARKS.clear(); flagged.clear(); seen.clear(); villagerCount.clear(); tick = 0;
        emitter = this::emit;
        NoxRender.add(emitter);
    }
    @Override protected void onDisable() {
        MARKS.clear(); flagged.clear(); seen.clear(); villagerCount.clear();
        NoxRender.remove(emitter);
    }
    @Override public String status() { return MARKS.size() + " marks"; }
    public static Set<Long> flagged() { return flagged; }

    @Override public void onTick() {
        if (mc.level == null || mc.player == null) return;
        if (++tick % scanTicks.intVal() != 0) return;
        villagerCount.clear();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player || e instanceof Player) continue;
            String label = classify(e);
            if (label == null) continue;
            double dx = e.getX() - mc.player.getX(), dy = e.getY() - mc.player.getY(),
                   dz = e.getZ() - mc.player.getZ();
            if (dx*dx + dy*dy + dz*dz < ignoreNear.get() * ignoreNear.get()) continue;
            long key = chunkKey(e.getBlockX(), e.getBlockZ());
            flagged.add(key);
            String mk = label + ":" + (int) e.getX() / 16 + ":" + (int) e.getZ() / 16;
            Mark m = MARKS.get(mk);
            if (m == null) {
                int col = color(e, label);
                MARKS.put(mk, new Mark(label, col, e.getX(), e.getY(), e.getZ()));
                if (MARKS.size() > 256) MARKS.remove(MARKS.keySet().iterator().next());
                if (seen.add(e.getId()) && chatNotify.on())
                    Toasts.push("◆ " + label + " " + (int) Math.sqrt(dx*dx+dy*dy+dz*dz) + "m", col);
                dev.noxclient.util.StashIndex.add("entity", label,
                    new BlockPos((int) e.getX(), (int) e.getY(), (int) e.getZ()));
            } else {
                m.count++; m.lastMs = System.currentTimeMillis();
                m.x = (m.x + e.getX()) / 2; m.y = (m.y + e.getY()) / 2; m.z = (m.z + e.getZ()) / 2;
            }
        }
    }

    private String classify(Entity e) {
        // custom name = name tag = guaranteed player (strongest signal)
        if (named.on() && e.hasCustomName()) return "NAMED " + e.getType().toString();
        if (decor.on()) {
            if (e instanceof ArmorStand) return "armor stand";
            if (e instanceof ItemFrame || e instanceof GlowItemFrame) return "item frame";
            if (e instanceof Painting) return "painting";
            if (e instanceof LeashFenceKnotEntity) return "leash knot";
        }
        if (carts.on()) {
            if (e instanceof MinecartChest) return "chest cart";
            if (e instanceof MinecartHopper) return "hopper cart";
            if (e instanceof MinecartTNT) return "TNT cart";
        }
        if (tamed.on()) {
            if (e instanceof TamableAnimal t && t.isTame()) return "tamed " + e.getType().toString();
            if (e instanceof AbstractHorse h && (h.isTamed() || h.isSaddled())) return "tamed mount";
            if (e instanceof Allay) return "allay";
        }
        if (npcs.on()) {
            boolean deep = e.getY() < villagerY.get();
            if (e instanceof AbstractVillager || e instanceof IronGolem || e instanceof SnowGolem) {
                if (deep) return e instanceof IronGolem ? "golem" : "villager";
                // above ground: only in clusters of 3+ (village farms count, wandering singles don't)
                long key = chunkKey(e.getBlockX(), e.getBlockZ());
                if (villagerCount.merge(key, 1, Integer::sum) >= 3) return "npc cluster";
            }
        }
        return null;
    }

    private int color(Entity e, String label) {
        if (label.startsWith("NAMED")) return 0xFFFF55FF;
        if (label.contains("cart")) return 0xFF7AE2A8;
        if (label.startsWith("tamed") || label.equals("allay")) return 0xFF8FD3FF;
        return 0xFFD0A060;
    }

    private static long chunkKey(int x, int z) {
        return ((long) (x >> 4) << 32) | (z >> 4 & 0xFFFFFFFFL);
    }

    private void emit() {
        if (mc.level == null || mc.player == null || MARKS.isEmpty()) return;
        double py = mc.player.getY();
        for (Mark m : MARKS.values()) {
            Vec3 c = new Vec3(m.x, m.y, m.z);
            AABB box = new AABB(c, c).inflate(0.6, 0.7, 0.6);
            int col = m.color & 0xFFFFFF;
            Gizmos.cuboid(box, GizmoStyle.strokeAndFill(col | 0xFF000000, 2f, col | 0x33000000))
                .setAlwaysOnTop();
            int dy = (int) (py - m.y);
            String txt = m.label + " ×" + m.count + (dy > 4 ? "  ↓" + dy : dy < -4 ? "  ↑" + -dy : "");
            Gizmos.billboardText(txt, c.add(0, 1.6, 0),
                TextGizmo.Style.forColorAndCentered(col | 0xFF000000).withScale(1.3f))
                .setAlwaysOnTop();
        }
    }
}
