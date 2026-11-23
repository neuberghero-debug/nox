package dev.noxclient.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Video-frame sky: plays assets/noxclient/sky/*.png as a looping animated
 * background (≈15fps), cover-cropped to the screen. Frames ship in the jar —
 * produced from any clip via tools/video-to-sky.sh. Returns false when no
 * frames exist so callers can fall back to the procedural Gargantua sky.
 */
public final class AnimatedSky {
    private record Frame(Identifier id, int w, int h) {}

    private static final List<Frame> FRAMES = new ArrayList<>();
    private static boolean scanned;
    private static long frameMs = 66; // ~15fps — smoother, 88 frames ≈ 5.8s loop

    public static boolean render(GuiGraphicsExtractor g, int w, int h) {
        return render(g, 0, 0, w, h);
    }

    public static boolean render(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        ensureLoaded();
        if (FRAMES.isEmpty()) return false;
        Frame f = FRAMES.get((int) ((System.currentTimeMillis() / frameMs) % FRAMES.size()));
        // cover-fit: crop the larger axis in texel space
        float sa = (float) w / h, ta = (float) f.w / f.h;
        float u = 0, v = 0, sw = f.w, sh = f.h;
        if (ta > sa) { sw = f.h * sa; u = (f.w - sw) / 2f; }
        else { sh = f.w / sa; v = (f.h - sh) / 2f; }
        g.blit(RenderPipelines.GUI_TEXTURED, f.id, x, y, u, v, w, h, (int) sw, (int) sh, f.w, f.h);
        return true;
    }

    private static void ensureLoaded() {
        if (scanned) return;
        scanned = true;
        Minecraft mc = Minecraft.getInstance();
        var rm = mc.getResourceManager();
        var found = rm.listResources("sky",
            id -> id.getNamespace().equals("noxclient")
                && (id.getPath().endsWith(".png") || id.getPath().endsWith(".jpg")));
        found.keySet().stream().sorted(Comparator.comparing(Identifier::getPath)).forEach(id -> {
            try (var in = rm.getResourceOrThrow(id).open()) {
                // the DynamicTexture owns the image from here on — closing it
                // eagerly relied on register() uploading synchronously
                NativeImage img = id.getPath().endsWith(".jpg")
                    ? jpeg(in) : NativeImage.read(in);
                try {
                    Identifier reg = Identifier.fromNamespaceAndPath("noxclient", "skyframe/" + FRAMES.size());
                    mc.getTextureManager().register(reg,
                        new DynamicTexture(() -> "nox_sky_" + FRAMES.size(), img));
                    FRAMES.add(new Frame(reg, img.getWidth(), img.getHeight()));
                } catch (Throwable t) { img.close(); throw t; }
            } catch (Throwable ignored) {}
        });
    }

    /** JPEG → NativeImage via ImageIO (JPEG quality >> PNG8 at the same size). */
    private static NativeImage jpeg(java.io.InputStream in) throws Exception {
        var buf = javax.imageio.ImageIO.read(in);
        NativeImage img = new NativeImage(buf.getWidth(), buf.getHeight(), false);
        for (int y = 0; y < buf.getHeight(); y++)
            for (int x = 0; x < buf.getWidth(); x++) {
                int p = buf.getRGB(x, y);                     // ARGB
                img.setPixelABGR(x, y, p & 0xFF00FF00
                    | (p & 0xFF) << 16 | (p >> 16 & 0xFF));   // swap R/B
            }
        return img;
    }
}
