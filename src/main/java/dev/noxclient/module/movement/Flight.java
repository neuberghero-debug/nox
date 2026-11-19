package dev.noxclient.module.movement;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.world.phys.Vec3;

/**
 * Flight with anticheat presets:
 *  - Vanilla: creative-style, flagged on most servers
 *  - Glide: slow-fall horizontal glide, often survives movement checks
 *  - Ghost: tiny periodic offsets that look like latency jitter
 */
public class Flight extends Module {
    private final Setting.Mode mode = add(new Setting.Mode("Mode", "Glide", "Vanilla", "Glide", "Ghost"));
    private final Setting.Num speed = add(new Setting.Num("Speed", 0.4, 0.1, 3.0));
    private final Setting.Num glideFall = add(new Setting.Num("Glide fall", 0.04, 0.0, 0.5));
    private final Setting.Bool autoOff = add(new Setting.Bool("Disable on damage", true));
    private int tick;

    public Flight() { super("Flight", "Fly", Category.MOVEMENT); risky = true; }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        Vec3 v = mc.player.getDeltaMovement();
        switch (mode.get()) {
            case "Vanilla" -> {
                double y = mc.options.keyJump.isDown() ? speed.get()
                         : mc.options.keyShift.isDown() ? -speed.get() : 0;
                mc.player.setDeltaMovement(v.x, y, v.z);
            }
            case "Glide" -> mc.player.setDeltaMovement(v.x, Math.max(v.y, -glideFall.get()), v.z);
            case "Ghost" -> {
                if (++tick % 20 == 0)
                    mc.player.setPos(mc.player.getX(), mc.player.getY() + 0.04, mc.player.getZ());
                mc.player.setDeltaMovement(v.x, Math.max(v.y, -0.02), v.z);
            }
        }
        if (autoOff.on() && mc.player.hurtTime > 0) { setEnabled(false); return; }
        mc.player.resetFallDistance();
    }
}
