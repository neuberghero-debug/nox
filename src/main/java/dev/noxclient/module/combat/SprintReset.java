package dev.noxclient.module.combat;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/**
 * SprintReset — after every hit while sprinting, pulses W for a tick so sprint
 * re-engages → full knockback on next hit ("hit selecting"). The mixin on
 * attack() arms the pulse; onTick performs it.
 */
public class SprintReset extends Module {
    private final Setting.Bool hitSelect = add(new Setting.Bool("Hit selecting", true));
    private static volatile int pulseTicks;
    private static SprintReset instance;

    public SprintReset() { super("SprintReset", "Re-sprints after hits for max KB", Category.COMBAT); instance = this; }

    public static void onHit() { if (instance != null && instance.isEnabled()) pulseTicks = 2; }

    @Override
    public void onTick() {
        if (pulseTicks <= 0) return;
        pulseTicks--;
        // release W one tick, re-press the next — vanilla sprint reset
        if (mc.options != null) mc.options.keyUp.setDown(pulseTicks == 0);
    }

    @Override
    protected void onDisable() { pulseTicks = 0; if (mc.options != null) mc.options.keyUp.setDown(false); }
}
