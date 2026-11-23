package dev.noxclient.module.combat;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/**
 * Velocity — anti-knockback.
 *
 * "Scale" modes shrink the kb vector client-side — Grim replays the expected
 * knockback and flags the mismatch, so scaling is for weak-AC servers only.
 *
 * "JumpReset" is the Grim-safe bypass: jumping the tick you take a hit eats
 * most of the knockback through the LEGITIMATE sprint-reset mechanic — the
 * server sees a normal jump, nothing to flag.
 */
public class Velocity extends Module {
    public final Setting.Mode mode = add(new Setting.Mode("Mode", "JumpReset",
        "JumpReset", "Scale"));
    public final Setting.Num horizontal = add(new Setting.Num("Horizontal %", 0, 0, 100));
    public final Setting.Num vertical = add(new Setting.Num("Vertical %", 0, 0, 100));
    public final Setting.Num chance = add(new Setting.Num("Chance %", 100, 0, 100));

    public Velocity() { super("Velocity", "Reduces knockback", Category.COMBAT); }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (!mode.get().equals("JumpReset") && !dev.noxclient.module.donutsmp.DonutGuard.strict())
            return;
        // the legit jump-reset: hurt + onGround -> jump. Server sees a normal
        // jump; kb lands on a rising player and cancels out.
        if (mc.player.hurtTime > 0 && mc.player.onGround()
            && !mc.player.isInLiquid() && !mc.player.onClimbable()) {
            mc.player.jumpFromGround();
            mark("jump-reset");
        }
    }

    /** VelocityMixin consults this — strict/JumpReset means: never scale. */
    public boolean grimSafe() {
        return mode.get().equals("JumpReset")
            || dev.noxclient.module.donutsmp.DonutGuard.strict();
    }

    @Override public String status() {
        return grimSafe() ? "jump-reset (Grim-safe)" : "scaling (flags Grim!)";
    }
}
