package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.BaseIntel;
import dev.noxclient.util.BaseSigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;

/**
 * Chunk BE Leak [PREMIUM] — the chunk packet's block-entity list.
 *
 * Every chunk the server sends contains, next to the (possibly anti-xray
 * obfuscated) block states, a separate list of block entities with their
 * exact positions and NBT. Paper's anti-xray operates on block states — the
 * BE list goes out untouched on a LOT of servers. That means chests,
 * spawners, signs (with their TEXT), hoppers, furnaces, command blocks and
 * beehives are announced with exact coordinates even when the block itself
 * is hidden behind fake stone — at ANY height, under Y=0 too.
 *
 * Bonus leaks:
 *  - sign text often literally says whose base it is (or contains coords)
 *  - spawner NBT contains the mob type
 *  - ClientboundBlockEntityDataPacket = live edits (someone writing a sign)
 */
public class ChunkBELeak extends Module {
    private final Setting.Bool chatNotify = add(new Setting.Bool("Chat notify", true));
    private final Setting.Bool signText = add(new Setting.Bool("Leak sign text", true));
    private final Setting.Bool chestsToo = add(new Setting.Bool("Chests/hoppers", true));

    public ChunkBELeak() {
        super("ChunkBELeak", "Reads the chunk packet's block-entity list", Category.DONUTSMP);
        premium = true;
    }

    @Override
    public void onPacket(Packet<?> packet) {
        if (mc.player == null || mc.level == null) return;
        if (packet instanceof ClientboundLevelChunkWithLightPacket p) {
            try {
                p.chunkData().forEachBlockEntityTag(p.x(), p.z(), this::tag);
            } catch (Throwable ignored) {}
        } else if (packet instanceof ClientboundBlockEntityDataPacket p) {
            tag(p.getPos(), p.getType(), p.getTag());
        }
    }

    private void tag(BlockPos pos, BlockEntityType<?> type, CompoundTag nbt) {
        if (type == null || pos.distSqr(mc.player.blockPosition()) < 9) return;
        var key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
        String path = key == null ? "?" : key.getPath();

        BaseSigs.Cat cat; int score; String detail = path;
        if (type == BlockEntityTypes.SIGN || type == BlockEntityTypes.HANGING_SIGN) {
            cat = BaseSigs.Cat.DECOR; score = 5;
            String txt = signText.on() ? extractSignText(nbt) : "";
            if (!txt.isEmpty()) {
                detail = "sign: " + txt;
                if (txt.matches(".*-?\\d{2,}.*")) score = 8; // coords-ish text
            } else detail = "sign";
        } else if (type == BlockEntityTypes.MOB_SPAWNER) {
            cat = BaseSigs.Cat.INFRA; score = 6;
            detail = "spawner" + extractSpawnerMob(nbt);
        } else if (type == BlockEntityTypes.CHEST || type == BlockEntityTypes.TRAPPED_CHEST
                || type == BlockEntityTypes.BARREL || type == BlockEntityTypes.SHULKER_BOX
                || type == BlockEntityTypes.ENDER_CHEST) {
            if (!chestsToo.on()) return;
            cat = BaseSigs.Cat.STORAGE; score = 6;
            detail = customName(nbt, path);
        } else if (type == BlockEntityTypes.HOPPER || type == BlockEntityTypes.DISPENSER
                || type == BlockEntityTypes.DROPPER || type == BlockEntityTypes.CRAFTER) {
            if (!chestsToo.on()) return;
            cat = BaseSigs.Cat.MECH; score = 5;
            detail = customName(nbt, path);
        } else if (type == BlockEntityTypes.FURNACE || type == BlockEntityTypes.BLAST_FURNACE
                || type == BlockEntityTypes.SMOKER || type == BlockEntityTypes.BREWING_STAND
                || type == BlockEntityTypes.CAMPFIRE) {
            cat = BaseSigs.Cat.STATION; score = 5;
            detail = customName(nbt, path);
        } else if (type == BlockEntityTypes.ENCHANTING_TABLE || type == BlockEntityTypes.LECTERN
                || type == BlockEntityTypes.BEACON || type == BlockEntityTypes.CONDUIT
                || type == BlockEntityTypes.DAYLIGHT_DETECTOR || type == BlockEntityTypes.COMPARATOR
                || type == BlockEntityTypes.JUKEBOX || type == BlockEntityTypes.BELL
                || type == BlockEntityTypes.CHISELED_BOOKSHELF || type == BlockEntityTypes.DECORATED_POT
                || type == BlockEntityTypes.END_GATEWAY || type == BlockEntityTypes.SCULK_SENSOR
                || type == BlockEntityTypes.CALIBRATED_SCULK_SENSOR || type == BlockEntityTypes.VAULT
                || type == BlockEntityTypes.TRIAL_SPAWNER || type == BlockEntityTypes.CREAKING_HEART) {
            cat = type == BlockEntityTypes.BEACON || type == BlockEntityTypes.CONDUIT
                ? BaseSigs.Cat.INFRA : type == BlockEntityTypes.JUKEBOX
                || type == BlockEntityTypes.CHISELED_BOOKSHELF || type == BlockEntityTypes.DECORATED_POT
                ? BaseSigs.Cat.STATION
                : type == BlockEntityTypes.VAULT || type == BlockEntityTypes.TRIAL_SPAWNER
                || type == BlockEntityTypes.SCULK_SHRIEKER || type == BlockEntityTypes.CREAKING_HEART
                ? BaseSigs.Cat.ROOM : BaseSigs.Cat.MECH;
            score = 4;
            detail = customName(nbt, path);
        } else if (type == BlockEntityTypes.COMMAND_BLOCK || type == BlockEntityTypes.STRUCTURE_BLOCK
                || type == BlockEntityTypes.JIGSAW || type == BlockEntityTypes.TEST_BLOCK
                || type == BlockEntityTypes.TEST_INSTANCE_BLOCK) {
            cat = BaseSigs.Cat.INFRA; score = 7;
            String cmd = nbt == null ? "" : nbt.getString("Command").orElse("");
            detail = path + (cmd.isEmpty() ? "" : " /" + cmd);
        } else if (type == BlockEntityTypes.BEEHIVE) {
            cat = BaseSigs.Cat.FARM; score = 4;
            detail = "beehive";
        } else if (type == BlockEntityTypes.BANNER || type == BlockEntityTypes.SKULL
                || type == BlockEntityTypes.PISTON || type == BlockEntityTypes.END_PORTAL
                || type == BlockEntityTypes.SHELF) {
            cat = BaseSigs.Cat.DECOR; score = 3;
        } else if (type == BlockEntityTypes.SCULK_SHRIEKER || type == BlockEntityTypes.SCULK_CATALYST
                || type == BlockEntityTypes.BRUSHABLE_BLOCK || type == BlockEntityTypes.COPPER_GOLEM_STATUE
                || type == BlockEntityTypes.POTENT_SULFUR) {
            cat = BaseSigs.Cat.ROOM; score = 3; // deep dark / trial structures — weak
        } else {
            return; // uninteresting BE type
        }

        var h = BaseIntel.hit("be:" + path, pos, cat.color, detail, score);
        mark(detail);
        if (chatNotify.on() && h.count == 1) {
            BaseIntel.notify("be" + pos.toShortString().hashCode(),
                String.format("§d[NOX] §fBE %s §7(%s) §8@ %s", detail, cat.label, pos.toShortString()));
        }
    }

    /* Sign NBT in 1.20+: front_text/back_text compounds containing a list of
       JSON components under "messages". We scrape the raw strings — good
       enough for "x=123 z=-456" style intel. */
    private static String extractSignText(CompoundTag tag) {
        if (tag == null) return "";
        StringBuilder out = new StringBuilder();
        for (String side : new String[]{"front_text", "back_text"}) {
            tag.getCompound(side).ifPresent(t ->
                t.getList("messages").ifPresent(list -> {
                    for (int i = 0; i < list.size(); i++) {
                        String raw = list.getString(i).orElse("");
                        // component json → grab the visible text
                        String clean = raw.replaceAll("\\{\"text\":\"|\"\\}|\\\\\"", "")
                            .replaceAll("[{}\\[\\]\",]|color:\\w+|text:|extra:", " ").trim();
                        if (!clean.isEmpty() && !clean.equals("\"\"") && !clean.equals("null"))
                            out.append(clean).append(' ');
                    }
                }));
        }
        String s = out.toString().trim();
        return s.length() > 60 ? s.substring(0, 60) : s;
    }

    private static String extractSpawnerMob(CompoundTag tag) {
        if (tag == null) return "";
        // SpawnData.entity.id or SpawnPotentials[0].data.entity.id
        var mob = tag.getCompound("SpawnData")
            .flatMap(sd -> sd.getCompound("entity"))
            .flatMap(e -> e.getString("id")).orElse("");
        return mob.isEmpty() ? "" : " (" + mob.replace("minecraft:", "") + ")";
    }

    private static String customName(CompoundTag tag, String fallback) {
        if (tag == null) return fallback;
        var name = tag.getString("CustomName").orElse("");
        if (name.isEmpty()) return fallback;
        String clean = name.replaceAll("[{}\"\\[\\]]|text:|color:\\w+", "").trim();
        return fallback + (clean.isEmpty() ? "" : " \"" + clean + "\"");
    }

    @Override public String status() { return BaseIntel.size() + " intel marks"; }
}
