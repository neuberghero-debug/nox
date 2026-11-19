package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Removes the 5-tick block-break cooldown — mine at your real speed. */
public class FastBreak extends Module {
    public final Setting.Num delay = add(new Setting.Num("Delay ticks", 0, 0, 5));

    public FastBreak() { super("FastBreak", "No break cooldown", Category.DONUTSMP); risky = true; }

    @Override public void onTick() {
        var gm = mc.gameMode;
        if (gm == null) return;
        var acc = (dev.noxclient.mixin.GameModeAccessor) gm;
        if (acc.nox$getDestroyDelay() > delay.intVal())
            acc.nox$setDestroyDelay(delay.intVal());
    }
}
