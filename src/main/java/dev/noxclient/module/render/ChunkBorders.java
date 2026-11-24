package dev.noxclient.module.render;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.EspMarks;
import net.minecraft.core.BlockPos;

/** Marks the corners of your current chunk — claim/perimeter work. */
public class ChunkBorders extends Module {
    public final Setting.Num chunks = add(new Setting.Num("Chunk radius", 0, 0, 2));
    public final Setting.Num density = add(new Setting.Num("Density", 4, 2, 8));
    private final java.util.List<BlockPos> shown = new java.util.ArrayList<>();

    public ChunkBorders() { super("ChunkBorders", "Marks chunk corners", Category.RENDER); }

    @Override public void onTick() {
        if (mc.player == null) return;
        for (var p : shown) EspMarks.MARKS.remove(p);
        shown.clear();
        int cx = mc.player.getBlockX() >> 4, cz = mc.player.getBlockZ() >> 4;
        int y = mc.player.getBlockY() - 1, R = chunks.intVal(), step = density.intVal();
        for (int ox = -R; ox <= R; ox++) for (int oz = -R; oz <= R; oz++) {
            int bx = cx + ox, bz = cz + oz;
            for (int i = 0; i < 16; i += step) {
                for (int[] c : new int[][]{{0, i}, {15, i}, {i, 0}, {i, 15}}) {
                    var p = new BlockPos((bx << 4) + c[0], y, (bz << 4) + c[1]);
                    shown.add(p);
                    EspMarks.put(p, ox == 0 && oz == 0 ? 0xFF40C8FF : 0x8040C8FF);
                }
            }
        }
    }

    @Override public void onDisable() {
        for (var p : shown) EspMarks.MARKS.remove(p);
        shown.clear();
    }
}
