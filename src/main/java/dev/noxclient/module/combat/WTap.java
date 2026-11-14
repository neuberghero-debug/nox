package dev.noxclient.module.combat;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;

/** W-tap: re-presses forward right after landing a hit for max knockback. */
public class WTap extends Module {
    public final Setting.Num cps = add(new Setting.Num("CPS", 8, 1, 20));
    public final Setting.Num delay = add(new Setting.Num("Re-press ms", 60, 20, 200));
    private int stage; private long t;

    public WTap() { super("WTap", "W-taps after hits for knockback", Category.COMBAT); }

    @Override public void onDisable() { stage = 0; }

    @Override public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        if (stage == 1 && System.currentTimeMillis() - t > delay.get()) {
            mc.options.keyUp.setDown(true); stage = 0;
        }
        if (mc.crosshairPickEntity != null && mc.player.getAttackStrengthScale(0) > 0.9f
            && Legitifier.ready(this, cps.get(), 0.2)) {
            mc.options.keyUp.setDown(false);
            stage = 1; t = System.currentTimeMillis();
        }
    }
}
