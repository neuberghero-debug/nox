package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;

/** Runs /kit (or a custom command) after respawning — insta-regear. */
public class AutoKit extends Module {
    public final Setting.Str kit = add(new Setting.Str("Kit name", "pvp"));
    public final Setting.Num delayS = add(new Setting.Num("Delay s", 2, 0, 15));
    private boolean wasDead;
    private long respawned;

    public AutoKit() { super("AutoKit", "Auto /kit after respawn", Category.DONUTSMP); }

    @Override public void onTick() {
        if (mc.player == null || mc.getConnection() == null) return;
        boolean dead = !mc.player.isAlive();
        if (wasDead && !dead) respawned = System.currentTimeMillis();
        wasDead = dead;
        if (respawned == 0 || System.currentTimeMillis() - respawned < delayS.get() * 1000) return;
        if (!Legitifier.ready(this, 1, 0.5)) return;
        mc.getConnection().sendCommand("kit " + kit.get());
        respawned = 0;
    }
}
