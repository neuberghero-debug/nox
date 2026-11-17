package dev.noxclient.gui.nvg;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Minecraft's own particle sprites for the GUI seasonal FX.
 * Loaded lazily from the live ResourceManager (no assets shipped in the mod),
 * optionally RGB-tinted (MC leaf/generic sprites are grayscale — the game
 * tints them at spawn, so we do the same), then registered as NVG images
 * via {@link NvgOverlay#imageKey(byte[])}.
 *
 * Resolution path: key -> {@link NvgOverlay#imageHandle(int)} returns 0
 * until the GL upload ran, so callers must treat 0 as "not ready".
 */
public final class FxSprites {
    private static boolean tried;
    private static int[] SNOW, PETAL, LEAF_GREEN, LEAF_AUTUMN, SPARK, GLOW;

    public static void ensure() {
        if (tried) return;
        tried = true;
        try {
            SNOW = set("generic", 8, 0xFFFFFFFF);
            PETAL = set("cherry", 12, 0xFFFFFFFF);
            SPARK = set("glitter", 8, 0xFFFFFFFF);
            // leaf_* are grayscale -> tint like the biome pipeline does
            LEAF_GREEN = set("leaf", 12, 0xFF7CC74F, 0xFF5FA83C, 0xFF93D45E);
            LEAF_AUTUMN = set("leaf", 12, 0xFFD98A3D, 0xFFB8542E, 0xFFC7A83A, 0xFF9E6A3F);
            // soft warm blob for fireflies / embers
            GLOW = set("generic", 8, 0xFFFFE98A);
        } catch (Throwable t) {
            System.err.println("[nox] fxsprites: " + t);
        }
    }

    public static int[] snow()      { return SNOW == null ? Z : SNOW; }
    public static int[] petal()     { return PETAL == null ? Z : PETAL; }
    public static int[] leafGreen() { return LEAF_GREEN == null ? Z : LEAF_GREEN; }
    public static int[] leafAutumn(){ return LEAF_AUTUMN == null ? Z : LEAF_AUTUMN; }
    public static int[] spark()     { return SPARK == null ? Z : SPARK; }
    public static int[] glow()      { return GLOW == null ? Z : GLOW; }
    private static final int[] Z = new int[0];

    /* ---------- loading ---------- */

    /** Load `textures/particle/<base>_0..n-1.png`, cycling `tints` per index. */
    private static int[] set(String base, int n, int... tints) {
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            byte[] png = read("textures/particle/" + base + "_" + i + ".png");
            out[i] = png == null ? 0 : NvgOverlay.imageKey(tint(png, tints[i % tints.length]));
        }
        return out;
    }

    private static byte[] read(String path) {
        try {
            var rm = Minecraft.getInstance().getResourceManager();
            var res = rm.getResource(Identifier.fromNamespaceAndPath("minecraft", path));
            if (res.isEmpty()) return null;
            try (var in = res.get().open()) { return in.readAllBytes(); }
        } catch (Throwable t) { return null; }
    }

    /** Multiply-RGB tint on a PNG sprite (alpha untouched). Identity for white. */
    private static byte[] tint(byte[] png, int argb) {
        int r = (argb >>> 16) & 0xFF, g = (argb >>> 8) & 0xFF, b = argb & 0xFF;
        if (r >= 250 && g >= 250 && b >= 250) return png;
        try {
            BufferedImage im = ImageIO.read(new ByteArrayInputStream(png));
            if (im == null) return png;
            for (int y = 0; y < im.getHeight(); y++)
                for (int x = 0; x < im.getWidth(); x++) {
                    int p = im.getRGB(x, y);
                    im.setRGB(x, y, (p & 0xFF000000)
                        | ((p >>> 16 & 0xFF) * r / 255) << 16
                        | ((p >>> 8 & 0xFF) * g / 255) << 8
                        | ((p & 0xFF) * b / 255));
                }
            var bos = new ByteArrayOutputStream(png.length + 64);
            ImageIO.write(im, "png", bos);
            return bos.toByteArray();
        } catch (Throwable t) { return png; }
    }

    /** NVG handle for a sprite key — 0 until the upload pass ran. */
    public static int handle(int key) { return key == 0 ? 0 : NvgOverlay.imageHandle(key); }
}
