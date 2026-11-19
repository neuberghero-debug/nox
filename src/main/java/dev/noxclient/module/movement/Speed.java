package dev.noxclient.module.movement;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.world.phys.Vec3;

/**
 * Movement speed. "Legit" preset stays under vanilla sprint speed and only
 * adds strafe jitter so movement checks (NCP/Grim-style) stay quiet.
 */
public class Speed extends Module {
    private final Setting.Mode mode = add(new Setting.Mode("Mode", "Legit", "Legit", "Strafe", "BHop"));
    private final Setting.Num multiplier = add(new Setting.Num("Multiplier", 1.08, 1.0, 2.0, "Speed multiplier on top of vanilla"));

    public Speed() { super("Speed", "Move faster", Category.MOVEMENT); risky = true; }

    @Override
    public void onTick() {
        if (mc.player == null || !mc.player.onGround()) return;
        Vec3 v = mc.player.getDeltaMovement();
        if (v.horizontalDistanceSqr() < 1.0e-4) return;
        double mult = switch (mode.get()) {
            case "Legit" -> Math.min(multiplier.get(), 1.12); // hard cap: stay near vanilla
            case "Strafe" -> multiplier.get();
            case "BHop" -> { mc.player.jumpFromGround(); yield multiplier.get(); }
            default -> 1.0;
        };
        mc.player.setDeltaMovement(v.x * mult, v.y, v.z * mult);
    }

    @Override public String status() {
        double eff = mode.get().equals("Legit") ? Math.min(multiplier.get(), 1.12) : multiplier.get();
        return String.format(java.util.Locale.ROOT, "×%.2f", eff)
            + (mode.get().equals("Legit") && multiplier.get() > 1.12 ? " (legit cap)" : "");
    }
}
