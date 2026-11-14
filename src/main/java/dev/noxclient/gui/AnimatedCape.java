package dev.noxclient.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Animated premium cape — a flowing purple-black gradient with a pulsing X
 * motif, repainted at ~20fps into a registered DynamicTexture. The cape layer
 * reads it through a plain ClientAsset.ResourceTexture like any static cape.
 */
public final class AnimatedCape {
    private static final int W = 64, H = 32;
    private static Identifier id;
    private static DynamicTexture tex;
    private static NativeImage img;
    private static long last;

    public static Identifier id() {
        if (id == null) {
            Minecraft mc = Minecraft.getInstance();
            id = Identifier.fromNamespaceAndPath("noxclient", "animated_cape");
            img = new NativeImage(W, H, false);
            tex = new DynamicTexture(() -> "nox_cape_anim", img);
            mc.getTextureManager().register(id, tex);
        }
        long now = System.currentTimeMillis();
        if (now - last > 50) { last = now; paint(now / 1100f); tex.upload(); }
        return id;
    }

    private static void paint(float t) {
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                // flowing diagonal wave: deep black -> violet -> magenta
                double w = Math.sin(y * 0.42 - t * 2.2 + Math.sin(x * 0.34) * 1.1);
                double k = 0.35 + 0.65 * (0.5 + 0.5 * w);   // never fully black
                int r = (int) (16 + k * 170);
                int g = (int) (10 + k * 60);
                int b = (int) (30 + k * 225);
                img.setPixelABGR(x, y, 0xFF000000 | b << 16 | g << 8 | r);
            }
        }
        // hem line pulses along the bottom
        for (int x = 0; x < W; x++) {
            double k = 0.6 + 0.4 * Math.sin(x * 0.5 + t * 4);
            img.setPixelABGR(x, H - 1, 0xFF000000 | abgr((int) (200 * k), (int) (90 * k), (int) (255 * k)));
        }
        // pulsing X motifs on both cape faces
        float pulse = 0.55f + 0.45f * (float) Math.sin(t * 3);
        int mx = abgr((int) (240 * pulse + 15), (int) (190 * pulse + 40), 255);
        for (int c : new int[]{5, 16}) {
            for (int o = -4; o <= 4; o++) {
                plot(c + o, 11 + o, mx); plot(c + o, 11 - o, mx);
                plot(c + o + 1, 11 + o, mx); plot(c + o + 1, 11 - o, mx);   // 2px strokes
            }
        }
    }

    private static int abgr(int r, int g, int b) {
        return (Math.min(255, b) << 16) | (Math.min(255, g) << 8) | Math.min(255, r);
    }

    private static void plot(int x, int y, int abgr) {
        if (x >= 0 && x < W && y >= 0 && y < H) img.setPixelABGR(x, y, 0xFF000000 | abgr);
    }
}
