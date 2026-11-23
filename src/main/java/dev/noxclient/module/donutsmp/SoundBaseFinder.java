package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.BaseIntel;
import dev.noxclient.util.BaseSigs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.level.block.LevelEvent;

/**
 * Sound Base Finder [PREMIUM] — the DonutSMP sound bypass.
 *
 * The server can't strip audio without breaking the game for legit players,
 * so every sound packet is a free coordinate leak:
 *
 *  - named sounds (ClientboundSoundPacket): chest/barrel/shulker opens,
 *    doors, pistons, redstone, stations, teleports, pearls, portals — each
 *    carries the exact block position it played at
 *  - entity sounds (ClientboundSoundEntityPacket): item frames, armor
 *    stands, storage minecarts, paintings — decoration sounds = base coords
 *  - level events (ClientboundLevelEventPacket): brewing, anvils, grindstone,
 *    smithing, jukebox, wither spawn — plus GLOBAL broadcasts sent to every
 *    client on the server
 *  - block events (ClientboundBlockEventPacket): chest lids, pistons,
 *    noteblocks, bells — fire even for blocks the anti-xray hides
 *
 * Own actions are filtered by distance so you don't flag your own stash.
 */
public class SoundBaseFinder extends Module {
    private final Setting.Bool chatNotify = add(new Setting.Bool("Chat notify", true));
    private final Setting.Num selfRadius = add(new Setting.Num("Own-action radius", 4, 0, 16));
    private final Setting.Bool farOnly = add(new Setting.Bool("Only beyond 16m", false));
    private final Setting.Bool doorsToo = add(new Setting.Bool("Track doors", true));

    public SoundBaseFinder() {
        super("SoundBaseFinder", "Finds bases through sound packets", Category.DONUTSMP);
        premium = true;
    }

    @Override
    public void onPacket(Packet<?> packet) {
        if (mc.player == null || mc.level == null) return;
        if (packet instanceof ClientboundSoundPacket p) namedSound(p);
        else if (packet instanceof ClientboundSoundEntityPacket p) entitySound(p);
        else if (packet instanceof ClientboundLevelEventPacket p) levelEvent(p);
        else if (packet instanceof ClientboundBlockEventPacket p) blockEvent(p);
    }

    /* ---------- named sounds ---------- */

    private void namedSound(ClientboundSoundPacket p) {
        var key = p.getSound().unwrapKey().orElse(null);
        String path = key == null ? null : key.identifier().getPath();
        if (path == null || BaseSigs.boringSound(path)) return;

        BlockPos pos = BlockPos.containing(p.getX(), p.getY(), p.getZ());
        if (nearSelf(pos)) return;
        if (farOnly.on() && mc.player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 256) return;

        var cat = BaseSigs.classify(path);
        if (cat == null) {
            // unclassified but player-only sounds still matter
            if (path.startsWith("entity.player.") || path.startsWith("item.")) {
                emit("sound", pos, BaseSigs.Cat.PLAYER, path, 2);
            }
            return;
        }
        if (cat == BaseSigs.Cat.DOOR && !doorsToo.on()) return;
        int score = switch (cat) {
            case STORAGE -> 6;
            case TP -> 5;
            case STATION -> 5;
            case MECH -> 4;
            case INFRA -> 4;
            case DECOR -> 4;
            case PLAYER -> 3;
            case FARM -> 3;
            case TOOLS -> 3;
            case ROOM -> 2;
            case ANIMAL -> 2;
            case DOOR -> 2;
            case LIGHT -> 1;
            case ACTIVITY -> 2;
        };
        if (BaseSigs.naturallyCommon(path) && cat != BaseSigs.Cat.PLAYER) score = Math.max(1, score - 2);
        emit("sound:" + path.split("\\.")[0] + "." + shortName(path), pos, cat, pretty(path), score);
    }

    /* ---------- entity sounds ---------- */

    private void entitySound(ClientboundSoundEntityPacket p) {
        var e = mc.level.getEntity(p.getId());
        if (e == null || e == mc.player) return;
        var key = p.getSound().unwrapKey().orElse(null);
        String path = key == null ? "" : key.identifier().getPath();
        var cat = BaseSigs.classify(path);
        // the entity type itself is the stronger signal — frames/stands/minecarts
        // don't exist in nature
        var typeCat = entityCat(e);
        BlockPos pos = e.blockPosition();
        if (nearSelf(pos)) return;
        if (typeCat != null) {
            var tk = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
            emit("esound:" + tk, pos, typeCat, tk.getPath(), 4);
        } else if (cat == BaseSigs.Cat.PLAYER && e instanceof net.minecraft.world.entity.player.Player) {
            emit("esound:player", pos, BaseSigs.Cat.PLAYER,
                e.getName().getString() + " " + shortName(path), 3);
        } else if (cat != null && cat != BaseSigs.Cat.ACTIVITY) {
            emit("esound", pos, cat, shortName(path), 2);
        }
    }

    /** Item frames, armor stands, paintings, leash knots, storage minecarts. */
    private static BaseSigs.Cat entityCat(net.minecraft.world.entity.Entity e) {
        var t = e.getType();
        if (t == net.minecraft.world.entity.EntityTypes.ITEM_FRAME
            || t == net.minecraft.world.entity.EntityTypes.GLOW_ITEM_FRAME
            || t == net.minecraft.world.entity.EntityTypes.ARMOR_STAND
            || t == net.minecraft.world.entity.EntityTypes.PAINTING
            || t == net.minecraft.world.entity.EntityTypes.LEASH_KNOT
            || t == net.minecraft.world.entity.EntityTypes.ITEM_DISPLAY
            || t == net.minecraft.world.entity.EntityTypes.BLOCK_DISPLAY
            || t == net.minecraft.world.entity.EntityTypes.TEXT_DISPLAY) {
            return BaseSigs.Cat.DECOR;
        }
        if (t == net.minecraft.world.entity.EntityTypes.CHEST_MINECART
            || t == net.minecraft.world.entity.EntityTypes.HOPPER_MINECART) {
            return BaseSigs.Cat.STORAGE;
        }
        return null;
    }

    /* ---------- level events ---------- */

    private void levelEvent(ClientboundLevelEventPacket p) {
        int t = p.getType();
        BlockPos pos = p.getPos();
        // GLOBAL broadcasts — every client on the server gets these with a
        // proxy position. EventTriangulator turns them into a region.
        if (p.isGlobalEvent()) return; // triangulator handles
        String label = switch (t) {
            case LevelEvent.SOUND_PLAY_JUKEBOX_SONG, LevelEvent.SOUND_STOP_JUKEBOX_SONG -> "jukebox";
            case LevelEvent.SOUND_BREWING_STAND_BREW -> "brewing stand";
            case LevelEvent.SOUND_ANVIL_USED, LevelEvent.SOUND_ANVIL_BROKEN,
                 LevelEvent.SOUND_ANVIL_LAND -> "anvil";
            case LevelEvent.SOUND_GRINDSTONE_USED -> "grindstone";
            case LevelEvent.SOUND_SMITHING_TABLE_USED -> "smithing table";
            case LevelEvent.SOUND_PORTAL_TRAVEL -> "portal travel";
            case LevelEvent.SOUND_END_PORTAL_SPAWN -> "end portal spawn";
            case LevelEvent.SOUND_WITHER_BOSS_SPAWN -> "wither spawn";
            case LevelEvent.SOUND_WITHER_BOSS_SHOOT, LevelEvent.SOUND_WITHER_BLOCK_BREAK -> "wither nearby";
            case LevelEvent.SOUND_DRAGON_DEATH -> "dragon death";
            case LevelEvent.SOUND_CRAFTER_CRAFT, LevelEvent.SOUND_CRAFTER_FAIL -> "crafter";
            case LevelEvent.SOUND_FIREWORK_SHOOT -> "firework launch";
            case LevelEvent.SOUND_DISPENSER_DISPENSE, LevelEvent.SOUND_DISPENSER_FAIL,
                 LevelEvent.SOUND_DISPENSER_PROJECTILE_LAUNCH -> "dispenser";
            case LevelEvent.SOUND_EXTINGUISH_FIRE -> "fire extinguished";
            case LevelEvent.SOUND_WIND_CHARGE_SHOOT -> "wind charge";
            case LevelEvent.END_PORTAL_FRAME_FILL -> "portal frame filled!";
            case LevelEvent.REDSTONE_TORCH_BURNOUT -> "redstone burnout";
            case LevelEvent.COMPOSTER_FILL -> "composter";
            case LevelEvent.SOUND_CHORUS_GROW, LevelEvent.SOUND_CHORUS_DEATH -> "chorus farm";
            case LevelEvent.SOUND_PAGE_TURN -> "book page";
            case LevelEvent.SOUND_GHAST_WARNING, LevelEvent.SOUND_GHAST_FIREBALL -> "ghast fight";
            case LevelEvent.SOUND_ZOMBIE_WOODEN_DOOR, LevelEvent.SOUND_ZOMBIE_IRON_DOOR,
                 LevelEvent.SOUND_ZOMBIE_DOOR_CRASH -> "zombie raid";
            case LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH -> "bonemeal use";
            case LevelEvent.PARTICLES_EYE_OF_ENDER_DEATH -> "ender eye used";
            case LevelEvent.PARTICLES_MOBBLOCK_SPAWN -> "block mob spawn";
            case LevelEvent.LAVA_FIZZ -> "lava fizz";
            default -> null;
        };
        if (label == null || nearSelf(pos)) return;
        var cat = label.contains("portal") || label.contains("wither") || label.contains("dragon")
            ? BaseSigs.Cat.TP : label.contains("anvil") || label.contains("smithing")
            || label.contains("grindstone") || label.contains("brewing") || label.contains("jukebox")
            ? BaseSigs.Cat.STATION
            : label.contains("dispenser") || label.contains("crafter") || label.contains("redstone")
            ? BaseSigs.Cat.MECH : BaseSigs.Cat.ACTIVITY;
        emit("levent:" + t, pos, cat, label, 4);
    }

    /* ---------- block events (chest lids, pistons, noteblocks…) ---------- */

    private void blockEvent(ClientboundBlockEventPacket p) {
        BlockPos pos = p.getPos();
        if (nearSelf(pos)) return;
        var b = p.getBlock();
        var cat = BaseSigs.cat(b);
        if (cat == null) return;
        // block events fire on interaction — the block doesn't even need to be
        // visible in the chunk data for this to leak its position
        var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b);
        emit("bevent:" + (key == null ? "?" : key.getPath()), pos, cat,
            (key == null ? b.toString() : key.getPath()) + " action", 5);
    }

    /* ---------- helpers ---------- */

    private boolean nearSelf(BlockPos pos) {
        double r = selfRadius.get();
        return mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < r * r;
    }

    private void emit(String src, BlockPos pos, BaseSigs.Cat cat, String detail, int score) {
        var h = BaseIntel.hit(src, pos, cat.color, detail, score);
        mark("heard " + detail);
        if (chatNotify.on() && h.count == 1) {
            BaseIntel.notify(src + pos.toShortString().hashCode(),
                String.format("§d[NOX] §f%s §7(%s) §8@ %s", detail, cat.label, pos.toShortString()));
        }
    }

    private static String shortName(String path) {
        int i = path.lastIndexOf('.');
        return i < 0 ? path : path.substring(i + 1);
    }

    private static String pretty(String path) {
        return path.replaceFirst("^(block|entity|item)\\.", "").replace('.', ' ');
    }

    @Override public String status() { return BaseIntel.size() + " intel marks"; }
}
