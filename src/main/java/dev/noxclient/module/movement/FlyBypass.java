package dev.noxclient.module.movement;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Interact;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Fly Bypass [PREMIUM] — Grim-safe flight through real elytra physics.
 *
 * The old position-packet fly is dead on Grim (setPos without matching motion
 * = instant simulation flag). What survives is REAL elytra flight the server
 * simulates correctly:
 *  - auto START_FALL_FLYING when falling with an elytra equipped
 *  - pitch-glide via silent rotation (rides the vanilla pos packet — the
 *    server's replayed physics matches because it IS elytra physics)
 *  - horizontal speed comes from glide angle + optional AutoRocket pairing
 *
 * "Donut" mode — the documented DonutSMP bypass: slowfall-gravity mimicry +
 * elytra-cancel transitions. Donut's custom AC layer checks gravity
 * consistency and whether the last 20 ticks of vertical motion are physically
 * plausible — a slowfall-shaped descent curve passes that model because it's
 * a real thing the server sees from players with the effect. Gradient
 * velocity, capped speeds, anti-kick dips. Tune: speed ≤1.6, y-cap ~2.4.
 *
 * "Legacy" keeps the old packet jitter for servers without Grim.
 */
public class FlyBypass extends Module {
    private final Setting.Mode mode = add(new Setting.Mode("Mode", "Elytra",
        "Elytra", "Donut", "Legacy"));
    private final Setting.Num glidePitch = add(new Setting.Num("Glide pitch", -4, -30, 15));
    private final Setting.Num donutSpeed = add(new Setting.Num("Donut speed", 1.2, 0.5, 1.6));
    private final Setting.Num speed = add(new Setting.Num("Legacy speed", 0.5, 0.1, 2.0));
    private final Setting.Num jitter = add(new Setting.Num("Legacy Y jitter", 0.04, 0.01, 0.2));
    private int upPulse;
    private boolean phase;
    private boolean flying;
    private long lastCmd;

    public FlyBypass() { super("FlyBypass", "Elytra-physics fly (Grim-safe)", Category.MOVEMENT); premium = true; }

    @Override
    public void onTick() {
        if (mc.player == null || mc.getConnection() == null) return;
        if (mc.player.tickCount < 60) return;   // post-teleport confirm window

        if (mode.get().equals("Donut")) { donutFly(); return; }
        if (!mode.get().equals("Legacy") || dev.noxclient.module.donutsmp.DonutGuard.strict()) {
            elytraFly();
            return;
        }
        legacyFly();
    }

    /**
     * Donut-bypass fly: never flat — a slowfall-shaped descent curve with
     * input glide. dy stays in the -0.3..0 envelope, horizontal motion capped
     * ~1.2x sprint speed. Ascending uses small "elytra-cancel" impulses
     * spaced like real elytra transitions — jittered so tick-patterns vary.
     */
    private void donutFly() {
        var dm = mc.player.getDeltaMovement();
        Vec3 input = inputVector();

        // horizontal: input-shaped, capped — never faster than plausible
        double hs = Math.min(donutSpeed.get(), 1.6) * 0.11;
        double dx = input.x * hs + dm.x * 0.6;
        double dz = input.z * hs + dm.z * 0.6;

        // vertical: slowfall-shaped gravity — pull down gently, floor at -0.3
        double dy = Math.max(dm.y - 0.02, -0.30);
        if (mc.options.keyJump.isDown()) {
            // elytra-cancel pulses: spaced 6-9 ticks, small impulse
            if (--upPulse <= 0) {
                upPulse = 6 + (int) (Math.random() * 4);
                dy = 0.09;
            } else dy = Math.min(dy, -0.05); // between pulses, sag
        }
        if (mc.options.keyShift.isDown()) dy = Math.max(dy - 0.06, -0.45);

        mc.player.setDeltaMovement(dx, dy, dz);
        mc.player.resetFallDistance();

        // if we actually have an elytra, let real glide take over — it's
        // strictly more legal than mimicry
        if (mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            elytraFly();
        }
        mark("donut glide");
    }

    /** Legal elytra flight — server simulates this, so nothing to flag. */
    private void elytraFly() {
        boolean hasElytra = mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
        if (!hasElytra) { flying = false; return; }

        if (mc.player.isFallFlying()) {
            flying = true;
            // glide control through silent rotation — the next pos packet
            // carries this pitch, and the server's elytra sim follows it
            Interact.aim(new Vec3(
                mc.player.getX() - Math.sin(Math.toRadians(mc.player.getYRot())) * 10,
                mc.player.getY() + Math.tan(Math.toRadians(-glidePitch.get())) * 10,
                mc.player.getZ() + Math.cos(Math.toRadians(mc.player.getYRot())) * 10));
            mark("gliding");
        } else {
            flying = false;
            if (!mc.player.onGround() && mc.player.fallDistance > 1
                && System.currentTimeMillis() - lastCmd > 500) {
                lastCmd = System.currentTimeMillis();
                mc.getConnection().send(new ServerboundPlayerCommandPacket(
                    mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            }
        }
    }

    /** Old jitter fly — flags instantly on Grim, kept for weak-AC servers. */
    private void legacyFly() {
        double yOff = (phase = !phase) ? jitter.get() : -jitter.get();
        Vec3 input = inputVector().scale(speed.get());
        double nx = mc.player.getX() + input.x;
        double ny = mc.player.getY() + yOff + (mc.options.keyJump.isDown() ? speed.get() * 0.5 : 0)
            - (mc.options.keyShift.isDown() ? speed.get() * 0.5 : 0);
        double nz = mc.player.getZ() + input.z;
        mc.player.setPos(nx, ny, nz);
        mc.player.setDeltaMovement(Vec3.ZERO);
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

    @Override public String status() {
        if (mode.get().equals("Donut")) return "donut mimicry — tune if flagged";
        if (!mode.get().equals("Legacy") || dev.noxclient.module.donutsmp.DonutGuard.strict())
            return flying ? "gliding" : "needs elytra + fall";
        return "legacy (flags Grim!)";
    }
}
