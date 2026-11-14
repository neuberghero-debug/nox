package dev.noxclient.physics;

/**
 * Procedural-animation math: easing curves, a critically-damped spring
 * (variable-dt stable), and cheap seeded value noise for idle sway. Pure
 * functions only — nothing here touches Minecraft state, so every piece is
 * trivially testable. Used by the cloth cape and the animation layer.
 */
public final class AnimMath {

    private AnimMath() {}

    // ---- easing -----------------------------------------------------------

    public static float linear(float t) { return t; }

    public static float easeInOutQuad(float t) {
        return t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f;
    }

    public static float easeOutBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(t - 1f, 3) + c1 * (float) Math.pow(t - 1f, 2);
    }

    public static float easeOutElastic(float t) {
        if (t <= 0f || t >= 1f) return t;
        float c4 = (float) (2 * Math.PI / 3);
        return (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * c4) + 1);
    }

    public static float smootherstep(float t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    /** CSS-style cubic bezier through (0,0),(p1,p2 anchor-free x),(1,1).
     *  Control points (p1x=1/3+p1, p2x=2/3) — we only need a y(t) approx, so
     *  blend the two control ys over t with the classic basis. */
    public static float cubicBezier(float t, float p1y, float p2y) {
        float u = 1f - t;
        return 3f * u * u * t * p1y + 3f * u * t * t * p2y + t * t * t;
    }

    // ---- spring-damper ----------------------------------------------------

    /**
     * Critically-damped spring step. {@code vel} is a one-element in/out
     * velocity. Stable for variable dt (substeps recommended above ~50ms).
     */
    public static float spring(float pos, float target, float[] vel,
                               float stiffness, float damping, float dt) {
        float f = -stiffness * (pos - target) - damping * vel[0];
        vel[0] += f * dt;
        return pos + vel[0] * dt;
    }

    // ---- noise ------------------------------------------------------------

    /** Seeded 1-D value noise in [-1,1], smooth-interpolated. Seed per entity
     *  UUID so two players never breathe in sync. */
    public static float noise1(long seed, float x) {
        int i = (int) Math.floor(x);
        float f = x - i;
        return lerp(hash1(seed, i), hash1(seed, i + 1), smootherstep(f));
    }

    /** Seeded 2-D value noise in [-1,1] — used for global wind direction. */
    public static float noise2(long seed, float x, float y) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        float fx = x - ix, fy = y - iy;
        float a = hash2(seed, ix, iy), b = hash2(seed, ix + 1, iy);
        float c = hash2(seed, ix, iy + 1), d = hash2(seed, ix + 1, iy + 1);
        return lerp(lerp(a, b, smootherstep(fx)), lerp(c, d, smootherstep(fx)),
            smootherstep(fy));
    }

    public static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static float hash1(long seed, int i) {
        long h = seed ^ (i * 0x9E3779B97F4A7C15L);
        h = (h ^ (h >>> 33)) * 0xFF51AFD7ED558CCDL;
        h = (h ^ (h >>> 33)) * 0xC4CEB9FE1A85EC53L;
        return (((h ^ (h >>> 33)) & 0xFFFFFF) / 8388607.5f) - 1f;
    }

    private static float hash2(long seed, int x, int y) {
        return hash1(seed, x * 73428767 ^ y * 912271);
    }
}
