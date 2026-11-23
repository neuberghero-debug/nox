package dev.noxclient.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.noxclient.gui.ds.DS;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.function.IntSupplier;

/**
 * NOX splash — visual-only reskin of the vanilla loading overlay. Vanilla
 * keeps driving fade-in/out, reload completion, onFinish and screen hand-off;
 * we only swap the brand background, the Mojang logo (two half-blits) and
 * the progress bar. Cancelling extractRenderState raced the loader
 * lifecycle and the NVG overlay pass — this version can't wedge the boot.
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Unique private static boolean noxIconReady;
    @Unique private static int noxIconKey = -1;   // NvgOverlay imageKey → handle

    @Shadow private float currentProgress;
    @Shadow @Final private ReloadInstance reload;

    @Inject(method = "registerTextures", at = @At("TAIL"))
    private static void nox$register(TextureManager tm, CallbackInfo ci) {
        try (var in = LoadingOverlay.class
                .getResourceAsStream("/assets/noxclient/textures/icon_glow.png")) {
            if (in == null) return;
            // NVG does its own texture upload — the MC TextureManager path
            // samples black during the loading overlay (no texture view yet).
            noxIconKey = dev.noxclient.gui.nvg.NvgOverlay.imageKey(in.readAllBytes());
            noxIconReady = true;
        } catch (IOException | RuntimeException e) {
            System.err.println("[nox] splash icon failed: " + e.getMessage());
        }
    }

    /** Mojang brand background → NOX obsidian (alpha is replaced by vanilla). */
    @Redirect(method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"))
    private int nox$background(IntSupplier original) {
        return 0xFF07090F;
    }

    /**
     * Vanilla draws the Mojang logo as two half-width blits (u = -0.0625 and
     * u = +0.0625). Redirect both: the first call paints the NOX mark once,
     * the second is skipped. `color` carries the vanilla fade alpha.
     */
    @Redirect(method = "extractRenderState",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"))
    private void nox$logo(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier id,
                          int x, int y, float u, float v, int w, int h,
                          int regionW, int regionH, int texW, int texH, int color) {
        if (u >= 0f || (color >>> 24) == 0) return;   // second half → drop
        int width = g.guiWidth(), height = g.guiHeight();
        int cx = width / 2;
        // minimalist column: mark → wordmark → version, all centered.
        // Mark sits ABOVE center so the whole column is optically centered.
        int size = Math.max(56, Math.min(112, Math.min(width / 3, height * 2 / 5)));
        int cy = height / 2 - size / 2 - 26;              // mark TOP edge
        int a = color >>> 24;
        int iconH = noxIconReady && dev.noxclient.gui.nvg.NvgOverlay.ctx() != 0
            ? dev.noxclient.gui.nvg.NvgOverlay.imageHandle(noxIconKey) : 0;
        if (iconH == 0) {
            // ring placeholder — same footprint as the mark, so the screen
            // doesn't visibly swap design when the NVG image resolves
            int r = size / 2;
            g.outline(cx - r, cy, size, size, DS.withAlpha(DS.accent(), a));
        }
        var font = net.minecraft.client.Minecraft.getInstance().font;
        if (dev.noxclient.gui.nvg.NvgOverlay.ctx() != 0) {
            // NVG pass — Inter brand + accent version pill, matching NoxMenuScreen
            int fa = a;
            int bw = width, bh = height;
            int gs = Math.round(size / 0.62f);
            // queue-helpers belong OUTSIDE the op callback (same-list mutation → CME)
            dev.noxclient.gui.nvg.NvgOverlay.glow(cx - size * 1.2f, cy + size / 2f - size,
                size * 2.4f, size * 2f, size * 0.8f, DS.accent(), fa * 26 / 255f, size * 0.9f);
            dev.noxclient.gui.nvg.NvgOverlay.vignette(bw, bh, 0.5f * fa / 255f);
            dev.noxclient.gui.nvg.NvgOverlay.op((cc, st) -> {
                if (iconH != 0)
                    dev.noxclient.gui.nvg.Nvg.img(cc, st, iconH,
                        cx - gs / 2f, cy + size / 2f - gs / 2f, gs, gs, 0f, fa / 255f);
                dev.noxclient.gui.nvg.Nvg.text(cc, st, "inter_b", 30f, cx, cy + size + 10,
                    "NOX", DS.withAlpha(0xFFE8E8F0, fa),
                    org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_TOP);
                String ver = "v" + dev.noxclient.NoxClient.VERSION;
                float vw = dev.noxclient.gui.nvg.Nvg.textW(cc, "inter_sb", 9f, ver) + 14;
                dev.noxclient.gui.nvg.Nvg.roundB(cc, st, cx - vw / 2, cy + size + 46, vw, 16, 8f,
                    (DS.accent() & 0xFFFFFF) | (fa * 0x33 / 255 << 24),
                    (DS.accent() & 0xFFFFFF) | (fa * 0x66 / 255 << 24));
                dev.noxclient.gui.nvg.Nvg.text(cc, st, "inter_sb", 9f, cx, cy + size + 54, ver,
                    DS.withAlpha(0xFFE8E8F0, fa),
                    org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
                dev.noxclient.gui.nvg.Nvg.textL(cc, st, "inter", 8.5f, cx, cy + size + 68,
                    "DONUTSMP CLIENT", DS.withAlpha(0xFF8A8A9E, fa), 2.2f,
                    org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_TOP);
            });
            return;
        }
        // pre-NVG frames — same column in vanilla text; Gui.* helpers are NVG
        // ops and draw nothing here, which is what made the screen swap design
        String brand = "NOX";
        var pose = g.pose();
        pose.pushMatrix();
        pose.scale(2.4f, 2.4f);
        g.text(font, brand, Math.round((cx - font.width(brand) * 1.2f) / 2.4f),
            Math.round((cy + size + 10) / 2.4f), DS.withAlpha(0xFFF5F5F5, a));
        pose.popMatrix();
        String ver = "v" + dev.noxclient.NoxClient.VERSION;
        g.text(font, ver, cx - font.width(ver) / 2, cy + size + 50,
            DS.withAlpha(0xFF8A8A9E, a));
        String tag = "DONUTSMP CLIENT";
        g.text(font, tag, cx - font.width(tag) / 2, cy + size + 68,
            DS.withAlpha(0xFF5A5A6E, a));
    }

    /** Vanilla bar → slim accent bar centered under the icon. */
    @Inject(method = "extractProgressBar", at = @At("HEAD"), cancellable = true)
    private void nox$progress(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2,
                              float fade, CallbackInfo ci) {
        ci.cancel();
        int width = g.guiWidth(), height = g.guiHeight();
        int span = Math.min(200, Math.max(4, width - 32));
        int bx = (width - span) / 2;
        int iconSize = Math.max(56, Math.min(112, Math.min(width / 3, height * 2 / 5)));
        // below the full brand column: mark→wordmark→pill→tagline ends at +78
        int by = height / 2 - iconSize / 2 - 26 + iconSize + 92;
        int alpha = Math.round(255 * Math.clamp(fade, 0f, 1f));
        float p = reload.isDone() ? 1f : Math.clamp(currentProgress, 0f, 1f);
        if (dev.noxclient.gui.nvg.NvgOverlay.ctx() != 0) {
            // rounded well + accent gradient fill + glow — the menu's bar style
            int fa = alpha;
            float pf = p;
            int by2 = by;
            dev.noxclient.gui.nvg.NvgOverlay.op((cc, st) -> {
                dev.noxclient.gui.nvg.Nvg.well(cc, st, bx, by2, span, 5, 2.5f,
                    DS.withAlpha(0xFF23283A, fa));
                int fw = Math.round(span * pf);
                if (fw > 3) {
                    dev.noxclient.gui.nvg.Nvg.hgrad(cc, st, bx, by2, fw, 5, 2.5f,
                        DS.withAlpha(DS.accent(), fa),
                        DS.withAlpha(DS.lighten(DS.accent(), .22f), fa));
                    dev.noxclient.gui.nvg.Nvg.insetHi(cc, st, bx, by2, fw, 2.5f,
                        (fa * 0x40 / 255 << 24) | 0xFFFFFF);
                }
                String pct = Math.round(pf * 100) + "%";
                dev.noxclient.gui.nvg.Nvg.text(cc, st, "mono", 8f, width / 2f, by2 + 10,
                    pct, DS.withAlpha(0xFF8A8A9E, fa),
                    org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_TOP);
            });
            return;
        }
        int filled = Math.round(span * p);
        g.fill(bx, by, bx + span, by + 2, DS.withAlpha(0xFF23283A, alpha));
        if (filled > 0)
            g.fill(bx, by, bx + filled, by + 2, DS.withAlpha(DS.accent(), alpha));
        // progress % centered under the bar
        var font = net.minecraft.client.Minecraft.getInstance().font;
        String pct = Math.round(p * 100) + "%";
        dev.noxclient.gui.Gui.text(g, font, pct, width / 2 - font.width(pct) / 2, by + 7,
            DS.withAlpha(0xFF8A8A9E, alpha));
    }
}
