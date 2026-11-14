package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.gui.Toasts;
import net.minecraft.world.entity.monster.PatrollingMonster;

/** Toasts when raiders spawn near your base. */
public class RaidAlarm extends Module {
    public final Setting.Num range = add(new Setting.Num("Range", 48, 16, 128));
    private final java.util.Set<java.util.UUID> seen = new java.util.HashSet<>();

    public RaidAlarm() { super("RaidAlarm", "Alerts on raiders nearby", Category.DONUTSMP); }

    @Override public void onTick() {
        if (mc.player == null || mc.level == null) return;
        double r2 = range.get() * range.get();
        for (var e : mc.level.entitiesForRendering()) {
            String t = e.getType().toString();
            boolean raider = t.contains("pillager") || t.contains("vindicator")
                || t.contains("evoker") || t.contains("ravager") || t.contains("witch");
            if (!raider || e.distanceToSqr(mc.player) > r2 || !seen.add(e.getUUID())) continue;
            Toasts.push("Raider: " + e.getName().getString() + " "
                + (int) Math.sqrt(e.distanceToSqr(mc.player)) + "m", 0xFFFF5555);
        }
    }
}
