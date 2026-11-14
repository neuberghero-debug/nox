package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;

/** Clicks respawn automatically on the death screen. */
public class AutoRespawn extends Module {
    public final Setting.Num delayS = add(new Setting.Num("Delay s", 1.0, 0, 10));
    private long diedAt;

    public AutoRespawn() { super("AutoRespawn", "Respawns instantly on death", Category.MISC); }

    @Override public void onTick() {
        if (mc.player == null || mc.getConnection() == null) return;
        if (mc.player.isAlive()) { diedAt = 0; return; }
        if (diedAt == 0) diedAt = System.currentTimeMillis();
        if (System.currentTimeMillis() - diedAt >= delayS.get() * 1000
            && Legitifier.ready(this, 2, 0)) {
            mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            diedAt = 0;
        }
    }
}
