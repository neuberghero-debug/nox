package dev.noxclient.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * Antialiased rounded shapes. The extractor only knows axis-aligned integer
 * fills, which makes chord-math rounded corners look stair-stepped at any GUI
 * scale. Instead we rasterize a 64px rounded rect with 8x supersampled alpha
 * coverage per color (tiny cache — themes use few colors), blit the four
 * corner tiles through the bilinear-filtered texture pipeline and fill the
 * axis-aligned middle with plain rects. Result: smooth corners at any scale.
 */
public final class SmoothShapes {
    private static final int T = 64;       // texture size
    private static final int R = 22;       // corner radius inside the texture
    private static final int SS = 8;       // supersample factor
    private static final int MAX_CACHE = 160;
    /** LRU — evicts GL textures instead of leaking one per animation frame. */
    private static final Map<Long, Identifier> CACHE =
        new java.util.LinkedHashMap<>(64, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Long, Identifier> e) {
                if (size() <= MAX_CACHE) return false;
                Minecraft.getInstance().getTextureManager().release(e.getValue());
                return true;
            }
        };

    private SmoothShapes() {}

    /** Quantize each channel to /4 — kills the per-frame-interpolated-color
     *  cache explosion while staying visually identical. */
    private static long qkey(int argb, int kind) {
        // Geometry is independent of color. Tint the three reusable masks at
        // submission time instead of rasterizing/uploading every hover color.
        return kind;
    }

    private static Identifier tex(int argb, boolean ring) {
        argb = 0xFFFFFFFF;
        long key = qkey(argb, ring ? 1 : 0);
        Identifier id = CACHE.get(key);
        if (id != null) return id;
        NativeImage img = new NativeImage(T, T, true);
        int a0 = (argb >>> 24) & 0xFF, r0 = (argb >>> 16) & 0xFF,
            g0 = (argb >>> 8) & 0xFF, b0 = argb & 0xFF;
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            int inside = 0;
            for (int sy = 0; sy < SS; sy++) for (int sx = 0; sx < SS; sx++) {
                double px = x + (sx + 0.5) / SS, py = y + (sy + 0.5) / SS;
                if (insideRound(px, py) && (!ring || !insideRound2(px, py))) inside++;
            }
            int a = a0 * inside / (SS * SS);
            img.setPixel(x, y, (a << 24) | (r0 << 16) | (g0 << 8) | b0);
        }
        var dt = new DynamicTexture(() -> "nox_smooth", img);
        dt.upload();
        id = Identifier.fromNamespaceAndPath("noxclient",
            "smooth_" + Integer.toHexString(argb) + (ring ? "_r" : ""));
        Minecraft.getInstance().getTextureManager().register(id, dt);
        CACHE.put(key, id);
        return id;
    }

    /** Inside a rounded rect of size T with corner radius R. */
    private static boolean insideRound(double x, double y) {
        double cx = Math.max(R, Math.min(T - R, x));
        double cy = Math.max(R, Math.min(T - R, y));
        double dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= R * R;
    }

    /** Supersampled disc — smooth circles for toggles and dot accents. */
    public static void circle(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        if (r <= 0 || ((color >>> 24) & 0xFF) == 0) return;
        Identifier id = discTex(color);
        tinted(g, id, cx - r, cy - r, r * 2, r * 2, 0, 0, T, T, color);
    }

    private static Identifier discTex(int argb) {
        argb = 0xFFFFFFFF;
        long key = qkey(argb, 2);
        Identifier id = CACHE.get(key);
        if (id != null) return id;
        NativeImage img = new NativeImage(T, T, true);
        int a0 = (argb >>> 24) & 0xFF, r0 = (argb >>> 16) & 0xFF,
            g0 = (argb >>> 8) & 0xFF, b0 = argb & 0xFF;
        double c = T / 2.0 - 0.5, rr = T / 2.0;
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            int inside = 0;
            for (int sy = 0; sy < SS; sy++) for (int sx = 0; sx < SS; sx++) {
                double px = x + (sx + 0.5) / SS - c, py = y + (sy + 0.5) / SS - c;
                if (px * px + py * py <= rr * rr) inside++;
            }
            int a = a0 * inside / (SS * SS);
            img.setPixel(x, y, (a << 24) | (r0 << 16) | (g0 << 8) | b0);
        }
        var dt = new DynamicTexture(() -> "nox_disc", img);
        dt.upload();
        id = Identifier.fromNamespaceAndPath("noxclient",
            "disc_" + Integer.toHexString(argb));
        Minecraft.getInstance().getTextureManager().register(id, dt);
        CACHE.put(key, id);
        return id;
    }

    /** Inner rounded rect for ring borders (2px ring width at T scale). */
    private static boolean insideRound2(double x, double y) {
        if (x < 2 || x > T - 2 || y < 2 || y > T - 2) return false;
        double r = R - 2;
        double cx = Math.max(2 + r, Math.min(T - 2 - r, x));
        double cy = Math.max(2 + r, Math.min(T - 2 - r, y));
        double dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }

    /** Smooth rounded rect: 4 AA corner tiles + crisp axis fills. */
    public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        int cs = Math.min(Math.min(r, Math.min(w, h) / 2), 32);
        if (cs <= 1 || ((color >>> 24) & 0xFF) == 0) { g.fill(x, y, x + w, y + h, color); return; }
        Identifier id = tex(color, false);
        // corners (bilinear-filtered → smooth alpha edges)
        // blit(id, x0,y0,x1,y1, u0,u1,v0,v1) — end coords + normalized UVs
        float c = (float) R / T, e = 1f - c;
        tinted(g, id, x, y, cs, cs, 0, 0, R, R, color);
        tinted(g, id, x + w - cs, y, cs, cs, T - R, 0, R, R, color);
        tinted(g, id, x, y + h - cs, cs, cs, 0, T - R, R, R, color);
        tinted(g, id, x + w - cs, y + h - cs, cs, cs, T - R, T - R, R, R, color);
        // crisp middle (axis-aligned fills don't need AA)
        g.fill(x + cs, y, x + w - cs, y + h, color);
        g.fill(x, y + cs, x + cs, y + h - cs, color);
        g.fill(x + w - cs, y + cs, x + w, y + h - cs, color);
    }

    /** Single AA corner tile tinted `color` — quad: 0=TL 1=TR 2=BL 3=BR.
     * Used by Gui.vgrad so gradient surfaces get smooth corners too. */
    public static void corner(GuiGraphicsExtractor g, int x, int y, int cs, int quad, int color) {
        if (cs <= 1 || ((color >>> 24) & 0xFF) == 0) return;
        Identifier id = tex(color, false);
        switch (quad) {
            case 0 -> tinted(g, id, x, y, cs, cs, 0, 0, R, R, color);
            case 1 -> tinted(g, id, x, y, cs, cs, T - R, 0, R, R, color);
            case 2 -> tinted(g, id, x, y, cs, cs, 0, T - R, R, R, color);
            default -> tinted(g, id, x, y, cs, cs, T - R, T - R, R, R, color);
        }
    }

    /** Rounded rect with a smooth 1px border + inner fill. */
    public static void roundBorder(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                   int r, int borderColor, int fillColor) {
        int cs = Math.min(Math.min(r, Math.min(w, h) / 2), 32);
        if (cs <= 1) { g.fill(x, y, x + w, y + h, borderColor); g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fillColor); return; }
        Identifier ring = tex(borderColor, true);
        // ring corners = border — blit(id, x0,y0,x1,y1, u0,u1,v0,v1)
        float c = (float) R / T, e = 1f - c;
        tinted(g, ring, x, y, cs, cs, 0, 0, R, R, borderColor);
        tinted(g, ring, x + w - cs, y, cs, cs, T - R, 0, R, R, borderColor);
        tinted(g, ring, x, y + h - cs, cs, cs, 0, T - R, R, R, borderColor);
        tinted(g, ring, x + w - cs, y + h - cs, cs, cs, T - R, T - R, R, R, borderColor);
        // border edges
        g.fill(x + cs, y, x + w - cs, y + 1, borderColor);
        g.fill(x + cs, y + h - 1, x + w - cs, y + h, borderColor);
        g.fill(x, y + cs, x + 1, y + h - cs, borderColor);
        g.fill(x + w - 1, y + cs, x + w, y + h - cs, borderColor);
        // inner fill with inset corners (plain round, r-1)
        round(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, cs - 1), fillColor);
    }

    /** 26.3 public blit overload: destination size, source size, texture size, ARGB. */
    private static void tinted(GuiGraphicsExtractor g, Identifier id, int x, int y,
                               int w, int h, int u, int v, int uw, int vh, int color) {
        if (w <= 0 || h <= 0) return;
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, id,
            x, y, (float) u, (float) v, w, h, uw, vh, T, T, color);
    }
}
