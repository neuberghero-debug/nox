package dev.noxclient.module.movement;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

public class NoFall extends Module {
    private final Setting.Mode mode = add(new Setting.Mode("Mode", "Packet", "Packet", "Reset"));
    private final Setting.Num minFall = add(new Setting.Num("Min fall blocks", 3, 2, 20));
    private final Setting.Bool continuous = add(new Setting.Bool("Continuous spoof", true,
        "Anubis DonutNoFall: keep claiming onGround for the WHOLE fall so the "
        + "server fall-distance never accumulates — also covers long drops"));

    private boolean fired;   // latch: one spoof per fall (continuous=off), re-arms on landing

    public NoFall() { super("NoFall", "Prevents fall damage (Packet mode may kick on strict AC)", Category.MOVEMENT); risky = true; }

    @Override
    public void onTick() {
        if (mc.player == null || mc.getConnection() == null) return;
        if (mc.player.onGround() || mc.player.fallDistance < 1) { fired = false; return; }
        if (mc.player.fallDistance < minFall.get()) return;
        // server kicks ("Invalid move player packet") if a move packet lands
        // inside the post-teleport/join confirm window — grace covers that
        if (mc.player.tickCount < 60 || mc.player.isPassenger() || mc.player.isDeadOrDying()) return;
        if (mode.get().equals("Packet")) {
            if (continuous.on()) {
                // every vanilla move packet leaves with onGround=true —
                // server fall-distance resets every tick, landing deals 0
                dev.noxclient.util.MoveGuard.requestOnGround(true);
            } else {
                if (fired) return;
                fired = true;
                dev.noxclient.util.MoveGuard.requestOnGround(true);
            }
            return;
        }
        if (fired) return;
        fired = true;
        mc.player.resetFallDistance();
    }
}
