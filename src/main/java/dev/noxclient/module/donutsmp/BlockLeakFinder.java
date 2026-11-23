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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;

/**
 * Block Leak Finder — every live block change inside your view distance is
 * sent in the clear. Anti-xray only obfuscates *chunk loads*; it cannot hide
 * what happens afterwards:
 *
 *  - ClientboundBlockUpdatePacket / SectionBlocksUpdatePacket: real block
 *    states of anything placed, broken or changing — storage, redstone,
 *    portals, crops growing, doors toggling
 *  - ClientboundBlockDestructionPacket: another player mining = activity
 *  - ClientboundAddTransientBlockPacket: server-shown transient states
 *  - ClientboundExplodePacket: explosions = raids / wither fights
 *  - ClientboundLevelParticlesPacket: portals, totem pops, bonemeal
 *
 * Works at any Y — a base 120 blocks under your feet still produces live
 * updates you receive while standing at y=60+.
 */
public class BlockLeakFinder extends Module {
    private final Setting.Bool chatNotify = add(new Setting.Bool("Chat notify", true));
    private final Setting.Bool clusters = add(new Setting.Bool("Activity clusters", true));
    private final Setting.Num clusterMin = add(new Setting.Num("Cluster min changes", 6, 2, 40));

    /** section-key → [change count, window start] for the activity heuristic */
    private final Map<Long, int[]> churn = new HashMap<>();
    private long lastChurnSweep;

    public BlockLeakFinder() {
        super("BlockLeakFinder", "Reads live block changes the server sends", Category.DONUTSMP);
    }

    @Override
    public void onPacket(Packet<?> packet) {
        if (mc.player == null || mc.level == null) return;
        if (packet instanceof ClientboundBlockUpdatePacket p) {
            changed(p.getPos(), p.getBlockState());
        } else if (packet instanceof ClientboundSectionBlocksUpdatePacket p) {
            p.runUpdates((pos, state) -> changed(pos, state));
        } else if (packet instanceof ClientboundAddTransientBlockPacket p) {
            changed(p.getPos(), p.getBlockState());
        } else if (packet instanceof ClientboundBlockDestructionPacket p) {
            if (p.getId() != mc.player.getId() && p.getProgress() > 0)
                emit("destruction", p.getPos(), BaseSigs.Cat.ACTIVITY,
                    "someone mining (" + p.getProgress() + ")", 3);
        } else if (packet instanceof ClientboundExplodePacket p) {
            emit("explosion", BlockPos.containing(p.center()), BaseSigs.Cat.PLAYER,
                "explosion r" + (int) p.radius(), 5);
        } else if (packet instanceof ClientboundLevelParticlesPacket p) {
            particles(p);
        }
    }

    /** One changed block: classify + churn-count. */
    private void changed(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        var cat = BaseSigs.cat(state);
        if (cat != null && cat != BaseSigs.Cat.ACTIVITY) {
            var key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            emit("block:" + key.getPath(), pos, cat, key.getPath() + " changed", catScore(cat));
        } else if (clusters.on()) {
            churn(pos);
        }
    }

    private static int catScore(BaseSigs.Cat c) {
        return switch (c) {
            case STORAGE -> 6; case TP -> 5; case STATION -> 5; case INFRA -> 4;
            case MECH -> 4; case DECOR -> 3; case FARM -> 3; case PLAYER -> 3;
            case DOOR -> 2; case TOOLS -> 2; case ANIMAL -> 2; case ROOM -> 2;
            case LIGHT -> 1; case ACTIVITY -> 1;
        };
    }

    /** Repeated block churn in one 32m cell = someone building/farming there. */
    private void churn(BlockPos pos) {
        long now = System.currentTimeMillis();
        long key = cellKey(pos);
        int[] st = churn.computeIfAbsent(key, k -> new int[]{0, (int) (now / 1000)});
        if (now / 1000 - st[1] > 90) { st[0] = 0; st[1] = (int) (now / 1000); }
        if (++st[0] == clusterMin.intVal()) {
            emit("churn", pos, BaseSigs.Cat.ACTIVITY, "block activity cluster", 4);
        }
        if (now - lastChurnSweep > 60_000) {
            lastChurnSweep = now;
            churn.values().removeIf(v -> now / 1000 - v[1] > 120);
        }
    }

    private static long cellKey(BlockPos p) {
        return (((long) p.getX() >> 5) & 0x3FFFFFFL) << 26 | (((long) p.getZ() >> 5) & 0x3FFFFFFL);
    }

    private void particles(ClientboundLevelParticlesPacket p) {
        var t = p.particle().getType();
        var key = BuiltInRegistries.PARTICLE_TYPE.getKey(t);
        if (key == null) return;
        String path = key.getPath();
        // particles that only happen around players / player-built things
        String label = switch (path) {
            case "portal" -> "nether portal";
            case "reverse_portal" -> "portal (reverse)";
            case "totem_of_undying" -> "totem pop!";
            case "happy_villager" -> "bonemeal/cured";
            case "enchant" -> "enchanting";
            case "soul", "soul_fire_flame" -> "soul fire/speed";
            case "campfire_cosy_smoke", "campfire_signal_smoke" -> "campfire";
            case "end_rod" -> "end rod";
            case "sculk_soul" -> "sculk";
            case "spore_blossom_air" -> null;
            case "explosion", "explosion_emitter" -> "explosion";
            case "firework" -> "firework";
            case "composter" -> "composter";
            case "squid_ink" -> null;
            case "trial_spawner_detection", "trial_spawner_detection_ominous" -> "trial spawner";
            case "infested" -> "infestation";
            case "white_smoke" -> null;
            default -> null;
        };
        if (label == null) return;
        BlockPos pos = BlockPos.containing(p.x(), p.y(), p.z());
        if (mc.player.distanceToSqr(p.x(), p.y(), p.z()) < 16) return; // your own
        var cat = switch (path) {
            case "totem_of_undying", "explosion", "explosion_emitter", "firework" -> BaseSigs.Cat.PLAYER;
            case "enchant" -> BaseSigs.Cat.STATION;
            case "campfire_cosy_smoke", "campfire_signal_smoke", "happy_villager", "composter" -> BaseSigs.Cat.FARM;
            default -> BaseSigs.Cat.ACTIVITY;
        };
        emit("particle:" + path, pos, cat, label, 3);
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
