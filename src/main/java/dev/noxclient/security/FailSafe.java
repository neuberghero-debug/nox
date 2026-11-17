package dev.noxclient.security;

import dev.noxclient.util.TamperGuard;
import net.minecraft.client.Minecraft;

/**
 * Anti-crack 30.6 — central fail-safe arbiter. Individual detectors
 * (TamperGuard, Distributed, Canary, VmGuard) report here; FailSafe owns the
 * escalation ladder so responses stay consistent:
 *
 *   level 0  clean
 *   level 1  WARN    — one transient failure (toast/chat once)
 *   level 2  DEGRADE — repeated/persistent failure: premium cosmetics off
 *   level 3  LOCK    — hard compromise: engage TamperScreen lock path
 *
 * De-escalates automatically when checks pass again for a while (except LOCK,
 * which is sticky for the session).
 */
public final class FailSafe {
    private static volatile int level;
    private static int cleanTicks, heavy;
    private static long lastWarn;
    private static final java.util.Set<String> reasons = new java.util.HashSet<>();

    public static int level() { return level; }
    public static boolean degraded() { return level >= 2; }
    public static boolean locked() { return level >= 3; }
    public static java.util.Set<String> reasons() {
        return java.util.Set.copyOf(reasons);
    }

    /** Report a failure. Weight 1 = suspicious, 2 = strong (instant LOCK). */
    public static synchronized void fail(String reason, int weight) {
        reasons.add(reason);
        int want = weight >= 2 ? 3 : (level >= 2 ? 2 : 1);
        if (want > level) {
            level = want;
            cleanTicks = 0;
            onEscalate(reason);
        }
    }

    /** Periodic poll (client tick). Detectors push via fail(); this handles
     *  recovery and the scheduled cascade checks. */
    public static void tick() {
        // cheap flags every tick; heavy scans throttled to every 200 ticks
        if (TamperGuard.compromised()) fail("jar-signature", 2);
        if (!dev.noxclient.premium.Distributed.c4()) fail("clock-rollback", 2);
        if (++heavy >= 200) {
            heavy = 0;
            if (VmGuard.instrumented()) fail("vm-agent", 1);
            if (!Canary.ok()) fail("canary", 2);
        }

        if (level > 0 && level < 3) {
            cleanTicks++;
            if (cleanTicks > 20 * 60 * 5) {   // ~5 min clean
                level = Math.max(0, level - 1);
                cleanTicks = 0;
                reasons.clear();
            }
        }
    }

    private static void onEscalate(String reason) {
        if (System.currentTimeMillis() - lastWarn < 10_000) return;
        lastWarn = System.currentTimeMillis();
        var p = Minecraft.getInstance().player;
        String msg = switch (level) {
            case 1 -> "§e[NOX] integrity warning §8(" + reason + ")";
            case 2 -> "§c[NOX] degraded — premium cosmetics disabled §8(" + reason + ")";
            default -> "§4[NOX] LOCKED — tampering detected §8(" + reason + ")";
        };
        if (p != null) p.sendSystemMessage(
            net.minecraft.network.chat.Component.literal(msg));
        dev.noxclient.gui.Toasts.push(msg.replaceAll("§.", ""), 0xFFFF6B6B);
        if (level >= 2) dev.noxclient.module.render.Cosmetics.degrade();
        if (level >= 3) dev.noxclient.module.misc.TamperWarner.engageLock();
    }
}
