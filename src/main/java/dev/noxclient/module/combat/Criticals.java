package dev.noxclient.module.combat;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import dev.noxclient.util.MoveGuard;

/**
 * Packet-mode criticals: requests a +0.0625 hop through MoveGuard. While the
 * client is emitting position packets the guard rewrites the next vanilla
 * Pos/PosRot in-flight (one packet stays one packet); while traffic is quiet
 * the hop is sent directly and vanilla's next position packet is eaten, so a
 * second hasPosition() packet can never reach a server tick.
 */
public class Criticals extends Module {
    private final Setting.Num cps = add(new Setting.Num("CPS", 8, 1, 15));

    public Criticals() { super("Criticals", "Forces critical hits", Category.COMBAT); risky = true; }

    @Override
    public void onTick() {
        if (mc.player == null || mc.getConnection() == null) return;
        // join/teleport grace — early move packets kick with "invalid move"
        if (mc.player.tickCount < 60) return;
        if (!mc.player.onGround() || mc.player.getAttackStrengthScale(0) < 1.0f) return;
        // stationary only: while moving the client sends its own PosRot and a
        // mid-run hop is pointless anyway — real crits come from real jumps.
        if (mc.player.getDeltaMovement().horizontalDistanceSqr() > 1.0e-6) return;
        // Only hop when something is actually hittable — idle micro-hops are
        // pure packet spam that packet-order checks love to flag.
        boolean near = KillAura.currentTarget != null;
        if (!near && mc.level != null)
            for (var e : mc.level.entitiesForRendering())
                if (e != mc.player && e instanceof net.minecraft.world.entity.LivingEntity
                    && e.distanceToSqr(mc.player) < 16) { near = true; break; }
        if (!near || !Legitifier.ready(this, cps.get(), 0.4)) return;

        // GrimAC Timer kicks on EXTRA move packets — the hop must ride on the
        // next vanilla Pos/PosRot via MoveGuard's in-flight rewrite. Standing
        // perfectly still means no pos packet to carry it; the hop then just
        // waits for the 20-tick reminder instead of being injected.
        MoveGuard.requestHop(0.0625);
    }
}
