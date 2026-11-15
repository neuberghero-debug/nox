package dev.noxclient.physics;

/** Reusable fixed-step secondary-motion chain. No allocations after construction. */
public final class VerletChain {
    public final int count;
    private final float length;
    private final float[] x, y, z, oldX, oldY, oldZ;
    public float gravityX, gravityY = -9.81f, gravityZ, drag = .985f;
    public int iterations = 8;

    public VerletChain(int count, float length) {
        if (count < 2 || count > 128 || !Float.isFinite(length) || length <= 0)
            throw new IllegalArgumentException("Chain requires 2..128 points and positive length");
        this.count = count; this.length = length;
        x = new float[count]; y = new float[count]; z = new float[count];
        oldX = new float[count]; oldY = new float[count]; oldZ = new float[count];
        reset(0, 0, 0);
    }
    public void reset(float ax, float ay, float az) {
        for (int i = 0; i < count; i++) {
            x[i] = oldX[i] = ax; y[i] = oldY[i] = ay - length * i; z[i] = oldZ[i] = az;
        }
    }
    public void anchor(float ax, float ay, float az) {
        x[0] = oldX[0] = ax; y[0] = oldY[0] = ay; z[0] = oldZ[0] = az;
    }
    public void step(float dt) {
        if (!Float.isFinite(dt) || dt <= 0 || dt > .1f) return;
        float damp = Math.max(0, Math.min(1, drag)), h = dt * dt;
        for (int i = 1; i < count; i++) {
            float vx = (x[i] - oldX[i]) * damp + gravityX * h;
            float vy = (y[i] - oldY[i]) * damp + gravityY * h;
            float vz = (z[i] - oldZ[i]) * damp + gravityZ * h;
            float distance = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
            float scale = distance > length ? length / distance : 1;
            oldX[i] = x[i]; oldY[i] = y[i]; oldZ[i] = z[i];
            x[i] += vx * scale; y[i] += vy * scale; z[i] += vz * scale;
        }
        for (int pass = 0; pass < Math.max(1, Math.min(32, iterations)); pass++) {
            for (int i = 1; i < count; i++) {
                float dx = x[i] - x[i - 1], dy = y[i] - y[i - 1], dz = z[i] - z[i - 1];
                float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d < 1e-6f) continue;
                float f = (d - length) / d, parent = i == 1 ? 0 : .5f;
                x[i - 1] += dx * f * parent; y[i - 1] += dy * f * parent; z[i - 1] += dz * f * parent;
                x[i] -= dx * f * (1 - parent); y[i] -= dy * f * (1 - parent); z[i] -= dz * f * (1 - parent);
            }
        }
    }
    public float x(int i) { return x[i]; }
    public float y(int i) { return y[i]; }
    public float z(int i) { return z[i]; }
}
