package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.BaseIntel;
import dev.noxclient.util.BaseSigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Entity Leak Finder — entities are not part of chunk obfuscation. When a
 * chunk loads, its item frames, armor stands, paintings and storage minecarts
 * spawn as entities with exact coordinates — a base hidden behind fake stone
 * still puts its decorations on the wire.
 *
 *  - ClientboundAddEntityPacket: frames, stands, paintings, displays,
 *    leash knots, storage minecarts, XP orbs (farms), primed TNT (raiders),
 *    thrown pearls/potions (players)
 *  - ClientboundTakeItemEntityPacket: someone collecting drops
 *  - ClientboundEntityEventPacket: totem pops etc.
 *  - ClientboundSetPassengersPacket: riders on mounts/vehicles
 */
public class EntityLeakFinder extends Module {
    private final Setting.Bool chatNotify = add(new Setting.Bool("Chat notify", true));
    private final Setting.Num itemStreamMin = add(new Setting.Num("Item stream min", 5, 2, 30));

    private final Map<Long, int[]> itemCells = new HashMap<>();
    private long lastSweep;

    public EntityLeakFinder() {
        super("EntityLeakFinder", "Finds bases through entity spawn packets", Category.DONUTSMP);
    }

    /** ids whose gear we still want to read — SetEntityData arrives at HEAD
        before vanilla applies it, so frame contents are read next tick */
    private final java.util.Set<Integer> pendingGear = new java.util.HashSet<>();
    private final java.util.Set<Integer> gearDone = new java.util.HashSet<>();
    private final java.util.Set<Integer> fightSeen = new java.util.HashSet<>();

    @Override
    public void onPacket(Packet<?> packet) {
        if (mc.player == null || mc.level == null) return;
        if (packet instanceof ClientboundAddEntityPacket p) added(p);
        else if (packet instanceof ClientboundTakeItemEntityPacket p) taken(p);
        else if (packet instanceof ClientboundEntityEventPacket p) entityEvent(p);
        else if (packet instanceof ClientboundSetPassengersPacket p) passengers(p);
        else if (packet instanceof ClientboundSetEntityDataPacket p) {
            // gear/content updates — item frames send their DISPLAYED ITEM,
            // armor stands their equipment: free wealth intel off the wire
            if (gearDone.add(p.id())) pendingGear.add(p.id());
        } else if (packet instanceof ClientboundDamageEventPacket p) damage(p);
    }

    /** Read frame contents / stand equipment one tick after the metadata lands. */
    @Override
    public void onTick() {
        if (mc.level == null || pendingGear.isEmpty()) return;
        var it = pendingGear.iterator();
        while (it.hasNext()) {
            Entity e = mc.level.getEntity(it.next());
            it.remove();
            if (e == null) continue;
            if (e instanceof net.minecraft.world.entity.decoration.ItemFrame f) {
                var item = f.getItem();
                if (!item.isEmpty())
                    emit("frameitem", e.blockPosition(), BaseSigs.Cat.DECOR,
                        "frame holds " + item.getHoverName().getString(), 5);
            } else if (e instanceof net.minecraft.world.entity.decoration.ArmorStand s) {
                for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                    var item = s.getItemBySlot(slot);
                    if (!item.isEmpty()) {
                        emit("standgear", e.blockPosition(), BaseSigs.Cat.DECOR,
                            "stand wears " + item.getHoverName().getString(), 5);
                        break;
                    }
                }
            }
        }
    }

    /** Someone taking damage = a fight happening right there. */
    private void damage(ClientboundDamageEventPacket p) {
        Entity e = mc.level.getEntity(p.entityId());
        if (e == null || e == mc.player || !fightSeen.add(p.entityId())) return;
        String who = e instanceof Player pl ? pl.getName().getString() + " fighting"
            : "mob taking damage (someone fighting)";
        emit("fight", e.blockPosition(), BaseSigs.Cat.PLAYER, who,
            e instanceof Player ? 5 : 3);
    }

    private void added(ClientboundAddEntityPacket p) {
        EntityType<?> t = p.getType();
        BlockPos pos = BlockPos.containing(p.getX(), p.getY(), p.getZ());
        if (pos.distSqr(mc.player.blockPosition()) < 9) return; // your own spawns

        if (t == EntityTypes.ITEM_FRAME) emit("frame", pos, BaseSigs.Cat.DECOR, "item frame", 5);
        else if (t == EntityTypes.GLOW_ITEM_FRAME) emit("frame", pos, BaseSigs.Cat.DECOR, "glow item frame", 5);
        else if (t == EntityTypes.ARMOR_STAND) emit("stand", pos, BaseSigs.Cat.DECOR, "armor stand", 5);
        else if (t == EntityTypes.PAINTING) emit("painting", pos, BaseSigs.Cat.DECOR, "painting", 4);
        else if (t == EntityTypes.LEASH_KNOT) emit("leash", pos, BaseSigs.Cat.DECOR, "leash knot", 4);
        else if (t == EntityTypes.ITEM_DISPLAY) emit("display", pos, BaseSigs.Cat.DECOR, "item display", 5);
        else if (t == EntityTypes.BLOCK_DISPLAY || t == EntityTypes.TEXT_DISPLAY)
            emit("display", pos, BaseSigs.Cat.DECOR, "display entity", 4);
        else if (t == EntityTypes.CHEST_MINECART) emit("minecart", pos, BaseSigs.Cat.STORAGE, "chest minecart", 6);
        else if (t == EntityTypes.HOPPER_MINECART) emit("minecart", pos, BaseSigs.Cat.MECH, "hopper minecart", 6);
        else if (t == EntityTypes.SPAWNER_MINECART) emit("minecart", pos, BaseSigs.Cat.INFRA, "spawner minecart", 6);
        else if (t == EntityTypes.TNT) emit("tnt", pos, BaseSigs.Cat.PLAYER, "primed tnt", 5);
        else if (t == EntityTypes.ENDER_PEARL) emit("pearl", pos, BaseSigs.Cat.TP, "thrown pearl", 4);
        else if (t == EntityTypes.SPLASH_POTION || t == EntityTypes.LINGERING_POTION)
            emit("potion", pos, BaseSigs.Cat.PLAYER, "thrown potion", 3);
        else if (t == EntityTypes.FIREWORK_ROCKET) emit("firework", pos, BaseSigs.Cat.PLAYER, "firework", 3);
        else if (t == EntityTypes.EXPERIENCE_ORB) emit("xp", pos, BaseSigs.Cat.FARM, "xp orb", 3);
        else if (t == EntityTypes.FALLING_BLOCK) { /* natural gravity too */ }
        else if (t == EntityTypes.ITEM) itemStream(pos);
        else {
            var key = BuiltInRegistries.ENTITY_TYPE.getKey(t);
            var cat = key == null ? null : BaseSigs.classify(key.getPath());
            if (cat == BaseSigs.Cat.ANIMAL)
                emit("animal:" + key.getPath(), pos, cat, key.getPath(), 3);
        }
    }

    /** Streams of dropped items in one cell = farm output / death pile. */
    private void itemStream(BlockPos pos) {
        long now = System.currentTimeMillis();
        long key = (((long) pos.getX() >> 4) & 0x7FFFFFFL) << 27 | (((long) pos.getZ() >> 4) & 0x7FFFFFFL);
        int[] st = itemCells.computeIfAbsent(key, k -> new int[]{0, (int) (now / 1000)});
        if (now / 1000 - st[1] > 120) { st[0] = 0; st[1] = (int) (now / 1000); }
        if (++st[0] == itemStreamMin.intVal())
            emit("itemstream", pos, BaseSigs.Cat.FARM, "item stream (farm/drops)", 5);
        if (now - lastSweep > 90_000) {
            lastSweep = now;
            itemCells.values().removeIf(v -> now / 1000 - v[1] > 180);
        }
    }

    private void taken(ClientboundTakeItemEntityPacket p) {
        // someone picked up an item — the collector's position = activity
        Entity collector = mc.level.getEntity(p.getPlayerId());
        if (collector == null || collector == mc.player) return;
        BlockPos pos = collector.blockPosition();
        String who = collector instanceof Player pl ? pl.getName().getString()
            : BuiltInRegistries.ENTITY_TYPE.getKey(collector.getType()).getPath();
        emit("pickup", pos, BaseSigs.Cat.PLAYER, who + " picked up items", 4);
    }

    private void entityEvent(ClientboundEntityEventPacket p) {
        // 35 = totem of undying pop — someone fighting right there
        if (p.getEventId() != 35) return;
        Entity e = p.getEntity(mc.level);
        if (e == null || e == mc.player) return;
        emit("totem", e.blockPosition(), BaseSigs.Cat.PLAYER,
            e instanceof Player pl ? "totem pop: " + pl.getName().getString() : "totem pop", 4);
    }

    private void passengers(ClientboundSetPassengersPacket p) {
        Entity vehicle = mc.level.getEntity(p.getVehicle());
        if (vehicle == null || vehicle == mc.player) return;
        for (int id : p.getPassengers()) {
            Entity e = mc.level.getEntity(id);
            if (e instanceof Player pl && e != mc.player)
                emit("rider", vehicle.blockPosition(), BaseSigs.Cat.PLAYER,
                    pl.getName().getString() + " riding", 3);
        }
    }

    private void emit(String src, BlockPos pos, BaseSigs.Cat cat, String detail, int score) {
        var h = BaseIntel.hit(src, pos, cat.color, detail, score);
        mark(detail);
        if (chatNotify.on() && h.count == 1) {
            BaseIntel.notify(src + pos.toShortString().hashCode(),
                String.format("§d[NOX] §f%s §7(%s) §8@ %s", detail, cat.label, pos.toShortString()));
        }
    }

    @Override public String status() { return BaseIntel.size() + " intel marks"; }
}
