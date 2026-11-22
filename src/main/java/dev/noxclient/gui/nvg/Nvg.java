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

    /** Nested clip: intersects with the current scissor, restored by clipPop.
     * (nvgScissor REPLACES and nvgResetScissor clears everything — a bare
     * scissor+unScissor pair inside a clipped region leaks: later draws then
     * render unclipped. save+intersect+restore keeps the outer clip alive.) */
    public static void clipPush(long c, float x, float y, float w, float h) {
        nvgSave(c);
        nvgIntersectScissor(c, x, y, w, h);
    }
    public static void clipPop(long c) { nvgRestore(c); }

    /* ---------- images ---------- */
    /** Rounded image fill — handle from NvgOverlay.imageKey(byte[]). */
    public static void img(long c, MemoryStack st, int handle, float x, float y,
                           float w, float h, float r, float alpha) {
        NVGPaint p = nvgImagePattern(c, x, y, w, h, 0, handle, alpha, NVGPaint.malloc(st));
        nvgBeginPath(c); nvgRoundedRect(c, x, y, w, h, r);
        nvgFillPaint(c, p); nvgFill(c);
    }

    /** Sprite centered at (cx,cy), rotated `rot` radians — tumbling leaves, flakes. */
    public static void imgRot(long c, MemoryStack st, int handle, float cx, float cy,
                              float w, float h, float rot, float alpha) {
        nvgSave(c);
        nvgTranslate(c, cx, cy);
        nvgRotate(c, rot);
        NVGPaint p = nvgImagePattern(c, -w / 2, -h / 2, w, h, 0, handle, alpha,
            NVGPaint.malloc(st));
        nvgBeginPath(c); nvgRect(c, -w / 2, -h / 2, w, h);
        nvgFillPaint(c, p); nvgFill(c);
        nvgRestore(c);
    }

    /* ---------- material helpers (Obsidian pass) ---------- */

    /** 1px gradient stroke around a rounded rect (edge light, not a flat line). */
    public static void borderGrad(long c, MemoryStack st, float x, float y, float w, float h,
                                  float r, int top, int mid, int bot) {
        NVGPaint p = nvgLinearGradient(c, x, y, x, y + h,
            col(st, top), col(st, bot), NVGPaint.malloc(st));
        // three-stop look: stroke once with mid, overlay gradient stroke on top
        nvgBeginPath(c); nvgRoundedRect(c, x + .5f, y + .5f, w - 1, h - 1, Math.max(0, r - .5f));
        nvgStrokePaint(c, p); nvgStrokeWidth(c, 1f); nvgStroke(c);
        if (((mid >>> 24) & 0xFF) > 0) {
            NVGPaint p2 = nvgLinearGradient(c, x, y + h * .35f, x + w * .55f, y + h * .6f,
                col(st, mid), col(st, mid & 0xFFFFFF, 0), NVGPaint.malloc(st));
            nvgBeginPath(c); nvgRoundedRect(c, x + .5f, y + .5f, w - 1, h - 1, Math.max(0, r - .5f));
            nvgStrokePaint(c, p2); nvgStrokeWidth(c, 1f); nvgStroke(c);
        }
    }

    /** Inner top highlight — the 1px light line that makes fills read as material. */
    public static void insetHi(long c, MemoryStack st, float x, float y, float w, float r, int argb) {
        NVGPaint p = nvgLinearGradient(c, x, y, x + w, y,
            col(st, argb), col(st, argb & 0xFFFFFF, 0), NVGPaint.malloc(st));
        nvgBeginPath(c); nvgMoveTo(c, x + r, y + 1f); nvgLineTo(c, x + w - r, y + 1f);
        nvgStrokePaint(c, p); nvgStrokeWidth(c, 1f); nvgStroke(c);
    }

    /** Recessed well — darker fill + dark top edge + faint bottom lift (engraved). */
    public static void well(long c, MemoryStack st, float x, float y, float w, float h,
                            float r, int fill) {
        round(c, st, x, y, w, h, r, fill);
        nvgBeginPath(c); nvgMoveTo(c, x + r, y + 1f); nvgLineTo(c, x + w - r, y + 1f);
        nvgStrokeColor(c, col(st, 0x55000000)); nvgStrokeWidth(c, 1.5f); nvgStroke(c);
        nvgBeginPath(c); nvgMoveTo(c, x + r, y + h - 1f); nvgLineTo(c, x + w - r, y + h - 1f);
        nvgStrokeColor(c, col(st, 0x12FFFFFF)); nvgStrokeWidth(c, 1f); nvgStroke(c);
    }

    /** Letter-spaced text (caps micro-labels). */
    public static void textL(long c, MemoryStack st, String font, float size, float x, float y,
                             String s, int argb, float spacing, int align) {
        nvgFontFace(c, font);
        nvgFontSize(c, size * fontScale);
        nvgTextLetterSpacing(c, spacing);
        nvgTextAlign(c, align);
        nvgFillColor(c, col(st, argb));
        nvgText(c, x, y, s);
        nvgTextLetterSpacing(c, 0);
    }
    public static float textLW(long c, String font, float size, String s, float spacing) {
        nvgFontFace(c, font);
        nvgFontSize(c, size * fontScale);
        nvgTextLetterSpacing(c, spacing);
        float w = nvgTextBounds(c, 0, 0, s, (float[]) null);
        nvgTextLetterSpacing(c, 0);
        return w;
    }

    /** iOS toggle v2 — recessed off-track, accent gradient on + glow + lit knob. */
    public static void toggle2(long c, MemoryStack st, float x, float y, float w, float h,
                               float f, int off, int accA, int accB, float glow) {
        if (f > 0.001f) {
            NVGPaint p = nvgLinearGradient(c, x, y, x + w, y + h,
                col(st, accA), col(st, accB), NVGPaint.malloc(st));
            nvgBeginPath(c); nvgRoundedRect(c, x, y, w, h, h / 2);
            nvgFillPaint(c, p); nvgFill(c);
            if (glow > 0.05f)
                shadow(c, st, x, y + 1, w, h - 2, h / 2, 7 * glow, fade(accA, 0.45f * Math.min(1f, glow)));
            // inner top shade on the ON track (knob-carved look)
            nvgBeginPath(c); nvgMoveTo(c, x + h / 2, y + 1f); nvgLineTo(c, x + w - h / 2, y + 1f);
            nvgStrokeColor(c, col(st, 0x33000000, f)); nvgStrokeWidth(c, 1.4f); nvgStroke(c);
        }
        if (f < 0.999f) {
            /* recessed dark track + contour — DS.surface() blended into the row
             * card, so the OFF toggle looked like a floating white dot. */
            float a = 1f - f;
            round(c, st, x, y, w, h, h / 2, lerp(off, 0xFF0B0D13, .75f * a));
            nvgBeginPath(c); nvgRoundedRect(c, x + .5f, y + .5f, w - 1, h - 1, h / 2 - .5f);
            nvgStrokeColor(c, col(st, 0x2EFFFFFF, a)); nvgStrokeWidth(c, 1f); nvgStroke(c);
            nvgBeginPath(c); nvgMoveTo(c, x + h / 2, y + 1f); nvgLineTo(c, x + w - h / 2, y + 1f);
            nvgStrokeColor(c, col(st, 0x1F000000, a)); nvgStrokeWidth(c, 1.5f); nvgStroke(c);
        }
        float d = h - 5, kx = x + 2.5f + f * (w - h);
        shadow(c, st, kx, y + 1.5f, d, d, d / 2, 3.5f, 0x40000000);
        knob(c, st, kx + d / 2, y + h / 2, d / 2);
    }

    /** Radial-lit knob — top-left light, faint bottom shade ring. */
    public static void knob(long c, MemoryStack st, float cx, float cy, float r) {
        circle(c, st, cx, cy, r, 0xFFF2F3F7);
        NVGPaint p = nvgRadialGradient(c, cx - r * .3f, cy - r * .35f, r * .1f, r * 1.15f,
            col(st, 0x66FFFFFF), col(st, 0x1A000000), NVGPaint.malloc(st));
        nvgBeginPath(c); nvgCircle(c, cx, cy, r); nvgFillPaint(c, p); nvgFill(c);
    }

    /* ---------- glyph icons (1.5px line style in a unit box) ---------- */
    /** Named vector icon. Drawn in a 12-unit box centered at cx,cy, stroke ~1.5. */
    public static void glyph(long c, MemoryStack st, String name, float cx, float cy,
                             float s, int argb) {
        float u = s / 12f, x0 = cx - s / 2f, y0 = cy - s / 2f;
        float lw = Math.max(1.2f, 1.5f * u);
        switch (name) {
            case "sword" -> { // stroked 45° sword — reads cleanly at 11px
                // blade: hilt → tip (top-right), slightly thicker than other strokes
                line(c, st, x0 + 4.8f * u, y0 + 7.2f * u, x0 + 10.3f * u, y0 + 1.7f * u, lw * 1.9f, argb);
                // crossguard: perpendicular bar across the hilt
                line(c, st, x0 + 3.4f * u, y0 + 6.2f * u, x0 + 6.2f * u, y0 + 9.0f * u, lw * 1.7f, argb);
                // grip continuing down-left past the guard
                line(c, st, x0 + 4.6f * u, y0 + 8.4f * u, x0 + 2.7f * u, y0 + 10.3f * u, lw * 1.5f, argb);
                // pommel dot
                nvgBeginPath(c);
                nvgCircle(c, x0 + 2.1f * u, y0 + 10.7f * u, 1.05f * u);
                nvgFillColor(c, col(st, argb)); nvgFill(c);
            }
            case "crosshair" -> {
                ring(c, st, cx, cy, 4 * u, lw, argb);
                line(c, st, cx, y0 + .6f * u, cx, y0 + 3 * u, lw, argb);
                line(c, st, cx, y0 + 9 * u, cx, y0 + 11.4f * u, lw, argb);
                line(c, st, x0 + .6f * u, cy, x0 + 3 * u, cy, lw, argb);
                line(c, st, x0 + 9 * u, cy, x0 + 11.4f * u, cy, lw, argb);
            }
            case "bolt" -> { // stroked lightning — fill paths render as bbox here
                nvgBeginPath(c);
                nvgMoveTo(c, x0 + 7 * u, y0 + .8f * u); nvgLineTo(c, x0 + 3.4f * u, y0 + 6.6f * u);
                nvgLineTo(c, x0 + 6.2f * u, y0 + 6.6f * u); nvgLineTo(c, x0 + 5 * u, y0 + 11.2f * u);
                nvgLineTo(c, x0 + 8.8f * u, y0 + 5.2f * u); nvgLineTo(c, x0 + 6 * u, y0 + 5.2f * u);
                nvgClosePath(c);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw);
                nvgLineJoin(c, NVG_ROUND); nvgStroke(c);
            }
            case "eye" -> {
                nvgBeginPath(c);
                nvgMoveTo(c, x0 + .8f * u, cy);
                nvgBezierTo(c, x0 + 3.4f * u, y0 + 2.6f * u, x0 + 8.6f * u, y0 + 2.6f * u, x0 + 11.2f * u, cy);
                nvgBezierTo(c, x0 + 8.6f * u, y0 + 9.4f * u, x0 + 3.4f * u, y0 + 9.4f * u, x0 + .8f * u, cy);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw); nvgStroke(c);
                circle(c, st, cx, cy, 1.9f * u, argb);
            }
            case "cube" -> {
                nvgBeginPath(c); // iso hex outline + top/bottom splits
                nvgMoveTo(c, cx, y0 + .8f * u); nvgLineTo(c, x0 + 10.8f * u, y0 + 3.6f * u);
                nvgLineTo(c, x0 + 10.8f * u, y0 + 8.4f * u); nvgLineTo(c, cx, y0 + 11.2f * u);
                nvgLineTo(c, x0 + 1.2f * u, y0 + 8.4f * u); nvgLineTo(c, x0 + 1.2f * u, y0 + 3.6f * u);
                nvgClosePath(c);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw);
                nvgLineJoin(c, NVG_ROUND); nvgStroke(c);
                line(c, st, x0 + 1.2f * u, y0 + 3.6f * u, cx, cy, lw, argb);
                line(c, st, x0 + 10.8f * u, y0 + 3.6f * u, cx, cy, lw, argb);
                line(c, st, cx, cy, cx, y0 + 11.2f * u, lw, argb);
            }
            case "shield" -> {
                nvgBeginPath(c);
                nvgMoveTo(c, cx, y0 + .8f * u);
                nvgLineTo(c, x0 + 10.4f * u, y0 + 2.8f * u); nvgLineTo(c, x0 + 10.4f * u, y0 + 6.4f * u);
                nvgBezierTo(c, x0 + 10.4f * u, y0 + 9.2f * u, x0 + 7.6f * u, y0 + 10.6f * u, cx, y0 + 11.4f * u);
                nvgBezierTo(c, x0 + 4.4f * u, y0 + 10.6f * u, x0 + 1.6f * u, y0 + 9.2f * u, x0 + 1.6f * u, y0 + 6.4f * u);
                nvgLineTo(c, x0 + 1.6f * u, y0 + 2.8f * u); nvgClosePath(c);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw);
                nvgLineJoin(c, NVG_ROUND); nvgStroke(c);
            }
            case "wings" -> { // double chevron sweep = flight
                line(c, st, x0 + 1.5f * u, y0 + 8 * u, x0 + 6 * u, y0 + 3.4f * u, lw, argb);
                line(c, st, x0 + 6 * u, y0 + 3.4f * u, x0 + 10.5f * u, y0 + 8 * u, lw, argb);
                line(c, st, x0 + 2.4f * u, y0 + 10.6f * u, x0 + 6 * u, y0 + 7 * u, lw, argb);
                line(c, st, x0 + 6 * u, y0 + 7 * u, x0 + 9.6f * u, y0 + 10.6f * u, lw, argb);
            }
            case "chat" -> {
                nvgBeginPath(c); nvgRoundedRect(c, x0 + 1.2f * u, y0 + 1.8f * u, 9.6f * u, 7 * u, 2.2f * u);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw); nvgStroke(c);
                line(c, st, x0 + 4 * u, y0 + 8.8f * u, x0 + 4 * u, y0 + 11 * u, lw, argb);
                line(c, st, x0 + 4 * u, y0 + 11 * u, x0 + 6.6f * u, y0 + 8.8f * u, lw, argb);
            }
            case "note" -> { // music note
                line(c, st, x0 + 8.6f * u, y0 + 2 * u, x0 + 8.6f * u, y0 + 8.4f * u, lw, argb);
                line(c, st, x0 + 8.6f * u, y0 + 2 * u, x0 + 4.4f * u, y0 + 3.4f * u, lw, argb);
                line(c, st, x0 + 4.4f * u, y0 + 3.4f * u, x0 + 4.4f * u, y0 + 9.4f * u, lw, argb);
                circle(c, st, x0 + 3.4f * u, y0 + 9.4f * u, 1.6f * u, argb);
                circle(c, st, x0 + 7.6f * u, y0 + 8.4f * u, 1.6f * u, argb);
            }
            case "radar" -> {
                ring(c, st, cx, cy, 4.6f * u, lw * .8f, argb);
                ring(c, st, cx, cy, 2.6f * u, lw * .8f, argb);
                circle(c, st, cx, cy, .9f * u, argb);
                line(c, st, cx, cy, x0 + 9.4f * u, y0 + 2.6f * u, lw, argb);
            }
            case "flame" -> {
                nvgBeginPath(c);
                nvgMoveTo(c, cx, y0 + .8f * u);
                nvgBezierTo(c, x0 + 8.6f * u, y0 + 4.4f * u, x0 + 9.8f * u, y0 + 6.4f * u, x0 + 9.8f * u, y0 + 8.2f * u);
                nvgBezierTo(c, x0 + 9.8f * u, y0 + 10.4f * u, x0 + 8 * u, y0 + 11.4f * u, cx, y0 + 11.4f * u);
                nvgBezierTo(c, x0 + 4 * u, y0 + 11.4f * u, x0 + 2.2f * u, y0 + 10.4f * u, x0 + 2.2f * u, y0 + 8.2f * u);
                nvgBezierTo(c, x0 + 2.2f * u, y0 + 6.4f * u, x0 + 3.4f * u, y0 + 4.4f * u, cx, y0 + .8f * u);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw);
                nvgLineJoin(c, NVG_ROUND); nvgStroke(c);
            }
            case "heart" -> {
                nvgBeginPath(c);
                nvgMoveTo(c, cx, y0 + 10.6f * u);
                nvgBezierTo(c, x0 + 1 * u, y0 + 6.4f * u, x0 + 1 * u, y0 + 2.8f * u, x0 + 3.8f * u, y0 + 2.6f * u);
                nvgBezierTo(c, x0 + 4.9f * u, y0 + 2.6f * u, cx, y0 + 4.2f * u, cx, y0 + 4.2f * u);
                nvgBezierTo(c, cx, y0 + 4.2f * u, x0 + 7.1f * u, y0 + 2.6f * u, x0 + 8.2f * u, y0 + 2.6f * u);
                nvgBezierTo(c, x0 + 11 * u, y0 + 2.8f * u, x0 + 11 * u, y0 + 6.4f * u, cx, y0 + 10.6f * u);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw);
                nvgLineJoin(c, NVG_ROUND); nvgStroke(c);
            }
            case "star" -> star5(c, st, cx, cy, 5 * u, argb);
            case "person" -> {
                circle(c, st, cx, y0 + 3.6f * u, 2.2f * u, argb);
                nvgBeginPath(c);
                nvgMoveTo(c, x0 + 2 * u, y0 + 10.8f * u);
                nvgBezierTo(c, x0 + 2 * u, y0 + 7.6f * u, x0 + 4 * u, y0 + 6.6f * u, cx, y0 + 6.6f * u);
                nvgBezierTo(c, x0 + 8 * u, y0 + 6.6f * u, x0 + 10 * u, y0 + 7.6f * u, x0 + 10 * u, y0 + 10.8f * u);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw);
                nvgLineCap(c, NVG_ROUND); nvgStroke(c);
            }
            case "graph" -> {
                line(c, st, x0 + 1.6f * u, y0 + 10 * u, x0 + 4.4f * u, y0 + 6.6f * u, lw, argb);
                line(c, st, x0 + 4.4f * u, y0 + 6.6f * u, x0 + 7.2f * u, y0 + 8 * u, lw, argb);
                line(c, st, x0 + 7.2f * u, y0 + 8 * u, x0 + 10.4f * u, y0 + 3 * u, lw, argb);
            }
            case "diamond" -> {
                nvgBeginPath(c);
                nvgMoveTo(c, cx, y0 + 1 * u); nvgLineTo(c, x0 + 10.6f * u, cy);
                nvgLineTo(c, cx, y0 + 11 * u); nvgLineTo(c, x0 + 1.4f * u, cy); nvgClosePath(c);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw);
                nvgLineJoin(c, NVG_ROUND); nvgStroke(c);
            }
            case "pin" -> {
                nvgBeginPath(c);
                nvgArc(c, cx, y0 + 4.6f * u, 4 * u, (float) Math.toRadians(-160), (float) Math.toRadians(-20), NVG_CW);
                nvgBezierTo(c, x0 + 2.4f * u, y0 + 8.8f * u, x0 + 4 * u, y0 + 10 * u, cx, y0 + 11.2f * u);
                nvgBezierTo(c, x0 + 8 * u, y0 + 10 * u, x0 + 9.6f * u, y0 + 8.8f * u, x0 + 10 * u, y0 + 4.6f * u);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw); nvgStroke(c);
                circle(c, st, cx, y0 + 4.6f * u, 1.3f * u, argb);
            }
            case "snow" -> {
                for (int i = 0; i < 3; i++) {
                    double a = Math.PI * i / 3;
                    line(c, st, cx - (float) Math.cos(a) * 4.6f * u, cy - (float) Math.sin(a) * 4.6f * u,
                        cx + (float) Math.cos(a) * 4.6f * u, cy + (float) Math.sin(a) * 4.6f * u, lw, argb);
                }
            }
            case "drop" -> {
                nvgBeginPath(c);
                nvgMoveTo(c, cx, y0 + 1 * u);
                nvgBezierTo(c, x0 + 8.8f * u, y0 + 5.8f * u, x0 + 9.4f * u, y0 + 7.4f * u, x0 + 9.4f * u, y0 + 8.4f * u);
                nvgBezierTo(c, x0 + 9.4f * u, y0 + 10.4f * u, x0 + 7.8f * u, y0 + 11.2f * u, cx, y0 + 11.2f * u);
                nvgBezierTo(c, x0 + 4.2f * u, y0 + 11.2f * u, x0 + 2.6f * u, y0 + 10.4f * u, x0 + 2.6f * u, y0 + 8.4f * u);
                nvgBezierTo(c, x0 + 2.6f * u, y0 + 7.4f * u, x0 + 3.2f * u, y0 + 5.8f * u, cx, y0 + 1 * u);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw); nvgStroke(c);
            }
            case "rocket" -> {
                nvgBeginPath(c);
                nvgMoveTo(c, cx, y0 + .8f * u);
                nvgBezierTo(c, x0 + 8.6f * u, y0 + 2.6f * u, x0 + 8.6f * u, y0 + 5.6f * u, x0 + 8.2f * u, y0 + 8 * u);
                nvgLineTo(c, x0 + 3.8f * u, y0 + 8 * u);
                nvgBezierTo(c, x0 + 3.4f * u, y0 + 5.6f * u, x0 + 3.4f * u, y0 + 2.6f * u, cx, y0 + .8f * u);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw); nvgStroke(c);
                circle(c, st, cx, y0 + 4.6f * u, 1.2f * u, argb);
                line(c, st, x0 + 3.8f * u, y0 + 8 * u, x0 + 2.6f * u, y0 + 9.6f * u, lw, argb);
                line(c, st, x0 + 8.2f * u, y0 + 8 * u, x0 + 9.4f * u, y0 + 9.6f * u, lw, argb);
                line(c, st, cx, y0 + 8.6f * u, cx, y0 + 11 * u, lw, argb);
            }
            case "book" -> {
                nvgBeginPath(c); nvgRoundedRect(c, x0 + 2.4f * u, y0 + 1.6f * u, 7.2f * u, 8.8f * u, 1.2f * u);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw); nvgStroke(c);
                line(c, st, x0 + 4.4f * u, y0 + 4.4f * u, x0 + 7.6f * u, y0 + 4.4f * u, lw * .85f, argb);
                line(c, st, x0 + 4.4f * u, y0 + 6.4f * u, x0 + 7.6f * u, y0 + 6.4f * u, lw * .85f, argb);
            }
            case "camera" -> {
                nvgBeginPath(c); nvgRoundedRect(c, x0 + 1 * u, y0 + 3.2f * u, 10 * u, 6.6f * u, 1.6f * u);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw); nvgStroke(c);
                ring(c, st, cx, y0 + 6.5f * u, 2 * u, lw, argb);
                line(c, st, x0 + 4 * u, y0 + 3.2f * u, x0 + 5 * u, y0 + 1.8f * u, lw, argb);
                line(c, st, x0 + 5 * u, y0 + 1.8f * u, x0 + 7 * u, y0 + 1.8f * u, lw, argb);
                line(c, st, x0 + 7 * u, y0 + 1.8f * u, x0 + 8 * u, y0 + 3.2f * u, lw, argb);
            }
            case "flag" -> {
                line(c, st, x0 + 3 * u, y0 + 1.4f * u, x0 + 3 * u, y0 + 11 * u, lw, argb);
                nvgBeginPath(c);
                nvgMoveTo(c, x0 + 3 * u, y0 + 2 * u); nvgLineTo(c, x0 + 9.6f * u, y0 + 2 * u);
                nvgLineTo(c, x0 + 7.8f * u, y0 + 4 * u); nvgLineTo(c, x0 + 9.6f * u, y0 + 6 * u);
                nvgLineTo(c, x0 + 3 * u, y0 + 6 * u);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw);
                nvgLineJoin(c, NVG_ROUND); nvgStroke(c);
            }
            case "magnet" -> {
                nvgBeginPath(c);
                nvgMoveTo(c, x0 + 2.6f * u, y0 + 1.6f * u); nvgLineTo(c, x0 + 2.6f * u, y0 + 6.4f * u);
                nvgBezierTo(c, x0 + 2.6f * u, y0 + 9.6f * u, x0 + 4.4f * u, y0 + 10.8f * u, cx, y0 + 10.8f * u);
                nvgBezierTo(c, x0 + 7.6f * u, y0 + 10.8f * u, x0 + 9.4f * u, y0 + 9.6f * u, x0 + 9.4f * u, y0 + 6.4f * u);
                nvgLineTo(c, x0 + 9.4f * u, y0 + 1.6f * u);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw); nvgStroke(c);
                line(c, st, x0 + 2.6f * u, y0 + 4.4f * u, x0 + 5 * u, y0 + 4.4f * u, lw, argb);
                line(c, st, x0 + 7 * u, y0 + 4.4f * u, x0 + 9.4f * u, y0 + 4.4f * u, lw, argb);
            }
            case "clock" -> {
                ring(c, st, cx, cy, 4.8f * u, lw, argb);
                line(c, st, cx, cy, cx, y0 + 2.6f * u, lw, argb);
                line(c, st, cx, cy, x0 + 8.6f * u, cy, lw, argb);
            }
            case "skull" -> {
                nvgBeginPath(c);
                nvgArc(c, cx, y0 + 5.4f * u, 4.2f * u, (float) Math.toRadians(195), (float) Math.toRadians(-15), NVG_CW);
                nvgLineTo(c, x0 + 9.6f * u, y0 + 8.8f * u); nvgLineTo(c, x0 + 2.4f * u, y0 + 8.8f * u);
                nvgClosePath(c);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw); nvgStroke(c);
                circle(c, st, x0 + 4.4f * u, y0 + 5.4f * u, .9f * u, argb);
                circle(c, st, x0 + 7.6f * u, y0 + 5.4f * u, .9f * u, argb);
            }
            case "wave" -> { // sound wave
                line(c, st, x0 + 1.4f * u, cy, x0 + 3.4f * u, cy, lw, argb);
                line(c, st, x0 + 3.4f * u, cy, x0 + 5.4f * u, y0 + 3.4f * u, lw, argb);
                line(c, st, x0 + 5.4f * u, y0 + 3.4f * u, x0 + 7.4f * u, y0 + 8.6f * u, lw, argb);
                line(c, st, x0 + 7.4f * u, y0 + 8.6f * u, x0 + 9.4f * u, y0 + 4.6f * u, lw, argb);
                line(c, st, x0 + 9.4f * u, y0 + 4.6f * u, x0 + 10.6f * u, cy, lw, argb);
            }
            case "globe" -> {
                ring(c, st, cx, cy, 4.8f * u, lw, argb);
                nvgBeginPath(c); nvgEllipse(c, cx, cy, 2.2f * u, 4.8f * u);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw * .85f); nvgStroke(c);
                line(c, st, x0 + 1.2f * u, cy, x0 + 10.8f * u, cy, lw * .85f, argb);
            }
            case "key" -> {
                ring(c, st, x0 + 4 * u, y0 + 4 * u, 2.4f * u, lw, argb);
                line(c, st, x0 + 5.8f * u, y0 + 5.8f * u, x0 + 9.8f * u, y0 + 9.8f * u, lw, argb);
                line(c, st, x0 + 7.8f * u, y0 + 7.8f * u, x0 + 9 * u, y0 + 6.6f * u, lw, argb);
            }
            case "gift" -> {
                nvgBeginPath(c); nvgRoundedRect(c, x0 + 1.8f * u, y0 + 4.4f * u, 8.4f * u, 6.4f * u, 1 * u);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw); nvgStroke(c);
                line(c, st, cx, y0 + 4.4f * u, cx, y0 + 10.8f * u, lw, argb);
                line(c, st, x0 + 1.8f * u, y0 + 6.4f * u, x0 + 10.2f * u, y0 + 6.4f * u, lw * .8f, argb);
                nvgBeginPath(c); nvgMoveTo(c, cx, y0 + 4.4f * u);
                nvgBezierTo(c, x0 + 3 * u, y0 + 4.4f * u, x0 + 3 * u, y0 + 1.8f * u, x0 + 4.6f * u, y0 + 1.8f * u);
                nvgBezierTo(c, x0 + 5.8f * u, y0 + 1.8f * u, cx, y0 + 3 * u, cx, y0 + 4.4f * u);
                nvgBezierTo(c, cx, y0 + 3 * u, x0 + 6.2f * u, y0 + 1.8f * u, x0 + 7.4f * u, y0 + 1.8f * u);
                nvgBezierTo(c, x0 + 9 * u, y0 + 1.8f * u, x0 + 9 * u, y0 + 4.4f * u, cx, y0 + 4.4f * u);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw * .8f); nvgStroke(c);
            }
            case "donut" -> { // ring + hole + sprinkles — the DonutSMP mark
                ring(c, st, cx, cy, 4.8f * u, lw, argb);
                ring(c, st, cx, cy, 1.7f * u, lw * .8f, argb);
                circle(c, st, x0 + 3.8f * u, y0 + 3.2f * u, .65f * u, argb);
                circle(c, st, x0 + 8.8f * u, y0 + 4.2f * u, .65f * u, argb);
                circle(c, st, x0 + 7.8f * u, y0 + 8.8f * u, .65f * u, argb);
                circle(c, st, x0 + 3.2f * u, y0 + 8f * u, .65f * u, argb);
            }
            case "chest" -> {
                nvgBeginPath(c); nvgRoundedRect(c, x0 + 1.2f * u, y0 + 3.6f * u, 9.6f * u, 6.8f * u, 1.4f * u);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw); nvgStroke(c);
                line(c, st, x0 + 1.2f * u, y0 + 6 * u, x0 + 10.8f * u, y0 + 6 * u, lw, argb);
                nvgBeginPath(c); nvgRoundedRect(c, cx - .9f * u, y0 + 5f * u, 1.8f * u, 2.4f * u, .5f * u);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw * .85f); nvgStroke(c);
            }
            case "coin" -> { // ring + S from two arcs + stem
                ring(c, st, cx, cy, 4.8f * u, lw, argb);
                line(c, st, cx, y0 + 3f * u, cx, y0 + 9f * u, lw * .8f, argb);
                nvgBeginPath(c);
                nvgArc(c, cx, y0 + 4.6f * u, 1.7f * u, (float) Math.toRadians(20), (float) Math.toRadians(290), NVG_CW);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw * .85f); nvgStroke(c);
                nvgBeginPath(c);
                nvgArc(c, cx, y0 + 7.4f * u, 1.7f * u, (float) Math.toRadians(200), (float) Math.toRadians(350), NVG_CW);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw * .85f); nvgStroke(c);
            }
            case "bell" -> {
                nvgBeginPath(c);
                nvgMoveTo(c, x0 + 2.8f * u, y0 + 8.6f * u);
                nvgLineTo(c, x0 + 2.8f * u, y0 + 5.4f * u);
                nvgBezierTo(c, x0 + 2.8f * u, y0 + 3f * u, x0 + 4.4f * u, y0 + 1.6f * u, cx, y0 + 1.6f * u);
                nvgBezierTo(c, x0 + 7.6f * u, y0 + 1.6f * u, x0 + 9.2f * u, y0 + 3f * u, x0 + 9.2f * u, y0 + 5.4f * u);
                nvgLineTo(c, x0 + 9.2f * u, y0 + 8.6f * u);
                nvgLineTo(c, x0 + 10.4f * u, y0 + 9.6f * u);
                nvgLineTo(c, x0 + 1.6f * u, y0 + 9.6f * u);
                nvgClosePath(c);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw);
                nvgLineJoin(c, NVG_ROUND); nvgStroke(c);
                circle(c, st, cx, y0 + 10.6f * u, .9f * u, argb);
            }
            case "potion" -> {
                nvgBeginPath(c); nvgRoundedRect(c, x0 + 3f * u, y0 + 5f * u, 6f * u, 6.4f * u, 2f * u);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw); nvgStroke(c);
                line(c, st, x0 + 5f * u, y0 + 1.6f * u, x0 + 5f * u, y0 + 5f * u, lw, argb);
                line(c, st, x0 + 4.2f * u, y0 + 1.6f * u, x0 + 5.8f * u, y0 + 1.6f * u, lw, argb);
                line(c, st, x0 + 3.9f * u, y0 + 8.2f * u, x0 + 8.1f * u, y0 + 8.2f * u, lw * .85f, argb);
            }
            case "ghost" -> {
                nvgBeginPath(c);
                nvgMoveTo(c, x0 + 2.4f * u, y0 + 10.8f * u);
                nvgLineTo(c, x0 + 2.4f * u, y0 + 5.6f * u);
                nvgBezierTo(c, x0 + 2.4f * u, y0 + 2.8f * u, x0 + 4f * u, y0 + 1.4f * u, cx, y0 + 1.4f * u);
                nvgBezierTo(c, x0 + 8f * u, y0 + 1.4f * u, x0 + 9.6f * u, y0 + 2.8f * u, x0 + 9.6f * u, y0 + 5.6f * u);
                nvgLineTo(c, x0 + 9.6f * u, y0 + 10.8f * u);
                nvgLineTo(c, x0 + 7.8f * u, y0 + 9.6f * u);
                nvgLineTo(c, cx, y0 + 10.8f * u);
                nvgLineTo(c, x0 + 4.2f * u, y0 + 9.6f * u);
                nvgClosePath(c);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw);
                nvgLineJoin(c, NVG_ROUND); nvgStroke(c);
                circle(c, st, x0 + 4.6f * u, y0 + 5.4f * u, .8f * u, argb);
                circle(c, st, x0 + 7.4f * u, y0 + 5.4f * u, .8f * u, argb);
            }
            case "target" -> { // bullseye — bounty/range modules
                ring(c, st, cx, cy, 4.8f * u, lw, argb);
                ring(c, st, cx, cy, 2.6f * u, lw * .85f, argb);
                circle(c, st, cx, cy, .9f * u, argb);
            }
            case "pearl" -> { // ender pearl — circle + inner crescent
                ring(c, st, cx, cy, 4.8f * u, lw, argb);
                nvgBeginPath(c);
                nvgArc(c, cx, cy, 2.8f * u, (float) Math.toRadians(150), (float) Math.toRadians(300), NVG_CW);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw * .8f); nvgStroke(c);
            }
            case "tnt" -> { // charge block + lit fuse spark
                nvgBeginPath(c); nvgRoundedRect(c, x0 + 2f * u, y0 + 4f * u, 8f * u, 6.4f * u, 1f * u);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw); nvgStroke(c);
                line(c, st, x0 + 2f * u, y0 + 7.2f * u, x0 + 10f * u, y0 + 7.2f * u, lw, argb);
                line(c, st, cx, y0 + 4f * u, cx, y0 + 2f * u, lw, argb);
                circle(c, st, cx + 1.3f * u, y0 + 1.6f * u, .95f * u, argb);
            }
            default -> { // hollow diamond — unmapped modules still get a visible mark
                nvgBeginPath(c);
                nvgMoveTo(c, cx, y0 + 2 * u); nvgLineTo(c, x0 + 9.6f * u, cy);
                nvgLineTo(c, cx, y0 + 10 * u); nvgLineTo(c, x0 + 2.4f * u, cy);
                nvgClosePath(c);
                nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, lw);
                nvgLineJoin(c, NVG_ROUND); nvgStroke(c);
            }
        }
    }

    private static void star5(long c, MemoryStack st, float cx, float cy, float r, int argb) {
        nvgBeginPath(c);
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5 - Math.PI / 2;
            float rr = i % 2 == 0 ? r : r * .45f;
            float px = cx + (float) Math.cos(a) * rr, py = cy + (float) Math.sin(a) * rr;
            if (i == 0) nvgMoveTo(c, px, py); else nvgLineTo(c, px, py);
        }
        nvgClosePath(c);
        nvgStrokeColor(c, col(st, argb)); nvgStrokeWidth(c, Math.max(1.2f, r * .3f));
        nvgLineJoin(c, NVG_ROUND); nvgStroke(c);   // outlined — filled paths render as bbox
    }

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
