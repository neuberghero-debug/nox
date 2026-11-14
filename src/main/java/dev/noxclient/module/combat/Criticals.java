package dev.noxclient.module.combat;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/**
 * Packet-mode criticals: emits the vanilla-style micro-offsets before a hit so
 * hits register as crits while falling — timing jittered via Legitifier.
 */
public class Criticals extends Module {
    private final Setting.Num cps = add(new Setting.Num("CPS", 8, 1, 15));

    public Criticals() { super("Criticals", "Forces critical hits", Category.COMBAT); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.getConnection() == null) return;
        // join/teleport grace — early move packets kick with "invalid move"
        if (mc.player.tickCount < 60) return;
        if (!mc.player.onGround() || mc.player.getAttackStrengthScale(0) < 1.0f) return;
        // Only hop when something is actually hittable — idle micro-hops are
        // pure packet spam that packet-order checks love to flag.
        boolean near = KillAura.currentTarget != null;
        if (!near && mc.level != null)
            for (var e : mc.level.entitiesForRendering())
                if (e != mc.player && e instanceof net.minecraft.world.entity.LivingEntity
                    && e.distanceToSqr(mc.player) < 16) { near = true; break; }
        if (!near || !dev.noxclient.util.Legitifier.ready(this, cps.get(), 0.4)) return;
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        var conn = mc.getConnection();
        conn.send(new ServerboundMovePlayerPacket.Pos(x, y + 0.0625, z, false, false));
        conn.send(new ServerboundMovePlayerPacket.Pos(x, y, z, false, false));
    }
}
