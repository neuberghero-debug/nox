package dev.noxclient.module.render;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.util.EspMarks;
import dev.noxclient.setting.Setting;
import net.minecraft.core.BlockPos;

/** Leaves a fading trail of markers where you walked — never get lost. */
public class Breadcrumbs extends Module {
    public final Setting.Num maxPoints = add(new Setting.Num("Max points", 200, 50, 1000));
    public final Setting.Num spacing = add(new Setting.Num("Spacing", 3, 1, 10));
    private final java.util.LinkedList<BlockPos> trail = new java.util.LinkedList<>();
    private BlockPos last;

    public Breadcrumbs() { super("Breadcrumbs", "Trail of your path", Category.RENDER); }

    @Override public void onTick() {
        if (mc.player == null || !mc.player.onGround()) return;
        var p = mc.player.blockPosition();
        if (last != null && p.distSqr(last) < spacing.get() * spacing.get()) return;
        last = p.immutable();
        trail.addLast(last);
        EspMarks.MARKS.put(last, 0xFFFFD870);
        while (trail.size() > maxPoints.intVal())
            EspMarks.MARKS.remove(trail.pollFirst());
    }

    @Override public void onDisable() {
        for (var p : trail) EspMarks.MARKS.remove(p);
        trail.clear(); last = null;
    }
}
