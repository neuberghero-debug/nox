package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.util.EspMarks;
import dev.noxclient.setting.Setting;
import net.minecraft.core.BlockPos;

/** Shows entity hitboxes as block-corner markers (F3+B without debug screen). */
public class Hitboxes extends Module {
    public final Setting.Num range = add(new Setting.Num("Range", 24, 8, 64));
    private final java.util.List<BlockPos> shown = new java.util.ArrayList<>();

    public Hitboxes() { super("Hitboxes", "Entity hitbox markers", Category.MISC); }

    @Override public void onTick() {
        if (mc.player == null || mc.level == null) return;
        for (var p : shown) EspMarks.MARKS.remove(p);
        shown.clear();
        double r2 = range.get() * range.get();
        for (var e : mc.level.entitiesForRendering()) {
            if (e == mc.player || e.distanceToSqr(mc.player) > r2) continue;
            var bb = e.getBoundingBox();
            BlockPos p = BlockPos.containing(bb.minX - 0.5, bb.minY, bb.minZ - 0.5);
            shown.add(p); EspMarks.MARKS.put(p, 0xFFFFFFFF);
            BlockPos p2 = BlockPos.containing(bb.maxX - 0.5, bb.maxY - 1, bb.maxZ - 0.5);
            shown.add(p2); EspMarks.MARKS.put(p2, 0xFFFFFFFF);
        }
    }

    @Override public void onDisable() {
        for (var p : shown) EspMarks.MARKS.remove(p);
        shown.clear();
    }
}
