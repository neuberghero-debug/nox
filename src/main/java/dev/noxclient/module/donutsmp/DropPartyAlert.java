package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.gui.Toasts;
import net.minecraft.world.entity.item.ItemEntity;

/** Toasts when a pile of drops appears nearby — drop party / stash dump. */
public class DropPartyAlert extends Module {
    public final Setting.Num range = add(new Setting.Num("Range", 24, 8, 64));
    public final Setting.Num threshold = add(new Setting.Num("Min items", 8, 3, 40));
    private long cooldown;

    public DropPartyAlert() { super("DropPartyAlert", "Alerts on mass drops nearby", Category.DONUTSMP); }

    @Override public void onTick() {
        if (mc.player == null || mc.level == null) return;
        if (System.currentTimeMillis() - cooldown < 10000) return;
        double r2 = range.get() * range.get();
        int n = 0;
        for (var e : mc.level.entitiesForRendering())
            if (e instanceof ItemEntity && e.distanceToSqr(mc.player) < r2) n++;
        if (n >= threshold.intVal()) {
            Toasts.push("Drop party! " + n + " items nearby", 0xFFFFD870);
            cooldown = System.currentTimeMillis();
        }
    }
}
