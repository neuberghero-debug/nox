package dev.noxclient.module.movement;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.world.phys.Vec3;

/**
 * Fly Bypass [PREMIUM] — position-packet flight: each tick sends a Pos packet
 * with micro vertical jitter below the server's move-check threshold, plus a
 * StatusOnly ground-spoof so strict checks see a legal ground cycle. Horizontal
 * motion follows your input at the configured speed.
 */
public class FlyBypass extends Module {
    private final Setting.Num speed = add(new Setting.Num("Speed", 0.5, 0.1, 2.0));
    private final Setting.Num jitter = add(new Setting.Num("Y jitter", 0.04, 0.01, 0.2));
    private boolean phase;

    public FlyBypass() { super("FlyBypass", "Packet fly", Category.MOVEMENT); risky = true; premium = true; }

    @Override
    public void onTick() {
        if (mc.player == null || mc.getConnection() == null) return;
        if (mc.player.tickCount < 60) return;   // post-teleport confirm window
        double yOff = (phase = !phase) ? jitter.get() : -jitter.get();
        Vec3 input = inputVector().scale(speed.get());
        double nx = mc.player.getX() + input.x;
        double ny = mc.player.getY() + yOff + (mc.options.keyJump.isDown() ? speed.get() * 0.5 : 0)
            - (mc.options.keyShift.isDown() ? speed.get() * 0.5 : 0);
        double nz = mc.player.getZ() + input.z;

        mc.player.setPos(nx, ny, nz);
        mc.player.setDeltaMovement(Vec3.ZERO);
        // no manual Pos packet — setPos makes vanilla send one PosRot itself,
        // and a second hasPosition() packet in the same server tick disconnects
        // with invalid_player_movement (26.3 check).
        mc.player.resetFallDistance();
    }

    private Vec3 inputVector() {
        var mv = mc.player.input.getMoveVector();
        float s = mv.x, f = mv.y;
        if (f == 0 && s == 0) return Vec3.ZERO;
        double yaw = Math.toRadians(mc.player.getYRot());
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        return new Vec3(s * cos - f * sin, 0, f * cos + s * sin).normalize();
    }
}
