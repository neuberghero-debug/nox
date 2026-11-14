package dev.noxclient.module.movement;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.world.phys.Vec3;

/** Pulls you back to the last safe spot when you fall into the void. */
public class AntiVoid extends Module {
    public final Setting.Num minY = add(new Setting.Num("Min Y", 0, -64, 60));
    public final Setting.Mode method = add(new Setting.Mode("Method", "Teleport",
        "Teleport", "Bounce"));
    private Vec3 safe;

    public AntiVoid() { super("AntiVoid", "Rescues you from the void", Category.MOVEMENT); }

    @Override public void onTick() {
        if (mc.player == null) return;
        if (mc.player.onGround() && mc.player.getY() > minY.get())
            safe = mc.player.position();
        if (mc.player.getY() < minY.get() && safe != null) {
            if (method.get().equals("Bounce"))
                mc.player.setDeltaMovement(
                    mc.player.getDeltaMovement().x,
                    Math.max(1.2, (safe.y - mc.player.getY()) * 0.15),
                    mc.player.getDeltaMovement().z);
            else
                mc.player.setPos(safe.x, safe.y + 0.5, safe.z);
        }
    }
}
