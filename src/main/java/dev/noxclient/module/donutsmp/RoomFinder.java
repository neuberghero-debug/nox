package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.EspMarks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.Set;

/**
 * Room Finder — hidden rooms under your feet, even below Y=0.
 *
 * Anti-xray can replace a chest with fake stone, but it can NOT turn air
 * into stone: a dug-out room stays air, otherwise nobody could stand in
 * it. A natural cave is open on at least one side — a player-dug room is
 * a pocket of air fully enclosed by stone/deepslate. We count "interior
 * cells" (air whose 6 neighbors are all solid, at least 2 of them
 * stone-family) per chunk; enough of them = hidden room.
 *
 * False-positive control: water/lava pockets, surface caves and soil
 * pockets are excluded by the Y cap, the fluid rule and the soil rule.
 */
public class RoomFinder extends Module {
    private final Setting.Num radius = add(new Setting.Num("Radius (chunks)", 8, 2, 32));
    private final Setting.Num maxY = add(new Setting.Num("Max Y", 40, -60, 200));
    private final Setting.Num minCells = add(new Setting.Num("Min room cells", 8, 3, 40));
    private final Setting.Bool chatNotify = add(new Setting.Bool("Chat notify", true));
    private final Set<ChunkPos> done = new HashSet<>();

    public RoomFinder() {
        super("RoomFinder", "Finds sealed underground rooms", Category.DONUTSMP);
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
            scan(cp);
            return; // one chunk-column per tick — full scans froze the game
        }
    }

    private void scan(ChunkPos cp) {
        var chunk = mc.level.getChunk(cp.x(), cp.z());
        int interior = 0;
        BlockPos first = null;
        int minY = mc.level.getMinY();
        int topY = Math.min(maxY.intVal(), mc.level.getMaxY() - 1);
        var sections = chunk.getSections();
        for (int si = 0; si < sections.length; si++) {
            var sec = sections[si];
            if (sec == null || sec.hasOnlyAir()) continue;
            int secY = mc.level.getSectionYFromSectionIndex(si);
            int y0 = secY * 16;
            if (y0 > topY || y0 + 15 < minY) continue;
            var states = sec.getStates();
            var m = new BlockPos.MutableBlockPos();
            for (int ly = 0; ly < 16; ly++) {
                int wy = y0 + ly;
                if (wy < minY || wy > topY) continue;
                for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
                    if (!states.get(lx, ly, lz).isAir()) continue;
                    m.set(cp.getMinBlockX() + lx, wy, cp.getMinBlockZ() + lz);
                    if (isInterior(m)) {
                        if (first == null) first = m.immutable();
                        if (++interior >= minCells.intVal()) {
                            emit(cp, first, interior);
                            return;
                        }
                    }
                }
            }
        }
        if (interior >= minCells.intVal() && first != null) emit(cp, first, interior);
    }

    /** Air cell whose neighbors are all opaque — sealed pocket interior. */
    private boolean isInterior(BlockPos pos) {
        int stoneish = 0;
        for (Direction d : Direction.values()) {
            var s = mc.level.getBlockState(pos.relative(d));
            if (s.isAir() || s.getBlock() == Blocks.WATER || s.getBlock() == Blocks.LAVA
                || s.getBlock() == Blocks.CAVE_AIR || s.getBlock() == Blocks.VOID_AIR) {
                return false; // open to a cavity or fluid — natural
            }
            var b = s.getBlock();
            if (b == Blocks.STONE || b == Blocks.DEEPSLATE || b == Blocks.TUFF
                || b == Blocks.ANDESITE || b == Blocks.DIORITE || b == Blocks.GRANITE
                || b == Blocks.BASALT || b == Blocks.BLACKSTONE || b == Blocks.NETHERRACK
                || b == Blocks.END_STONE || b == Blocks.BEDROCK) {
                stoneish++;
            } else if (b == Blocks.DIRT || b == Blocks.GRASS_BLOCK || b == Blocks.SAND
                || b == Blocks.GRAVEL || b == Blocks.CLAY || s.is(Blocks.OAK_LOG)) {
                return false; // soil pockets form naturally — skip
            }
            // anything else (ores, planks, obsidian, fake-xray blocks) counts solid
        }
        return stoneish >= 2;
    }

    private void emit(ChunkPos cp, BlockPos pos, int cells) {
        EspMarks.put(pos, 0xFFCC66FF, "room ~" + cells);
        if (chatNotify.on() && mc.player != null) {
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§7[NOX] §dsealed room §f(~" + cells + " cells) §8@ " + pos.toShortString()
                + " §7chunk " + cp.x() + "," + cp.z()));
        }
        dev.noxclient.util.StashLogger.log("roomfinder", pos, "cells~" + cells);
        mark("room " + pos.toShortString());
    }

    @Override protected void onDisable() { done.clear(); }
    @Override public String status() { return done.size() + " chunks scanned"; }
}
