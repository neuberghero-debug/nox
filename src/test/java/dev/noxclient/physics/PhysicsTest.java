package dev.noxclient.physics;

/** Deterministic regression checks, runnable with gradle physicsCheck. */
public final class PhysicsTest {
    private static void close(double actual, double expected, double tolerance, String context) {
        if (!Double.isFinite(actual) || Math.abs(actual - expected) > tolerance)
            throw new AssertionError(context + ": " + actual + " != " + expected);
    }
    public static void main(String[] args) {
        for (float damping : new float[]{8, 20, 40}) {
            float[] fastV = {0}, slowV = {0};
            float fast = 0, slow = 0;
            for (int i = 0; i < 240; i++) fast = AnimMath.spring(fast, 1, fastV, 100, damping, 1f / 240);
            for (int i = 0; i < 30; i++) slow = AnimMath.spring(slow, 1, slowV, 100, damping, 1f / 30);
            close(fast, slow, 1e-5, "spring frame partition");
            close(fastV[0], slowV[0], 1e-4, "spring velocity partition");
        }
        for (int i = 0; i <= 100; i++) {
            float t = i / 100f;
            close(AnimMath.cubicBezier(t, .2f, .2f, .8f, .8f), t, 1e-5, "linear bezier");
        }
        float[] velocity = {4};
        close(AnimMath.spring(0, 1, velocity, 100, 20, 30), 1, 1e-6, "long stall spring");
        VerletChain chain = new VerletChain(8, .2f);
        for (int i = 0; i < 1200; i++) chain.step(1f / 120);
        close(chain.x(0), 0, 0, "pinned chain anchor");
        for (int i = 1; i < chain.count; i++)
            close(Math.abs(chain.y(i) - chain.y(i - 1)), .2, .004, "chain length");
        chain.reset(5, 10, 8);
        chain.gravityY = 0; chain.step(1f / 120);
        close(chain.y(7), 8.6, 1e-5, "reset removes momentum");
        ClothSim cloth = new ClothSim(8, 12, 10, 16);
        cloth.reset(0, 7.5f, 2.2f);
        for (int i = 0; i < 3600; i++) { cloth.setWind(30, 2, 50); cloth.step(1f / 120); }
        close(cloth.y(0, 0), 7.5, 0, "cloth anchor");
        for (int r = 0; r < cloth.rows; r++) for (int c = 0; c < cloth.cols; c++) {
            if (!Float.isFinite(cloth.x(r, c)) || !Float.isFinite(cloth.z(r, c)))
                throw new AssertionError("cloth nonfinite");
            if (r > 0) {
                double dx = cloth.x(r,c) - cloth.x(r-1,c), dy = cloth.y(r,c) - cloth.y(r-1,c), dz = cloth.z(r,c) - cloth.z(r-1,c);
                close(Math.sqrt(dx*dx+dy*dy+dz*dz), 16f/11, .08, "cloth stretch");
            }
        }
        ClothSim lod = new ClothSim(6, 8, 10, 16); lod.copyFrom(cloth);
        close(lod.x(0,0), cloth.x(0,0), 1e-6, "LOD left anchor");
        close(lod.z(7,5), cloth.z(11,7), 1e-5, "LOD hem");
        System.out.println("Physics checks passed: springs, Bezier inversion, chain lengths/reset, cloth stability/LOD.");
    }
}
