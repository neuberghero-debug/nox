package dev.noxclient.util;

import net.minecraft.client.Minecraft;

import java.util.Random;

/**
 * Central anticheat layer — every flaggable action routes through here.
 *
 *  - gaussian CPS jitter + fatigue pauses (timing)
 *  - eased rotations with speed caps (no snap aim)
 *  - GCD fix: rotation deltas quantized to the mouse-sensitivity quantum, so
 *    packet math is indistinguishable from a real mouse (Grim AimModulo360)
 *  - silent rotations: look packets only, your view never moves
 */
public final class Legitifier {
    private static final Random RNG = new Random();
    private static final java.util.Map<Object, long[]> PACERS = new java.util.IdentityHashMap<>();
    private static final Object SHARED = new Object();

    /** True when it's human-plausible to act now (gaussian-jittered pacing).
     *  NOTE: shared pacing — every caller competes for the same window. */
    public static boolean ready(double baseCps, double jitter) {
        return ready(SHARED, baseCps, jitter);
    }

    /** Per-owner pacing so concurrent modules don't starve each other. */
    public static synchronized boolean ready(Object owner, double baseCps, double jitter) {
        long[] st = PACERS.computeIfAbsent(owner,
            k -> new long[]{0, System.currentTimeMillis() + 30_000 + RNG.nextInt(60_000)});
        long now = System.currentTimeMillis();
        if (now < st[0]) return false;
        if (now > st[1]) {
            st[0] = now + 300 + RNG.nextInt(700);
            st[1] = now + 30_000 + RNG.nextInt(90_000);
            return false;
        }
        double delay = 1000.0 / baseCps;
        delay *= 1.0 + (RNG.nextGaussian() * jitter);
        st[0] = now + (long) Math.max(45, delay);
        return true;
    }

    /** Mouse-sensitivity quantum: smallest yaw/pitch delta a real mouse produces. */
    public static double gcdQuantum() {
        double f = Minecraft.getInstance().options.sensitivity().get() * 0.6 + 0.2;
        return f * f * f * 8.0 * 0.15;
    }

    /** Snap a delta to the nearest multiple of the mouse quantum (GCD fix). */
    public static float gcdFix(float delta) {
        double q = gcdQuantum();
        return (float) (Math.round(delta / q) * q);
    }

    /**
     * Ease current rotation toward target, speed-capped, micro-noise, and
     * GCD-quantized so the outgoing look packets match legit mouse math.
     */
    public static float approach(float current, float target, float maxStep) {
        float diff = wrap(target - current);
        float step = Math.max(-maxStep, Math.min(maxStep, diff));
        step += (float) (RNG.nextGaussian() * maxStep * 0.08);
        return current + gcdFix(step);
    }

    /**
     * Silent rotation without sending an extra packet — GrimAC Timer kicks on
     * injected move packets, so the rotation is rewritten onto the next
     * outgoing packet by MoveGuard instead.
     */
    public static void silentRot(float yaw, float pitch) {
        MoveGuard.requestRot(yaw, pitch);
    }

    public static float wrap(float deg) {
        deg %= 360f;
        if (deg >= 180f) deg -= 360f;
        if (deg < -180f) deg += 360f;
        return deg;
    }
}
