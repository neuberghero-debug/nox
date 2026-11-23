package dev.noxclient.module.movement;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.world.phys.Vec3;

/** Pulls you back to the last safe spot when you fall into the void. */
public class AntiVoid extends Module {
    public final Setting.Num minY = add(new Setting.Num("Min Y", -64, -64, 60));
    public final Setting.Mode method = add(new Setting.Mode("Method", "Teleport",
        "Teleport", "Bounce"));
    private Vec3 safe;
    private Object lastLevel;
    private long lastRescue;

    public AntiVoid() { super("AntiVoid", "Rescues you from the void", Category.MOVEMENT); }

    @Override public void onTick() {
        if (mc.player == null) return;
        // stale coords from another dimension/world must not teleport you
        if (mc.level != lastLevel) { lastLevel = mc.level; safe = null; }
        if (mc.player.onGround() && mc.player.getY() > minY.get())
            safe = mc.player.position();
        if (mc.player.getY() < minY.get() && safe != null
            && !dev.noxclient.module.donutsmp.DonutGuard.strict()   // teleports flag instantly on Grim
            && System.currentTimeMillis() - lastRescue > 1000) {
            lastRescue = System.currentTimeMillis();
            if (method.get().equals("Bounce"))
                mc.player.setDeltaMovement(
                    mc.player.getDeltaMovement().x,
                    Math.max(1.2, (safe.y - mc.player.getY()) * 0.15),
                    mc.player.getDeltaMovement().z);
            else
                mc.player.setPos(safe.x, safe.y + 0.5, safe.z);
        }
    }

    @Override protected void onDisable() { safe = null; }
}
