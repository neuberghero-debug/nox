package dev.noxclient.gui.nvg;

import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Vector canvas helpers used inside {@link NvgOverlay#op} draw lambdas.
 * Everything is real NVG — true AA, gradients, blur, arcs — no texture stairs.
 *
 * Colors are ARGB ints (0xAARRGGBB) like the rest of the codebase.
 * Fonts registered in NvgOverlay: "inter", "inter_sb", "inter_b", "mono".
 */
public final class Nvg {
    private Nvg() {}

    /* ---------- colors ---------- */
    public static NVGColor col(MemoryStack st, int argb) {
        return col(st, argb, 1f);
    }
    public static NVGColor col(MemoryStack st, int argb, float mulA) {
        NVGColor c = NVGColor.malloc(st);
        c.r(((argb >>> 16) & 0xFF) / 255f);
        c.g(((argb >>> 8) & 0xFF) / 255f);
        c.b((argb & 0xFF) / 255f);
        c.a(((argb >>> 24) & 0xFF) / 255f * mulA);
        return c;
    }

    /* ---------- shapes ---------- */
    public static void rect(long c, MemoryStack st, float x, float y, float w, float h, int argb) {
        nvgBeginPath(c); nvgRect(c, x, y, w, h);
        nvgFillColor(c, col(st, argb)); nvgFill(c);
    }

    public static void round(long c, MemoryStack st, float x, float y, float w, float h,
                             float r, int argb) {
        nvgBeginPath(c); nvgRoundedRect(c, x, y, w, h, r);
        nvgFillColor(c, col(st, argb)); nvgFill(c);
    }

    /** Filled + 1px rounded border in one call. */
    public static void roundB(long c, MemoryStack st, float x, float y, float w, float h,
                              float r, int fill, int border) {
        nvgBeginPath(c); nvgRoundedRect(c, x, y, w, h, r);
        nvgFillColor(c, col(st, fill)); nvgFill(c);
        nvgBeginPath(c); nvgRoundedRect(c, x + .5f, y + .5f, w - 1, h - 1, Math.max(0, r - .5f));
        nvgStrokeColor(c, col(st, border)); nvgStrokeWidth(c, 1f); nvgStroke(c);
    }

    /** Horizontal gradient inside a rounded rect. */
    public static void hgrad(long c, MemoryStack st, float x, float y, float w, float h,
                             float r, int argbL, int argbR) {
        NVGPaint p = nvgLinearGradient(c, x, y, x + w, y,
            col(st, argbL), col(st, argbR), NVGPaint.malloc(st));
        nvgBeginPath(c); nvgRoundedRect(c, x, y, w, h, r);
        nvgFillPaint(c, p); nvgFill(c);
    }

    public static void vgrad(long c, MemoryStack st, float x, float y, float w, float h,
                             float r, int argbT, int argbB) {
        NVGPaint p = nvgLinearGradient(c, x, y, x, y + h,
            col(st, argbT), col(st, argbB), NVGPaint.malloc(st));
        nvgBeginPath(c); nvgRoundedRect(c, x, y, w, h, r);
        nvgFillPaint(c, p); nvgFill(c);
    }

    public static void circle(long c, MemoryStack st, float cx, float cy, float r, int argb) {
        nvgBeginPath(c); nvgCircle(c, cx, cy, r);
        nvgFillColor(c, col(st, argb)); nvgFill(c);
    }

    public static void ring(long c, MemoryStack st, float cx, float cy, float r,
                            float width, int argb) {
        nvgBeginPath(c); nvgCircle(c, cx, cy, r);
        nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, width); nvgStroke(c);
    }

    /** Stroke a polyline — real AA, no stair steps. */
    public static void poly(long c, MemoryStack st, float[] pts, float width, int argb) {
        nvgBeginPath(c);
        nvgMoveTo(c, pts[0], pts[1]);
        for (int i = 2; i < pts.length; i += 2) nvgLineTo(c, pts[i], pts[i + 1]);
        nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, width);
        nvgLineCap(c, NVG_ROUND); nvgLineJoin(c, NVG_ROUND);
        nvgStroke(c);
    }

    /** Filled area under a polyline (gradient fade) — sparkline body. */
    public static void area(long c, MemoryStack st, float[] pts, float baseY,
                            float minY, int rgb, float aTop) {
        NVGPaint p = nvgLinearGradient(c, 0, minY, 0, baseY,
            col(st, 0xFF000000 | (rgb & 0xFFFFFF), aTop), col(st, 0, 0), NVGPaint.malloc(st));
        nvgBeginPath(c);
        nvgMoveTo(c, pts[0], baseY);
        for (int i = 0; i < pts.length; i += 2) nvgLineTo(c, pts[i], pts[i + 1]);
        nvgLineTo(c, pts[pts.length - 2], baseY);
        nvgClosePath(c);
        nvgFillPaint(c, p); nvgFill(c);
    }

    /** Arc segment (ring band), angles in degrees — 0 = top, clockwise. */
    public static void arc(long c, MemoryStack st, float cx, float cy, float r, float width,
                           float a0, float a1, int argb) {
        float rad0 = (float) Math.toRadians(a0 - 90), rad1 = (float) Math.toRadians(a1 - 90);
        nvgBeginPath(c);
        nvgArc(c, cx, cy, r, rad0, rad1, NVG_CW);
        nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, width);
        nvgLineCap(c, NVG_ROUND);
        nvgStroke(c);
    }

    /** Line x0,y0 → x1,y1. */
    public static void line(long c, MemoryStack st, float x0, float y0, float x1, float y1,
                            float width, int argb) {
        nvgBeginPath(c); nvgMoveTo(c, x0, y0); nvgLineTo(c, x1, y1);
        nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, width);
        nvgLineCap(c, NVG_ROUND);
        nvgStroke(c);
    }

    /** Soft drop shadow (box gradient blur). */
    public static void shadow(long c, MemoryStack st, float x, float y, float w, float h,
                              float r, float feather, int argb) {
        NVGPaint p = nvgBoxGradient(c, x, y, w, h, r, feather,
            col(st, argb), col(st, argb & 0xFFFFFF, 0), NVGPaint.malloc(st));
        nvgBeginPath(c);
        nvgRoundedRect(c, x - feather, y - feather, w + feather * 2, h + feather * 2, r + feather);
        nvgFillPaint(c, p); nvgFill(c);
    }

    /* ---------- text ---------- */
    /** Global text scale — set once per frame from UICustom.fontScale so text
     *  AND measured widths (textW) scale together and layouts stay exact. */
    public static float fontScale = 1f;

    public static void text(long c, MemoryStack st, String font, float size, float x, float y,
                            String s, int argb) {
        text(c, st, font, size, x, y, s, argb, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
    }

    public static void text(long c, MemoryStack st, String font, float size, float x, float y,
                            String s, int argb, int align) {
        nvgFontFace(c, font);
        nvgFontSize(c, size * fontScale);
        nvgTextAlign(c, align);
        nvgFillColor(c, col(st, argb));
        nvgText(c, x, y, s);
    }

    public static float textW(long c, String font, float size, String s) {
        nvgFontFace(c, font);
        nvgFontSize(c, size * fontScale);
        return nvgTextBounds(c, 0, 0, s, (float[]) null);
    }

    /* ---------- scissor ---------- */
    public static void scissor(long c, float x, float y, float w, float h) {
        nvgScissor(c, x, y, w, h);
    }
    public static void unScissor(long c) { nvgResetScissor(c); }

    /* ---------- composite helpers ---------- */
    /** iOS toggle — pill track + white knob, `f` = 0..1 travel. */
    public static void toggle(long c, MemoryStack st, float x, float y, float w, float h,
                              float f, int track, int accent) {
        int tc = lerp(track, accent, f);
        round(c, st, x, y, w, h, h / 2, tc);
        float d = h - 4, kx = x + 2 + f * (w - h);
        shadow(c, st, kx, y + 1, d, d, d / 2, 3, 0x33000000);
        circle(c, st, kx + d / 2, y + h / 2, d / 2, 0xFFFFFFFF);
    }

    /** 16px checkbox — pink check when on. */
    public static void check(long c, MemoryStack st, float x, float y, float s,
                             boolean on, int accent, int off, int border) {
        roundB(c, st, x, y, s, s, 4, on ? accent : off, on ? accent : border);
        if (on) {
            line(c, st, x + s * .24f, y + s * .55f, x + s * .44f, y + s * .74f, 1.6f, 0xFFFFFFFF);
            line(c, st, x + s * .44f, y + s * .74f, x + s * .78f, y + s * .30f, 1.6f, 0xFFFFFFFF);
        }
    }

    public static int lerp(int a, int b, float t) {
        int ra = (a >>> 16) & 0xFF, ga = (a >>> 8) & 0xFF, ba = a & 0xFF, aa = a >>> 24;
        int rb = (b >>> 16) & 0xFF, gb = (b >>> 8) & 0xFF, bb = b & 0xFF, ab = b >>> 24;
        return ((int) (aa + (ab - aa) * t) << 24) | ((int) (ra + (rb - ra) * t) << 16)
            | ((int) (ga + (gb - ga) * t) << 8) | (int) (ba + (bb - ba) * t);
    }
    public static int fade(int argb, float f) {
        return ((int) (((argb >>> 24) & 0xFF) * f) << 24) | (argb & 0xFFFFFF);
    }
}
