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
 * for over-the-top polish (bloom, edge sheen, vignette) is post-render.
 * Screens queue ops during extractRenderState; they are consumed here once.
 */
public final class NvgOverlay {

    private static long ctx = 0;
    private static boolean failed = false;

    private static final int OP_GLOW = 0, OP_SHEEN = 1, OP_VIGNETTE = 2;
    private static final List<float[]> ops = new ArrayList<>();
    private static long lastFrameNanos;
    private static float pulse; // 0..1 animated accent pulse

    private NvgOverlay() {}

    private static boolean ensure() {
        if (ctx != 0 || failed) return ctx != 0;
        try {
            ctx = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
            if (ctx == 0) failed = true;
        } catch (Throwable t) {
            failed = true;
        }
        return ctx != 0;
    }

    /** Queue a soft accent bloom around a rect (drawn over the element edge). */
    public static void glow(float x, float y, float w, float h, float r, int rgb, float alpha, float feather) {
        ops.add(new float[]{OP_GLOW, x, y, w, h, r, rgb, alpha, feather});
    }

    /** Queue a vertical sheen gradient inside a rect (top highlight). */
    public static void sheen(float x, float y, float w, float h, float r, int rgb, float alpha) {
        ops.add(new float[]{OP_SHEEN, x, y, w, h, r, rgb, alpha, 0});
    }

    /** Queue a full-screen edge vignette (call once per frame, last op wins). */
    public static void vignette(float w, float h, float strength) {
        ops.add(new float[]{OP_VIGNETTE, 0, 0, w, h, 0, 0, strength, 0});
    }

    public static void clear() {
        ops.clear();
    }

    public static boolean hasOps() {
        return !ops.isEmpty();
    }

    /** Called by GuiRendererMixin after GuiRenderer.render() submits the GUI batch. */
    public static void render() {
        if (ops.isEmpty() || !ensure()) return;
        Minecraft mc = Minecraft.getInstance();
        float scale = (float) mc.getWindow().getGuiScale();
        long now = System.nanoTime();
        if (lastFrameNanos != 0) {
            float dt = Math.min(0.1f, (now - lastFrameNanos) / 1_000_000_000f);
            pulse += dt * 1.6f;
        }
        lastFrameNanos = now;
        float pulseA = 0.55f + 0.45f * (float) Math.sin(pulse * 2.0);

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
                float a = o[7] * (type == OP_GLOW ? pulseA * 0.5f + 0.5f : 1f);
                if (type == OP_GLOW) {
                    float feather = o[8];
                    nvgBoxGradient(ctx, x, y, w, h, r, feather,
                            rgba(col, rgb, a), rgba(col, rgb, 0), paint);
                    nvgBeginPath(ctx);
                    nvgRoundedRect(ctx, x - feather, y - feather, w + feather * 2, h + feather * 2, r + feather);
                    nvgFillPaint(ctx, paint);
                    nvgFill(ctx);
                } else if (type == OP_SHEEN) {
                    nvgLinearGradient(ctx, x, y, x, y + h,
                            rgba(col, rgb, a), rgba(col, rgb, 0), paint);
                    nvgBeginPath(ctx);
                    nvgRoundedRect(ctx, x, y, w, h, r);
                    nvgFillPaint(ctx, paint);
                    nvgFill(ctx);
                } else { // vignette
                    nvgBeginPath(ctx);
                    nvgRect(ctx, x, y, w, h);
                    float inset = Math.min(w, h) * 0.16f;
                    nvgRoundedRect(ctx, x + inset, y + inset, w - inset * 2, h - inset * 2, inset);
                    nvgPathWinding(ctx, NVG_HOLE);
                    rgba(col, 0x000000, a);
                    nvgFillColor(ctx, col);
                    nvgFill(ctx);
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
