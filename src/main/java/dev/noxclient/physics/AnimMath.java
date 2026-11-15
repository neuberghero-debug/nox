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

    /** Parametric Bezier ordinate, not a CSS time-to-progress easing. */
    public static float cubicBezier(float t, float p1y, float p2y) {
        float u = 1f - t;
        return 3f * u * u * t * p1y + 3f * u * t * t * p2y + t * t * t;
    }

    /** CSS timing curve: solve x(parameter)=time, then evaluate y(parameter). */
    public static float cubicBezier(float time, float x1, float y1, float x2, float y2) {
        if (!Float.isFinite(time)) return 0;
        if (time <= 0 || time >= 1) return Math.max(0, Math.min(1, time));
        x1 = Math.max(0, Math.min(1, x1)); x2 = Math.max(0, Math.min(1, x2));
        float lo = 0, hi = 1, u = time;
        for (int i = 0; i < 22; i++) {
            float x = cubicBezier(u, x1, x2);
            if (x < time) lo = u; else hi = u;
            u = (lo + hi) * .5f;
        }
        return cubicBezier(u, y1, y2);
    }

    /**
     * Exact solution of x'' + damping*x' + stiffness*(x-target)=0 for a
     * constant target over dt. Unit mass; critical damping = 2*sqrt(stiffness).
     * Unlike Euler integration this remains stable at variable frame times.
     */
    public static float spring(float pos, float target, float[] vel,
                               float stiffness, float damping, float dt) {
        if (!Float.isFinite(target)) return pos;
        if (!Float.isFinite(pos) || !Float.isFinite(vel[0])) { vel[0] = 0; return target; }
        if (!Float.isFinite(dt) || dt <= 0) return pos;
        double k = Math.max(0, Float.isFinite(stiffness) ? stiffness : 0);
        double c = Math.max(0, Float.isFinite(damping) ? damping : 0);
        double x = pos - target, v = vel[0], h = c / 2, d = h * h - k;
        double nx, nv;
        if (k == 0) {
            double decay = Math.exp(-c * dt);
            nx = x + (c == 0 ? v * dt : v * (1 - decay) / c); nv = v * decay;
        } else if (Math.abs(d) < 1e-6 * Math.max(1, k)) {
            double b = v + h * x, e = Math.exp(-h * dt);
            nx = (x + b * dt) * e; nv = (v - h * b * dt) * e;
        } else if (d < 0) {
            double w = Math.sqrt(-d), e = Math.exp(-h * dt), cos = Math.cos(w * dt), sin = Math.sin(w * dt);
            nx = e * (x * cos + (v + h * x) / w * sin);
            nv = e * (v * cos - (h * v + k * x) / w * sin);
        } else {
            double w = Math.sqrt(d), r1 = -h + w, r2 = -h - w;
            double a = (v - r2 * x) / (r1 - r2), b = x - a;
            double e1 = Math.exp(r1 * dt), e2 = Math.exp(r2 * dt);
            nx = a * e1 + b * e2; nv = r1 * a * e1 + r2 * b * e2;
        }
        vel[0] = (float) nv;
        return target + (float) nx;
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
