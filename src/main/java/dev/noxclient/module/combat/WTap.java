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
    private boolean wWasDown;
    private static WTap instance;

    public WTap() { super("WTap", "W-taps after hits for knockback", Category.COMBAT); instance = this; }

    /** Called by AttackMixin on a real landed hit — no more crosshair-hover firing. */
    public static void onHit() {
        if (instance == null || !instance.isEnabled() || instance.mc.options == null) return;
        if (!dev.noxclient.util.Legitifier.ready(instance, instance.cps.get(), 0.2)) return;
        instance.wWasDown = instance.mc.options.keyUp.isDown();
        instance.mc.options.keyUp.setDown(false);
        instance.stage = 1; instance.t = System.currentTimeMillis();
    }

    @Override public void onDisable() {
        stage = 0;
        if (mc.options != null) mc.options.keyUp.setDown(wWasDown);
    }

    @Override public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        if (stage == 1 && System.currentTimeMillis() - t > delay.get()) {
            mc.options.keyUp.setDown(wWasDown); stage = 0;
        }
    }
}
