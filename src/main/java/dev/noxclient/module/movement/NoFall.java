package dev.noxclient.module.movement;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class NoFall extends Module {
    private final Setting.Mode mode = add(new Setting.Mode("Mode", "Packet", "Packet", "Reset"));
    private final Setting.Num minFall = add(new Setting.Num("Min fall blocks", 3, 2, 20));

    private boolean fired;   // latch: one spoof per fall, re-arms on landing

    public NoFall() { super("NoFall", "Prevents fall damage (Packet mode may kick on strict AC)", Category.MOVEMENT); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.getConnection() == null) return;
        if (mc.player.onGround() || mc.player.fallDistance < 1) { fired = false; return; }
        if (fired || mc.player.fallDistance < minFall.get()) return;
        // server kicks ("Invalid move player packet") if a move packet lands
        // inside the post-teleport/join confirm window — grace covers that
        if (mc.player.tickCount < 60 || mc.player.isPassenger() || mc.player.isDeadOrDying()) return;
        fired = true;
        // Grim strict: a spoofed onGround packet contradicts the server's own
        // fall tracking — instant Ground flag. AutoMLG water-clutch is the
        // legal no-fall: it places real water with real packets.
        if (dev.noxclient.module.donutsmp.DonutGuard.strict()) return;
        if (mode.get().equals("Packet")) {
            mc.getConnection().send(new ServerboundMovePlayerPacket.StatusOnly(true, false));
        }
        mc.player.resetFallDistance();
    }

    @Override public String status() {
        return dev.noxclient.module.donutsmp.DonutGuard.strict()
            ? "blocked (Grim) — use AutoMLG" : "armed";
    }
}
