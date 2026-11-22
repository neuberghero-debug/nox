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
 * NanoVG finish pass — runs right before GpuSurface.blitFromTexture copies the
 * main render target to the window surface (see PresentMixin). At that point
 * every renderpearl submit for the frame has completed, so the direct-GL write
 * into the RT color texture is the last modification before the blit — no later
 * pass can overwrite it (the earlier GuiRenderer-TAIL slot raced with trailing
 * submits and produced visible flicker). Screens queue ops during
 * extractRenderState; consumed once here.
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
            OP_GSTROKE = 3, OP_COMET = 4, OP_GLASS = 5, OP_INNERTOP = 6, OP_WASH = 7;
    /** Single FIFO op stream — typed helpers AND generic draws share it so
     * layering always follows call order (backdrop < window < fx < text). */
    private static final List<java.util.function.BiConsumer<Long, MemoryStack>> generic = new ArrayList<>();
    private static void typed(float[] o) { generic.add((c, st) -> drawTyped(c, st, o)); }
    private static long lastFrameNanos;
    private static float t; // seconds, drives all animation

    private NvgOverlay() {}

    public static long ctx() { return ctx; }
    public static float time() { return t; }

    /** Queue a generic NVG draw — executed inside nvgBeginFrame/EndFrame.
     * The op receives (ctx, MemoryStack) — use it for NVGColor/NVGPaint allocs. */
    public static void op(java.util.function.BiConsumer<Long, MemoryStack> draw) {
        generic.add(draw);
    }

    private static boolean fontsLoaded;

    private static boolean ensure() {
        if (ctx != 0 || failed) return ctx != 0;
        try {
            ctx = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
            if (ctx == 0) failed = true;
            else System.err.println("[nox] nvg ctx created " + ctx);
        } catch (Throwable th) {
            failed = true;
            System.err.println("[nox] nvg create FAILED: " + th);
        }
        return ctx != 0;
    }

    /** Load the embedded TTFs into the NVG context once — Inter reg/sb/b + JetBrains Mono. */
    static void ensureFonts() {
        if (fontsLoaded || ctx == 0) return;
        fontsLoaded = true;
        loadFont("inter", "font/inter_regular.ttf");
        loadFont("inter_sb", "font/inter_semibold.ttf");
        loadFont("inter_b", "font/inter_bold.ttf");
        loadFont("mono", "font/jetbrains_mono.ttf");
    }

    private static final java.util.List<java.nio.ByteBuffer> FONT_BUFS = new ArrayList<>();

    private static void loadFont(String name, String path) {
        try {
            var id = net.minecraft.resources.Identifier.fromNamespaceAndPath("noxclient", path);
            try (var in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(id).open()) {
                byte[] b = in.readAllBytes();
                var buf = org.lwjgl.BufferUtils.createByteBuffer(b.length);
                buf.put(b).flip();
                FONT_BUFS.add(buf);          // must stay alive — NVG references it
                int fh = nvgCreateFontMem(ctx, name, buf, false);
                System.err.println("[nox] nvg font " + name + " handle=" + fh + " bytes=" + b.length);
            }
        } catch (Throwable th) {
            System.err.println("[nox] nvg font load failed " + path + ": " + th);
        }
    }

    /** Queue a soft accent bloom around a rect (drawn over the element edge). */
    public static void glow(float x, float y, float w, float h, float r, int rgb, float alpha, float feather) {
        typed(new float[]{OP_GLOW, x, y, w, h, r, rgb, alpha, feather, 0});
    }

    /** Queue a vertical sheen gradient inside a rect (top highlight). */
    public static void sheen(float x, float y, float w, float h, float r, int rgb, float alpha) {
        typed(new float[]{OP_SHEEN, x, y, w, h, r, rgb, alpha, 0, 0});
    }

    /** Queue a full-screen edge vignette (call once per frame, last op wins). */
    public static void vignette(float w, float h, float strength) {
        typed(new float[]{OP_VIGNETTE, 0, 0, w, h, 0, 0, strength, 0, 0});
    }

    /** Animated gradient border stroke — accent flows around the rounded edge. */
    public static void gradientStroke(float x, float y, float w, float h, float r,
                                      int rgbA, int rgbB, float alpha, float phase) {
        typed(new float[]{OP_GSTROKE, x, y, w, h, r, rgbA, alpha, rgbB, phase});
    }

    /** Light streak that sweeps across the top edge every `period` seconds. */
    public static void comet(float x, float y, float w, int rgb, float alpha, float period) {
        typed(new float[]{OP_COMET, x, y, w, 0, 0, rgb, alpha, period, 0});
    }

    /** Glass depth pass — top light lift + bottom inner shadow on one rect. */
    public static void glass(float x, float y, float w, float h, float r, float lift) {
        typed(new float[]{OP_GLASS, x, y, w, h, r, 0, lift, 0, 0});
    }

    /** Crisp 1px inner top highlight — the "glass rim" detail. */
    public static void innerTop(float x, float y, float w, float r, int rgb, float alpha) {
        typed(new float[]{OP_INNERTOP, x, y, w, 0, r, rgb, alpha, 0, 0});
    }

    /** Diagonal chromatic wash across a rect — the "tinted glass pane" that
     * makes Frost/Prism visibly change the WINDOW, not just the backdrop. */
    public static void wash(float x, float y, float w, float h, float r,
                            int rgbA, int rgbB, float alpha) {
        typed(new float[]{OP_WASH, x, y, w, h, r, rgbA, alpha, rgbB, 0});
    }

    public static void clear() {
        generic.clear();
    }

    public static boolean hasOps() {
        return !generic.isEmpty();
    }

    public static boolean hasWork() {
        return !generic.isEmpty();
    }

    /** Screenshot the REAL backbuffer (includes NVG overlay — MC's Screenshot
     * only reads the offscreen mainRenderTarget and misses this pass). */
    private static volatile java.nio.file.Path pendingGrab;
    public static void grabFrame(java.nio.file.Path out) { pendingGrab = out; }

    /* ---------- embedded images (icon, logos) ---------- */
    /** Queued PNG decodes — must run on the GL thread inside render(). */
    private static final java.util.Map<Integer, byte[]> pendingImgs = new java.util.HashMap<>();
    private static final java.util.Map<Integer, Integer> imgHandles = new java.util.HashMap<>();
    private static final java.util.List<java.nio.ByteBuffer> IMG_BUFS = new ArrayList<>();

    /** Register PNG bytes once -> stable key; resolves to an NVG image handle
     * via {@link #imageHandle(int)} after the next render pass. */
    public static int imageKey(byte[] png) {
        int k = java.util.Arrays.hashCode(png);
        if (!imgHandles.containsKey(k) && !pendingImgs.containsKey(k))
            pendingImgs.put(k, png.clone());
        return k;
    }
    /** NVG image handle for a key — 0 until the create ran inside a frame. */
    public static int imageHandle(int key) { return imgHandles.getOrDefault(key, 0); }

    private static void drainPendingImages() {
        if (pendingImgs.isEmpty()) return;
        for (var e : pendingImgs.entrySet()) {
            try {
                var buf = org.lwjgl.BufferUtils.createByteBuffer(e.getValue().length);
                buf.put(e.getValue()).flip();
                IMG_BUFS.add(buf);   // keep alive — NVG may reference lazily
                int h = nvgCreateImageMem(ctx, 0, buf);
                imgHandles.put(e.getKey(), h);
                System.err.println("[nox] nvg img key=" + e.getKey() + " handle=" + h);
            } catch (Throwable th) {
                System.err.println("[nox] nvg img create failed: " + th);
                imgHandles.put(e.getKey(), 0);
            }
        }
        pendingImgs.clear();
    }

    /** Called by PresentMixin right before GpuSurface.present().
     *
     * We draw STRAIGHT ONTO THE WINDOW BACKBUFFER (FBO 0) as the last GL work
     * before SDL_GL_SwapWindow. Every other slot raced with renderpearl:
     *  - GuiRenderer TAIL: later submits still rewrote the RT -> flicker.
     *  - RT texture attach before the blit: the RT stays bound as the blit's
     *    sample source; drawing into a texture that is bound for sampling is
     *    a GL feedback loop -> undefined output -> flicker.
     * On FBO0 nothing runs after us before the swap, so the overlay cannot be
     * overwritten mid-frame. (MC screenshots read the offscreen RT and won't
     * show the overlay — framegrab reads FBO0 for real-pixel verification.) */
    public static void render() {
        // If the blit is skipped for many frames (minimized window), ops would
        // pile up and replay as one stale burst on resume — cap them.
        if (generic.size() > 1024) generic.clear();
        if (generic.isEmpty() && pendingGrab == null) return;
        if (!ensure()) { generic.clear(); return; }   // NVG dead → don't leak
        ensureFonts();
        Minecraft mc = Minecraft.getInstance();
        int guiW = mc.getWindow().getGuiScaledWidth();
        int guiH = mc.getWindow().getGuiScaledHeight();
        int fboW = mc.getWindow().getWidth();
        int fboH = mc.getWindow().getHeight();
        if (guiW <= 0 || guiH <= 0 || fboW <= 0 || fboH <= 0) { generic.clear(); return; }
        float ratio = (float) fboW / guiW;
        long now = System.nanoTime();
        if (lastFrameNanos != 0) {
            t += Math.min(0.1f, (now - lastFrameNanos) / 1_000_000_000f);
        }
        lastFrameNanos = now;

        int prevFbo = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING);
        int[] prevVp = new int[4];
        org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT, prevVp);
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, 0);
        org.lwjgl.opengl.GL11.glViewport(0, 0, fboW, fboH);
        boolean scissorWasOn = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        if (scissorWasOn) org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        // The stencil RB is persistent — NVG's AA/stroke pipeline relies on
        // stencil starting at 0 each frame; leftover bits mask out random
        // glyph/fill regions = violent flicker. Also force the state NVG
        // needs (no depth test/cull, stencil writes allowed).
        boolean depthWasOn = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        boolean cullWasOn = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_CULL_FACE);
        if (depthWasOn) org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        if (cullWasOn) org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_CULL_FACE);
        org.lwjgl.opengl.GL11.glDepthMask(false);
        org.lwjgl.opengl.GL11.glStencilMask(0xFF);
        org.lwjgl.opengl.GL11.glClearStencil(0);
        org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_STENCIL_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);
        // MC's pipeline may leave a PIXEL_UNPACK_BUFFER bound — NVG texture
        // uploads (font atlas, images) would then read PBO offsets = garbage.
        // We MUST restore it afterwards: renderpearl's GlStateManager caches
        // the binding and skips re-binding, so a stomped PBO makes the next
        // copyBufferToTexture interpret an offset as a client pointer —
        // glTexSubImage2D then reads garbage and segfaults inside Mesa.
        int pub = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        if (pub != 0) org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        int pab = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER_BINDING);
        int peb = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        // Renderpearl leaves sampler objects bound to texture units; those
        // samplers use *MIPMAP* min filters, which makes NVG's single-level
        // textures (font atlas, images) incomplete -> they sample (0,0,0,1).
        // Detach samplers while we draw — and restore them, same desync risk.
        int[] prevSamplers = new int[8];
        int[] prevTex2D = new int[8];
        int prevActiveTex = org.lwjgl.opengl.GL11.glGetInteger(
            org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
        try {
            for (int u = 0; u < 8; u++) {
                prevSamplers[u] = org.lwjgl.opengl.GL33.glGetIntegeri(
                    org.lwjgl.opengl.GL33.GL_SAMPLER_BINDING, u);
                org.lwjgl.opengl.GL33.glBindSampler(u, 0);
                org.lwjgl.opengl.GL13.glActiveTexture(
                    org.lwjgl.opengl.GL13.GL_TEXTURE0 + u);
                prevTex2D[u] = org.lwjgl.opengl.GL11.glGetInteger(
                    org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
            }
        } catch (Throwable ignored) {}
        drainPendingImages();
        nvgBeginFrame(ctx, guiW, guiH, ratio);
        try (MemoryStack st = MemoryStack.stackPush()) {
            for (var op : generic) op.accept(ctx, st);
        }
        nvgResetScissor(ctx);   // leaked clipOn without clipOff must not poison next frame
        nvgEndFrame(ctx);
        if (scissorWasOn) org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        if (depthWasOn) org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        if (cullWasOn) org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_CULL_FACE);
        org.lwjgl.opengl.GL11.glDepthMask(true);
        org.lwjgl.opengl.GL11.glStencilMask(0xFF);
        generic.clear();
        if (pendingGrab != null) {
            java.nio.file.Path out = pendingGrab;
            pendingGrab = null;
            try {
                java.nio.ByteBuffer px = org.lwjgl.BufferUtils.createByteBuffer(fboW * fboH * 4);
                org.lwjgl.opengl.GL11.glReadPixels(0, 0, fboW, fboH,
                        org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, px);
                org.lwjgl.stb.STBImageWrite.stbi_flip_vertically_on_write(true);
                java.nio.file.Files.createDirectories(out.getParent());
                org.lwjgl.stb.STBImageWrite.stbi_write_png(out.toString(), fboW, fboH, 4, px, fboW * 4);
            } catch (Throwable th) {
                System.err.println("[nox] framegrab failed: " + th);
            }
        }
        org.lwjgl.opengl.GL11.glViewport(prevVp[0], prevVp[1], prevVp[2], prevVp[3]);
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, prevFbo);
        // hand every touched binding back — renderpearl's state cache must
        // see the same GL world it left, or it skips re-binds it "knows"
        if (pub != 0) org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER, pub);
        org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, pab);
        org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER, peb);
        try {
            for (int u = 0; u < 8; u++) {
                org.lwjgl.opengl.GL33.glBindSampler(u, prevSamplers[u]);
                org.lwjgl.opengl.GL13.glActiveTexture(
                    org.lwjgl.opengl.GL13.GL_TEXTURE0 + u);
                org.lwjgl.opengl.GL11.glBindTexture(
                    org.lwjgl.opengl.GL11.GL_TEXTURE_2D, prevTex2D[u]);
            }
            org.lwjgl.opengl.GL13.glActiveTexture(prevActiveTex);
        } catch (Throwable ignored) {}
    }

    /** Execute one typed overlay op (queued via NvgOverlay.glow/sheen/...). */
    private static void drawTyped(long c, MemoryStack st, float[] o) {
            NVGColor col = NVGColor.malloc(st);
            NVGPaint paint = NVGPaint.malloc(st);
            {
                int type = (int) o[0];
                float x = o[1], y = o[2], w = o[3], h = o[4], r = o[5];
                int rgb = (int) o[6];
                float a = o[7];
                float pulseA = 0.55f + 0.45f * (float) Math.sin(t * 3.2);
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
                    case OP_WASH -> {
                        int rgbB = (int) o[8];
                        // corner-to-corner chromatic gradient — both ends
                        // visible → uniform tinted-glass read over the window
                        nvgLinearGradient(ctx, x, y, x + w, y + h,
                                rgba(col, rgb, a), rgba(col, rgbB, a), paint);
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
