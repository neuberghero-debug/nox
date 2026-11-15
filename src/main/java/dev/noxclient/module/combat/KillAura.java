package dev.noxclient.module.combat;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * KillAura with a strict "legit" profile for anticheat servers:
 *  - gaussian CPS jitter, fatigue pauses (Legitifier)
 *  - smoothed, speed-limited rotations — never snaps
 *  - raycast gated: only swings when the crosshair actually lands on the target
 *  - only fires while a real weapon/attack is appropriate, respects attack cooldown
 */
public class KillAura extends Module {
    /** Last entity the aura is tracking — read by TargetHUD. */
    public static LivingEntity currentTarget;
    private final Setting.Num range = add(new Setting.Num("Range", 3.0, 1.0, 6.0));
    private final Setting.Num cps = add(new Setting.Num("CPS", 11, 1, 20));
    private final Setting.Num rotSpeed = add(new Setting.Num("Rotation speed", 12, 2, 40));
    private final Setting.Bool playersOnly = add(new Setting.Bool("Players only", true));
    private final Setting.Mode rotMode = add(new Setting.Mode("Rotations", "Legit", "Legit", "Silent"));

    public KillAura() { super("KillAura", "Attacks nearby entities humanly", Category.COMBAT); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) { currentTarget = null; return; }
        LivingEntity target = nearest();
        currentTarget = target;
        if (target == null) return;

        Vec3 eyes = mc.player.getEyePosition();
        Vec3 to = target.getEyePosition().subtract(eyes);
        double yaw = Math.toDegrees(Math.atan2(-to.x, to.z));
        double pitch = Math.toDegrees(-Math.atan2(to.y, Math.hypot(to.x, to.z)));

        float max = rotSpeed.get().floatValue();
        float ny = Legitifier.approach(mc.player.getYRot(), (float) yaw, max);
        float np = Legitifier.approach(mc.player.getXRot(), (float) pitch, max);
        if (rotMode.get().equals("Silent")) {
            // zero-packet silent aim: the next outgoing Rot/PosRot/StatusOnly
            // is rewritten in-flight by MoveGuard — GrimAC Timer kicks on any
            // extra move packet we used to inject here.
            dev.noxclient.util.MoveGuard.requestRot(ny, np);
        } else {
            mc.player.setYRot(ny);
            mc.player.setXRot(np);
        }

        // attack only when cooldown is full AND timing passes the human filter
        if (mc.player.getAttackStrengthScale(0) >= 1.0f
            && Legitifier.ready(this, cps.get(), 0.35)
            && (rotMode.get().equals("Silent") || isAimedAt(target))) {
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        }
    }

    private boolean isAimedAt(LivingEntity t) {
        var hit = mc.crosshairPickEntity;
        return hit == t || t.getBoundingBox().inflate(0.35).contains(mc.player.getEyePosition()
            .add(mc.player.getLookAngle().scale(range.get())));
    }

    private LivingEntity nearest() {
        LivingEntity best = null;
        double bd = range.get() * range.get();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le) || e == mc.player || !le.isAlive()) continue;
            if (playersOnly.on() && !(e instanceof Player)) continue;
            double d = mc.player.distanceToSqr(le);
            if (d < bd) { bd = d; best = le; }
        }
        return best;
    }

    @Override
    protected void onDisable() { currentTarget = null; }
    @Override public String status() {
        return currentTarget != null
            ? "attacking " + currentTarget.getName().getString()
            : "no target in range";
    }

}
