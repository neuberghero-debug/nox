package dev.noxclient.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared "player-activity signature" classifier for all finders.
 * Turns registry ids (blocks, sound paths, entity types) into a small set of
 * categories with a color and a rough score. Everything that only exists
 * because a player put it there (or that only happens when a player acts)
 * is a signature.
 */
public final class BaseSigs {
    public enum Cat {
        STORAGE(0xFFFF5050, "storage"),
        STATION(0xFF50E0FF, "station"),
        MECH(0xFFFFAA3C, "redstone"),
        DOOR(0xFFFFC870, "door"),
        TP(0xFFB44DFF, "teleport"),
        INFRA(0xFF50FF8C, "infrastructure"),
        DECOR(0xFFAAAAFF, "decoration"),
        PLAYER(0xFFFF66CC, "player"),
        FARM(0xFF7BE070, "farm"),
        TOOLS(0xFFC8A060, "tools"),
        ANIMAL(0xFFFFD080, "animals"),
        LIGHT(0xFFFFFF70, "light"),
        ROOM(0xFF70D0C0, "room"),
        ACTIVITY(0xFF9CC8FF, "activity");

        public final int color; public final String label;
        Cat(int color, String label) { this.color = color; this.label = label; }
    }

    /** Path substrings per category — registry-path contains-match. */
    private static final String[][] TABLE = {
        {"chest", "barrel", "shulker_box", "ender_chest", "trapped_chest", "chiseled_bookshelf", "decorated_pot"},
        {"anvil", "enchant", "brewing", "grindstone", "smithing", "stonecutter", "loom", "cartography", "fletching", "lectern", "furnace", "blastfurnace", "smoker", "campfire", "soul_campfire"},
        {"piston", "lever", "button", "pressure_plate", "note_block", "tripwire", "comparator", "repeater", "dispenser", "dropper", "hopper", "crafter", "daylight_detector", "redstone_torch", "redstone_wire", "redstone_lamp", "observer", "target", "sculk_sensor", "sculk_shrieker", "calibrated_sculk", "copper_bulb", "tnt"},
        {"_door", "trapdoor", "fence_gate"},
        {"ender_pearl", "chorus_fruit", "enderman.teleport", "respawn_anchor", "lodestone", "portal", "end_portal", "end_gateway"},
        {"beacon", "conduit", "jukebox", "spawner", "vault", "bell", "creaking_heart"},
        {"item_frame", "glow_item_frame", "armor_stand", "painting", "leash", "minecart", "boat", "sign", "hanging_sign", "banner", "skull", "head", "flower_pot", "candle", "lantern", "chain", "glow_lichen"},
        {"player.burp", "player.hurt", "player.death", "generic.eat", "generic.drink", "experience_orb", "item.pickup", "elytra", "totem", "bow", "crossbow", "trident", "shield", "mace", "tnt.primed", "generic.explode", "wither", "lightning_bolt", "firework", "ender_eye", "end_rod", "snowball", "egg", "potion", "fishing_bobber", "bobber"},
        {"beehive", "composter", "crop", "nether_wart", "sweet_berry", "bone_meal", "villager.work", "villager.yes", "villager.no", "mooshroom", "shear", "bucket", "kelp", "pumpkin", "melon", "sugar_cane", "bamboo", "cactus", "farmland", "hay_block", "turtle_egg", "froglight", "chorus"},
        {"axe.strip", "axe.scrape", "axe.wax", "shovel.flatten", "hoe.till", "shears", "brush", "flintandsteel", "firecharge", "mace.smash"},
        {"donkey", "llama", "horse", "mule", "cat", "wolf", "parrot", "camel", "sniffer", "axolotl", "bee", "saddle", "armor_stand.place"},
    };

    private static final Cat[] ORDER = Cat.values();

    /** Classify a registry path ("chest", "entity.player.burp", "block.anvil.land"). */
    public static Cat classify(String path) {
        for (int i = 0; i < TABLE.length; i++) {
            for (String needle : TABLE[i]) if (path.contains(needle)) return ORDER[i];
        }
        return null;
    }

    /** Classify a block by its registry path; falls back to direct instance checks. */
    public static Cat cat(Block b) {
        if (b == null) return null;
        if (b instanceof net.minecraft.world.level.block.ShulkerBoxBlock) return Cat.STORAGE;
        if (b instanceof net.minecraft.world.level.block.DoorBlock) return Cat.DOOR;
        var key = BuiltInRegistries.BLOCK.getKey(b);
        return key == null ? null : classify(key.getPath());
    }

    public static Cat cat(BlockState s) { return s == null ? null : cat(s.getBlock()); }

    /** Things that grow/exist naturally — used to soften scores, never to hide hits. */
    public static boolean naturallyCommon(String path) {
        return path.contains("villager") || path.contains("door") || path.contains("bell")
            || path.contains("bee") || path.contains("spawner") || path.contains("vault")
            || path.contains("decorated_pot") || path.contains("campfire") || path.contains("hay")
            || path.contains("crop") || path.contains("kelp") || path.contains("sugar_cane")
            || path.contains("cactus") || path.contains("bamboo") || path.contains("pumpkin");
    }

    /** Sounds that are useless for intel (own UI, ambient loops, music, weather). */
    public static boolean boringSound(String path) {
        return path.startsWith("ui.") || path.startsWith("music.") || path.startsWith("ambient.")
            || path.startsWith("weather.") || path.startsWith("event.") || path.startsWith("intentionally_empty")
            || path.contains(".ambient") || path.startsWith("block.lava") || path.startsWith("block.water")
            || path.startsWith("block.bubble") || path.startsWith("entity.bat") || path.startsWith("entity.squid")
            || path.startsWith("entity.glow_squid") || path.startsWith("entity.tadpole") || path.startsWith("entity.tropical_fish")
            || path.startsWith("entity.cod") || path.startsWith("entity.salmon") || path.startsWith("entity.puffer_fish.death")
            || path.startsWith("block.fire.ambient") || path.startsWith("block.beacon.ambient")
            || path.startsWith("entity.vex") || path.startsWith("entity.phantom.ambient");
    }

    private BaseSigs() {}
}
