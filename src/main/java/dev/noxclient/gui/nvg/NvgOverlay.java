package dev.noxclient.gui.nvg;

import net.minecraft.client.Minecraft;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.system.MemoryStack;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.nanovg.NanoVGGL3.*;

/**
 * NanoVG finish pass — runs after {@link net.minecraft.client.gui.render.GuiRenderer#render()}
 * (see GuiRendererMixin). MC's GUI pipeline is deferred/batched, so any raw GL
 * inside extractRenderState lands *behind* everything; the only correct slot
 * for over-the-top polish (bloom, edge sheen, vignette, gradient strokes) is
 * post-render. Screens queue ops during extractRenderState; consumed once here.
 *
 * Op layout: [type, x, y, w, h, r, rgb, alpha, arg2, arg3]
 *   GLOW      arg2=feather
 *   SHEEN     -
 *   VIGNETTE  alpha=strength
 *   GSTROKE   arg2=rgb2 (gradient end), arg3=phase offset — animated aurora border
 *   COMET     arg2=rgb, arg3=period seconds — light streak sweeping the top edge
 *   GLASS     alpha=lift amount — top-light + bottom-shadow depth pass
 *   INNERTOP  alpha — crisp 1px inner top highlight (fakes real glass rim)
 */
public final class NvgOverlay {

    private static long ctx = 0;
    private static boolean failed = false;

    private static final int OP_GLOW = 0, OP_SHEEN = 1, OP_VIGNETTE = 2,
            OP_GSTROKE = 3, OP_COMET = 4, OP_GLASS = 5, OP_INNERTOP = 6;
    private static final List<float[]> ops = new ArrayList<>();
    private static long lastFrameNanos;
    private static float t; // seconds, drives all animation

    private NvgOverlay() {}

    private static boolean ensure() {
        if (ctx != 0 || failed) return ctx != 0;
        try {
            ctx = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
            if (ctx == 0) failed = true;
        } catch (Throwable th) {
            failed = true;
        }
        return ctx != 0;
    }

    /** Queue a soft accent bloom around a rect (drawn over the element edge). */
    public static void glow(float x, float y, float w, float h, float r, int rgb, float alpha, float feather) {
        ops.add(new float[]{OP_GLOW, x, y, w, h, r, rgb, alpha, feather, 0});
    }

    /** Queue a vertical sheen gradient inside a rect (top highlight). */
    public static void sheen(float x, float y, float w, float h, float r, int rgb, float alpha) {
        ops.add(new float[]{OP_SHEEN, x, y, w, h, r, rgb, alpha, 0, 0});
    }

    /** Queue a full-screen edge vignette (call once per frame, last op wins). */
    public static void vignette(float w, float h, float strength) {
        ops.add(new float[]{OP_VIGNETTE, 0, 0, w, h, 0, 0, strength, 0, 0});
    }

    /** Animated gradient border stroke — accent flows around the rounded edge. */
    public static void gradientStroke(float x, float y, float w, float h, float r,
                                      int rgbA, int rgbB, float alpha, float phase) {
        ops.add(new float[]{OP_GSTROKE, x, y, w, h, r, rgbA, alpha, rgbB, phase});
    }

    /** Light streak that sweeps across the top edge every `period` seconds. */
    public static void comet(float x, float y, float w, int rgb, float alpha, float period) {
        ops.add(new float[]{OP_COMET, x, y, w, 0, 0, rgb, alpha, period, 0});
    }

    /** Glass depth pass — top light lift + bottom inner shadow on one rect. */
    public static void glass(float x, float y, float w, float h, float r, float lift) {
        ops.add(new float[]{OP_GLASS, x, y, w, h, r, 0, lift, 0, 0});
    }

    /** Crisp 1px inner top highlight — the "glass rim" detail. */
    public static void innerTop(float x, float y, float w, float r, int rgb, float alpha) {
        ops.add(new float[]{OP_INNERTOP, x, y, w, 0, r, rgb, alpha, 0, 0});
    }

    public static void clear() {
        ops.clear();
    }

    public static boolean hasOps() {
        return !ops.isEmpty();
    }

    /** Called by GuiRendererMixin after GuiRenderer.render() submits the GUI batch. */
    public static void render() {
        if (ops.isEmpty()) return;
        if (!ensure()) { ops.clear(); return; }   // NVG dead → don't leak queued ops forever
        Minecraft mc = Minecraft.getInstance();
        float scale = (float) mc.getWindow().getGuiScale();
        long now = System.nanoTime();
        if (lastFrameNanos != 0) {
            t += Math.min(0.1f, (now - lastFrameNanos) / 1_000_000_000f);
        }
        lastFrameNanos = now;
        float pulseA = 0.55f + 0.45f * (float) Math.sin(t * 3.2);

        int guiW = mc.getWindow().getGuiScaledWidth();
        int guiH = mc.getWindow().getGuiScaledHeight();
        nvgBeginFrame(ctx, guiW, guiH, scale);
        try (MemoryStack st = MemoryStack.stackPush()) {
            NVGColor col = NVGColor.malloc(st);
            NVGPaint paint = NVGPaint.malloc(st);
            for (float[] o : ops) {
                int type = (int) o[0];
                float x = o[1], y = o[2], w = o[3], h = o[4], r = o[5];
                int rgb = (int) o[6];
                float a = o[7];
                switch (type) {
                    case OP_GLOW -> {
                        float feather = o[8];
                        nvgBoxGradient(ctx, x, y, w, h, r, feather,
                                rgba(col, rgb, a * (pulseA * 0.5f + 0.5f)), rgba(col, rgb, 0), paint);
                        nvgBeginPath(ctx);
                        nvgRoundedRect(ctx, x - feather, y - feather, w + feather * 2, h + feather * 2, r + feather);
                        nvgFillPaint(ctx, paint);
                        nvgFill(ctx);
                    }
                    case OP_SHEEN -> {
                        nvgLinearGradient(ctx, x, y, x, y + h,
                                rgba(col, rgb, a), rgba(col, rgb, 0), paint);
                        nvgBeginPath(ctx);
                        nvgRoundedRect(ctx, x, y, w, h, r);
                        nvgFillPaint(ctx, paint);
                        nvgFill(ctx);
                    }
                    case OP_VIGNETTE -> {
                        nvgBeginPath(ctx);
                        nvgRect(ctx, x, y, w, h);
                        float inset = Math.min(w, h) * 0.16f;
                        nvgRoundedRect(ctx, x + inset, y + inset, w - inset * 2, h - inset * 2, inset);
                        nvgPathWinding(ctx, NVG_HOLE);
                        rgba(col, 0x000000, a);
                        nvgFillColor(ctx, col);
                        nvgFill(ctx);
                    }
                    case OP_GSTROKE -> {
                        int rgbB = (int) o[8];
                        float phase = o[9];
                        // gradient vector orbits the rect centre → the accent
                        // colour visibly travels along the border
                        float cx = x + w / 2f, cy = y + h / 2f;
                        float ang = t * 1.4f + phase;
                        float gx = (float) Math.cos(ang) * w * 0.6f;
                        float gy = (float) Math.sin(ang) * h * 0.6f;
                        nvgLinearGradient(ctx, cx - gx, cy - gy, cx + gx, cy + gy,
                                rgba(col, rgb, a), rgba(col, rgbB, a), paint);
                        nvgBeginPath(ctx);
                        nvgRoundedRect(ctx, x + 0.5f, y + 0.5f, w - 1, h - 1, Math.max(0, r - 0.5f));
                        nvgStrokePaint(ctx, paint);
                        nvgStrokeWidth(ctx, 1.1f);
                        nvgStroke(ctx);
                    }
                    case OP_COMET -> {
                        float period = o[8];
                        // bright segment sweeping left→right along the top edge
                        float pos = (t % period) / period;
                        float head = x - w * 0.2f + pos * (w * 1.4f);
                        float span = w * 0.26f;
                        // soft-centred box gradient = comet head fading both ways
                        nvgBoxGradient(ctx, head - span, y - 1.5f, span * 2, 3f, span, 1.6f,
                                rgba(col, rgb, a), rgba(col, rgb, 0), paint);
                        nvgBeginPath(ctx);
                        nvgRect(ctx, x, y - 1.5f, w, 3f);
                        nvgFillPaint(ctx, paint);
                        nvgFill(ctx);
                    }
                    case OP_GLASS -> {
                        // top light lift
                        nvgLinearGradient(ctx, x, y, x, y + h * 0.55f,
                                rgba(col, 0xFFFFFF, a * 0.10f), rgba(col, 0xFFFFFF, 0), paint);
                        nvgBeginPath(ctx);
                        nvgRoundedRect(ctx, x, y, w, h, r);
                        nvgFillPaint(ctx, paint);
                        nvgFill(ctx);
                        // bottom inner shadow
                        nvgLinearGradient(ctx, x, y + h, x, y + h - h * 0.35f,
                                rgba(col, 0x000000, a * 0.22f), rgba(col, 0x000000, 0), paint);
                        nvgBeginPath(ctx);
                        nvgRoundedRect(ctx, x, y, w, h, r);
                        nvgFillPaint(ctx, paint);
                        nvgFill(ctx);
                    }
                    case OP_INNERTOP -> {
                        nvgBeginPath(ctx);
                        nvgRoundedRect(ctx, x + 0.5f, y + 0.5f, w - 1, Math.max(2, r * 2), r);
                        nvgScissor(ctx, x, y, w, 1.4f);
                        rgba(col, rgb, a);
                        nvgFillColor(ctx, col);
                        nvgFill(ctx);
                        nvgResetScissor(ctx);
                    }
                    default -> { }
                }
            }
        }
        nvgEndFrame(ctx);
        ops.clear();
    }

    private static NVGColor rgba(NVGColor c, int rgb, float a) {
        c.r(((rgb >> 16) & 0xFF) / 255f);
        c.g(((rgb >> 8) & 0xFF) / 255f);
        c.b((rgb & 0xFF) / 255f);
        c.a(Math.max(0f, Math.min(1f, a)));
        return c;
    }

    public static void close() {
        if (ctx != 0) { nvgDelete(ctx); ctx = 0; }
    }
}
