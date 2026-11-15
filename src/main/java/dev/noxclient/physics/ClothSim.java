package dev.noxclient.physics;

/**
 * Verlet cloth grid for the cape. Simulated in BODY-LOCAL space (model pixels,
 * y up-positive, +z behind the player) so it can be emitted straight into the
 * renderer pose stack — no world→model transform needed.
 *
 * Top row is pinned to shoulder anchors moved by the caller. External forces:
 * gravity (constant), wind + motion drag (both pre-rotated into the body
 * frame), and a yaw-delta swing applied by rotating the loose points.
 */
public final class ClothSim {

    public final int cols, rows;
    private final float segX, segY;
    private final float[] x, y, z, ox, oy, oz;
    private final boolean[] pin;
    private float ax, ay, az;                  // pin-line centre (swing pivot)
    private float gravity = -140f;             // px/s^2, y-up so negative = down
    private float windX, windY, windZ;         // body-frame accel, px/s^2
    public float drag = 0.984f;
    public int iterations = 5;

    public ClothSim(int cols, int rows, float widthPx, float heightPx) {
        if (cols < 2 || rows < 2 || cols > 32 || rows > 32 || widthPx <= 0 || heightPx <= 0)
            throw new IllegalArgumentException("Invalid cloth dimensions");
        this.cols = cols;
        this.rows = rows;
        segX = widthPx / (cols - 1);
        segY = heightPx / (rows - 1);
        int n = cols * rows;
        x = new float[n]; y = new float[n]; z = new float[n];
        ox = new float[n]; oy = new float[n]; oz = new float[n];
        pin = new boolean[n];
        reset(0, 9.6f, 2.6f);
    }

    /** Re-drape the cloth from the anchor line (teleport / first frame). */
    public void reset(float ax, float ay, float az) {
        this.ax = ax; this.ay = ay; this.az = az;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int i = r * cols + c;
                x[i] = ox[i] = ax + (c - (cols - 1) / 2f) * segX;
                y[i] = oy[i] = ay - r * segY;
                z[i] = oz[i] = az + r * 0.12f;   // slight drape backwards
                pin[i] = r == 0;
            }
        }
    }

    /** Wind/motion acceleration for this step, already in the body frame. */
    public void setWind(float wx, float wy, float wz) { windX = wx; windY = wy; windZ = wz; }

    public void setGravity(float g) { gravity = g; }

    /**
     * Yaw-delta swing: rotate the loose points around the anchor line's Y
     * axis by {@code -dYaw*keep}. Callers pass ~0.88 of the body-yaw delta so
     * most of a fast turn carries the cloth but a residual stays behind and
     * swings back — sells the "turn and the cape follows late" feel.
     */
    public void swing(float radians) {
        if (radians == 0) return;
        float cs = (float) Math.cos(radians), sn = (float) Math.sin(radians);
        for (int i = cols; i < x.length; i++) {   // skip pinned top row
            float dx = x[i] - ax, dz = z[i] - az;
            x[i]  = ax + dx * cs - dz * sn;
            z[i]  = az + dx * sn + dz * cs;
            // rotate the previous position too — otherwise the rotation
            // shows up as phantom velocity and the cloth whips out
            dx = ox[i] - ax; dz = oz[i] - az;
            ox[i] = ax + dx * cs - dz * sn;
            oz[i] = az + dx * sn + dz * cs;
        }
    }

    public void step(float dt) {
        if (!Float.isFinite(dt) || dt <= 0 || dt > .05f) return;
        float dt2 = dt * dt;
        for (int i = 0; i < x.length; i++) {
            if (pin[i]) continue;
            float vx = (x[i] - ox[i]) * drag;
            float vy = (y[i] - oy[i]) * drag;
            float vz = (z[i] - oz[i]) * drag;
            ox[i] = x[i]; oy[i] = y[i]; oz[i] = z[i];
            vx += windX * dt2; vy += (gravity + windY) * dt2; vz += windZ * dt2;
            float displacement = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
            float cap = Math.max(segX, segY);
            float scale = displacement > cap ? cap / displacement : 1;
            x[i] += vx * scale; y[i] += vy * scale; z[i] += vz * scale;
        }
        for (int it = 0; it < iterations; it++) {
            for (int r = 0; r < rows; r++)
                for (int c = 0; c < cols; c++) {
                    if (c + 1 < cols) solve(r * cols + c, r * cols + c + 1, segX);
                    if (r + 1 < rows) solve(r * cols + c, (r + 1) * cols + c, segY);
                    // shear links keep the quad grid from collapsing
                    if (c + 1 < cols && r + 1 < rows) {
                        solve(r * cols + c, (r + 1) * cols + c + 1, diag());
                        solve((r + 1) * cols + c, r * cols + c + 1, diag());
                    }
                }
        }
    }

    /** Resample another LOD without resetting the drape or introducing velocity. */
    public void copyFrom(ClothSim other) {
        ax = other.ax; ay = other.ay; az = other.az;
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            float u = c * (other.cols - 1f) / (cols - 1), v = r * (other.rows - 1f) / (rows - 1);
            int c0 = Math.min((int) u, other.cols - 2), r0 = Math.min((int) v, other.rows - 2);
            int i = r * cols + c, a = r0 * other.cols + c0;
            float fx = u - c0, fy = v - r0;
            x[i] = sample(other.x, a, other.cols, fx, fy); y[i] = sample(other.y, a, other.cols, fx, fy); z[i] = sample(other.z, a, other.cols, fx, fy);
            ox[i] = sample(other.ox, a, other.cols, fx, fy); oy[i] = sample(other.oy, a, other.cols, fx, fy); oz[i] = sample(other.oz, a, other.cols, fx, fy);
        }
    }
    private static float sample(float[] a, int i, int cols, float x, float y) {
        return AnimMath.lerp(AnimMath.lerp(a[i], a[i + 1], x), AnimMath.lerp(a[i + cols], a[i + cols + 1], x), y);
    }

    private float diag() { return (float) Math.sqrt(segX * segX + segY * segY); }

    private void solve(int a, int b, float rest) {
        float dx = x[b] - x[a], dy = y[b] - y[a], dz = z[b] - z[a];
        float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1e-5f) return;
        float diff = (d - rest) / d;
        boolean pa = pin[a], pb = pin[b];
        float wa = pa ? 0 : (pb ? 1f : 0.5f), wb = pb ? 0 : (pa ? 1f : 0.5f);
        x[a] += dx * diff * wa; y[a] += dy * diff * wa; z[a] += dz * diff * wa;
        x[b] -= dx * diff * wb; y[b] -= dy * diff * wb; z[b] -= dz * diff * wb;
    }

    public float x(int r, int c) { return x[r * cols + c]; }
    public float y(int r, int c) { return y[r * cols + c]; }
    public float z(int r, int c) { return z[r * cols + c]; }
}
