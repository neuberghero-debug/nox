package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/**
 * DonutGuard — the anti-cheat master switch, ON by default.
 *
 * While enabled, every module that could produce an impossible packet shape
 * clamps itself into Grim-safe behavior instead of flagging:
 *
 *  - Speed        → real sprint-jump only (no velocity multiplier)
 *  - Flight       → disabled (elytra-glide only — use ElytraGlide)
 *  - FlyBypass    → elytra physics instead of raw position packets
 *  - NoFall       → packet spoofing off (use AutoMLG for real clutches)
 *  - Velocity     → JumpReset instead of motion scaling
 *  - HighJump     → off (no jump impulse above vanilla 0.42)
 *  - FastLadder   → vanilla climb speed
 *  - NoSlow       → off (item slowdown is server-simulated)
 *  - AntiVoid     → off (teleports flag instantly)
 *  - FastBreak    → vanilla 5-tick delay
 *  - FastPlace    → max 5 places/s (vanilla rate)
 *  - Nuker        → reach ≤4.3 + real rotation toward the block
 *  - KillAura     → reach clamped to 3.05
 *  - all places   → silent rotation via MoveGuard so server rays match
 *
 * Disable only on servers without Grim/anticheat.
 */
public class DonutGuard extends Module {
    private final Setting.Bool strict = add(new Setting.Bool("Grim strict mode", true));
    private static volatile DonutGuard INSTANCE;

    public DonutGuard() {
        super("DonutGuard", "Keeps every module Grim-safe", Category.MISC);
        INSTANCE = this;
        restore(true); // on by default
    }

    /** Modules consult this before doing anything packet-impossible. */
    public static boolean strict() {
        return INSTANCE != null && INSTANCE.isEnabled() && INSTANCE.strict.on();
    }
}
