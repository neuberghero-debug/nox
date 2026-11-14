package dev.noxclient.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Animated Gargantua-style sky for the title screen — a near edge-on
 * accretion disk with gravitational-lensing silhouette: the disk's far side
 * appears as bright arcs above and below the event horizon, plus a hot
 * photon ring, doppler-beamed disk brightness and starfield. Pure fill()
 * primitives, ~1.2k quads/frame.
 */
public final class Gargantua {

    private static final float TAU = (float) (Math.PI * 2);

    public static void render(GuiGraphicsExtractor g, int w, int h) {
        long ms = System.currentTimeMillis();
        float T = ms / 1000f;

        // deep space
        g.fillGradient(0, 0, w, h, 0xFF060410, 0xFF010104);

        // far nebula haze — huge, dim, slow drift
        neb(g, w, h, T, 0.13f, 0x703A1FA0, 0x09);
        neb(g, w, h, T, 0.79f, 0x50103A78, 0x07);
        neb(g, w, h, T, 0.55f, 0xA0401860, 0x05);

        // starfield — 210 deterministic stars, twinkling
        for (int i = 0; i < 210; i++) {
            int sx = Math.floorMod(i * 0x9E3779B1, w);
            int sy = Math.floorMod(i * 40503 + i * i * 31, h);
            float tw = 0.5f + 0.5f * (float) Math.sin(T * 1.4 + i * 1.93f);
            int a = (int) (50 + 190 * tw);
            int big = i % 11 == 0 ? 2 : 1;
            int col = i % 7 == 0 ? 0xC8D8FF : 0xFFF4E8; // blue + warm mix
            g.fill(sx, sy, sx + big, sy + big, (a << 24) | col);
        }

        float cx = w * 0.5f, cy = h * 0.47f;
        float R = Math.min(w, h) * 0.155f;          // horizon radius
        float diskRx = w * 0.72f, diskRy = diskRx * 0.115f;

        // === bloom bed behind the disk band ===
        for (int k = 9; k >= 1; k--) {
            float bh = diskRy * k * 0.5f;
            g.fill((int) (cx - diskRx - 30), (int) (cy - bh),
                (int) (cx + diskRx + 30), (int) (cy + bh), 0x071A0C06 + (k << 24) / 9);
        }

        // === disk: far side first (upper band, behind horizon) ===
        diskPass(g, cx, cy, R, diskRx, diskRy, T, true);

        // === lensed far-side image: outer faint arcs hugging the sphere ===
        lensArcs(g, cx, cy, R * 1.5f, T, 0.5f);

        // === event horizon — black sphere ===
        int ir = (int) R;
        for (int dy = -ir; dy <= ir; dy++) {
            int hw = (int) Math.sqrt(ir * ir - dy * dy);
            g.fill((int) cx - hw, (int) cy + dy, (int) cx + hw, (int) cy + dy + 1, 0xFF000000);
        }

        // === photon ring — thin, hot, nearly circular ===
        for (double a = 0; a < TAU; a += 0.022) {
            int px = (int) (cx + Math.cos(a) * R * 1.055f);
            int py = (int) (cy + Math.sin(a) * R * 1.055f);
            float sh = 0.75f + 0.25f * (float) Math.sin(a * 4 - T * 3);
            g.fill(px, py, px + 2, py + 2, ((int) (235 * sh) << 24) | 0xFFF3DFAF);
        }

        // === inner bright lensed arcs — the signature top/bottom wings ===
        lensArcs(g, cx, cy, R * 1.26f, T, 1.0f);

        // === disk: near side (lower band, in front of the sphere) ===
        diskPass(g, cx, cy, R, diskRx, diskRy, T, false);

        // === hottest spot — top of the ring ===
        for (int k = 14; k > 0; k--) {
            int a = 26 - k;
            g.fill((int) cx - k * 2, (int) (cy - R * 1.1f) - k / 2,
                (int) cx + k * 2, (int) (cy - R * 1.1f) + k / 2, (a << 24) | 0xFFF8E0C0);
        }
    }

    /** Drifting nebula blob: several big translucent rects around a slow orbit. */
    private static void neb(GuiGraphicsExtractor g, int w, int h, float T,
                            float phase, int rgb, int alpha) {
        float nx = w * (0.5f + 0.35f * (float) Math.sin(T * 0.05 + phase * 9f));
        float ny = h * (0.3f + 0.2f * (float) Math.cos(T * 0.04 + phase * 7f));
        for (int k = 8; k > 0; k--) {
            int rw = (int) (w * 0.28f * k / 8), rh = (int) (h * 0.14f * k / 8);
            g.fill((int) nx - rw, (int) ny - rh, (int) nx + rw, (int) ny + rh,
                (alpha << 24) | rgb);
        }
    }

    /**
     * Lensed image of the disk's far side: arcs arcing over the top and under
     * the bottom of the sphere. alpha peaks at top/bottom (|sin|≈1), fades at
     * the sides where the arc merges into the main band.
     */
    private static void lensArcs(GuiGraphicsExtractor g, float cx, float cy,
                                 float r, float T, float mul) {
        for (double a = 0; a < TAU; a += 0.018) {
            float sn = (float) Math.sin(a);
            float up = Math.abs(sn);                      // 1 top/bottom, 0 sides
            if (up < 0.18f) continue;
            float shimmer = 0.6f + 0.4f * (float) Math.sin(a * 5 + T * 2.4);
            int alpha = (int) (200 * up * shimmer * mul);
            if (alpha < 12) continue;
            // radius jitters with the band noise → turbulent disk look
            float rr = r * (1f + 0.045f * (float) Math.sin(a * 9 + T * 1.7));
            int px = (int) (cx + Math.cos(a) * rr);
            int py = (int) (cy + sn * rr);
            g.fill(px, py, px + 2, py + 2, (alpha << 24) | 0xFFE9B873);
        }
    }

    /** One depth-half of the accretion disk. back=true = far side (upper band). */
    private static void diskPass(GuiGraphicsExtractor g, float cx, float cy, float R,
                                 float diskRx, float diskRy, float T, boolean back) {
        for (int i = 0; i < 760; i++) {
            float u = (i * 0.618034f) % 1f;                       // golden scatter
            float rx = R * 1.12f + (diskRx - R * 1.12f) * (float) Math.pow(u, 1.55);
            // differential rotation: inner orbits faster
            float ang = u * TAU * 7.31f + T * 0.9f * (float) Math.sqrt(diskRx * 0.30f / rx);
            float sx = (float) Math.cos(ang), sy = (float) Math.sin(ang);
            if ((sy < 0) != back) continue;
            int px = (int) (cx + sx * rx);
            int py = (int) (cy + sy * rx * 0.12f);                // edge-on squash
            // doppler beaming: approaching side (left) hotter+brighter
            float beam = 1f - sx * 0.95f;                         // sx<0 → >1
            // temperature: white-hot inner → amber → ember outer
            int col;
            float t = rx / diskRx;
            if (t < 0.12f)      col = 0xFFF8ECD2;
            else if (t < 0.30f) col = 0xFFE8B268;
            else if (t < 0.62f) col = 0xFFC87A32;
            else                col = 0xFF96481E;
            if (beam > 1f) col = blend(col, 0xFFF0F6FF, Math.min(0.5f, (beam - 1f) * 0.8f));
            int alpha = (int) ((back ? 60 : 110) * beam * (0.6f + 0.4f * (i % 3)));
            alpha = Math.min(255, alpha);
            int sz = t < 0.35f ? 3 : 2;                           // inner looks denser
            g.fill(px, py, px + sz, py + 1, (alpha << 24) | col);
        }
    }

    private static int blend(int a, int b, float t) {
        int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return (int) (ar + (br - ar) * t) << 16
             | (int) (ag + (bg - ag) * t) << 8
             | (int) (ab + (bb - ab) * t);
    }
}
