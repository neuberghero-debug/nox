package dev.noxclient.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NOX splash replacing the Mojang loading screen entirely: dark gradient,
 * real icon texture (registered alongside the vanilla logo), accent progress
 * bar tracking actual reload progress. No font usage — fonts are mid-load.
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    private static final Identifier NOX_ICON =
        Identifier.fromNamespaceAndPath("noxclient", "textures/icon.png");

    @Shadow private float currentProgress;
    @Shadow private long fadeOutStart;
    @Shadow private long fadeInStart;
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private boolean fadeIn;
    @Shadow @Final private net.minecraft.server.packs.resources.ReloadInstance reload;
    @Shadow @Final private java.util.function.Consumer<java.util.Optional<java.lang.Throwable>> onFinish;

    private static int iconKey = -1;

    @Inject(method = "registerTextures", at = @At("TAIL"))
    private static void nox$register(TextureManager tm, CallbackInfo ci) {
        // Icon rides the NVG pass — crisp at any size and independent of the
        // renderpearl texture manager (DynamicTexture uploads raced reloads).
        if (iconKey >= 0) return;
        try (var in = LoadingOverlayMixin.class.getResourceAsStream("/assets/noxclient/textures/icon.png")) {
            if (in != null)
                iconKey = dev.noxclient.gui.nvg.NvgOverlay.imageKey(in.readAllBytes());
        } catch (Throwable ignored) {}
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void nox$splash(GuiGraphicsExtractor g, int mx, int my, float dt, CallbackInfo ci) {
        ci.cancel();
        int w = Math.max(1, minecraft.getWindow().getGuiScaledWidth());
        int h = Math.max(1, minecraft.getWindow().getGuiScaledHeight());
        int cx = w / 2, cy = h / 2;

        // vanilla completion logic — lives in extractRenderState, so the
        // HEAD cancel must drive it or the overlay never finishes
        long now = Util.getMillis();
        if (fadeIn && fadeInStart == -1) fadeInStart = now;
        if (fadeOutStart == -1 && reload.isDone()) {
            try {
                reload.checkExceptions();
                onFinish.accept(java.util.Optional.empty());
            } catch (Throwable t) {
                onFinish.accept(java.util.Optional.of(t));
            }
            fadeOutStart = now;
        }
        if (fadeOutStart != -1 && now - fadeOutStart >= LoadingOverlay.FADE_OUT_TIME) {
            minecraft.gui.setOverlay(null);
            return;
        }

        // fade alpha
        float a = 1.0f;
        if (fadeIn && fadeInStart != -1)
            a = Math.min(1f, (now - fadeInStart) / (float) LoadingOverlay.FADE_IN_TIME);
        if (fadeOutStart != -1)
            a *= 1f - Math.min(1f, (now - fadeOutStart) / (float) LoadingOverlay.FADE_OUT_TIME);
        final float A = a;

        // progress — drive smoothing ourselves (vanilla does it in the cancelled body)
        currentProgress += (Math.min(1f, reload.getActualProgress()) - currentProgress) * 0.2f;
        final float p = Math.min(1f, currentProgress);

        final int kk = iconKey;
        final long t = now;
        dev.noxclient.gui.nvg.NvgOverlay.op((c, st) -> {
            int ACC = 0xFF7A2BE2, ACC2 = 0xFF9B7BFF;

            /* backdrop — obsidian + soft accent nebula top-left / blue bottom-right */
            dev.noxclient.gui.nvg.Nvg.vgrad(c, st, 0, 0, w, h, 0,
                dev.noxclient.gui.nvg.Nvg.fade(0xFF0A0B14, A), dev.noxclient.gui.nvg.Nvg.fade(0xFF040407, A));
            org.lwjgl.nanovg.NVGPaint r1 = org.lwjgl.nanovg.NanoVG.nvgRadialGradient(c,
                w * .22f, h * .2f, 10, Math.min(w, h) * .55f,
                dev.noxclient.gui.nvg.Nvg.col(st, ACC, .10f * A), dev.noxclient.gui.nvg.Nvg.col(st, ACC, 0),
                org.lwjgl.nanovg.NVGPaint.malloc(st));
            org.lwjgl.nanovg.NanoVG.nvgBeginPath(c);
            org.lwjgl.nanovg.NanoVG.nvgRect(c, 0, 0, w, h);
            org.lwjgl.nanovg.NanoVG.nvgFillPaint(c, r1); org.lwjgl.nanovg.NanoVG.nvgFill(c);
            org.lwjgl.nanovg.NVGPaint r2 = org.lwjgl.nanovg.NanoVG.nvgRadialGradient(c,
                w * .82f, h * .85f, 10, Math.min(w, h) * .5f,
                dev.noxclient.gui.nvg.Nvg.col(st, 0xFF3A5AFF, .08f * A), dev.noxclient.gui.nvg.Nvg.col(st, 0xFF3A5AFF, 0),
                org.lwjgl.nanovg.NVGPaint.malloc(st));
            org.lwjgl.nanovg.NanoVG.nvgBeginPath(c);
            org.lwjgl.nanovg.NanoVG.nvgRect(c, 0, 0, w, h);
            org.lwjgl.nanovg.NanoVG.nvgFillPaint(c, r2); org.lwjgl.nanovg.NanoVG.nvgFill(c);

            /* twinkling dust — deterministic, drifts slowly right */
            for (int i = 0; i < 90; i++) {
                int sx = (int) ((i * 2654435761L & 0x7FFFFFFF) % (w + 40)) - 20;
                int sy = (i * 40503 & 0x7FFFFFFF) % h;
                sx = (sx + (int) (t / (70 + i % 40))) % (w + 30) - 15;
                float tw = .35f + .65f * (float) Math.abs(Math.sin(i * 1.7 + t / 900.0));
                dev.noxclient.gui.nvg.Nvg.circle(c, st, sx, sy, .8f + (i % 4) * .3f,
                    dev.noxclient.gui.nvg.Nvg.fade(i % 6 == 0 ? ACC2 : 0xFFB8C4FF, tw * .5f * A));
            }

            /* accretion ring — tilted ellipse arc rotating around the icon */
            float is = Math.min(110, h / 4.6f);
            float icy = cy - is * .62f;
            org.lwjgl.nanovg.NanoVG.nvgSave(c);
            org.lwjgl.nanovg.NanoVG.nvgTranslate(c, cx, icy);
            org.lwjgl.nanovg.NanoVG.nvgScale(c, 1f, .32f);
            org.lwjgl.nanovg.NanoVG.nvgRotate(c, (float) (t / 2600.0 * Math.PI * 2));
            org.lwjgl.nanovg.NanoVG.nvgBeginPath(c);
            org.lwjgl.nanovg.NanoVG.nvgArc(c, 0, 0, is * .85f, 0, (float) Math.PI * 1.35f,
                org.lwjgl.nanovg.NanoVG.NVG_CW);
            org.lwjgl.nanovg.NanoVG.nvgStrokeColor(c, dev.noxclient.gui.nvg.Nvg.col(st, ACC2, .55f * A));
            org.lwjgl.nanovg.NanoVG.nvgStrokeWidth(c, 3.2f); org.lwjgl.nanovg.NanoVG.nvgStroke(c);
            org.lwjgl.nanovg.NanoVG.nvgBeginPath(c);
            org.lwjgl.nanovg.NanoVG.nvgArc(c, 0, 0, is * .85f, (float) Math.PI * 1.5f,
                (float) Math.PI * 1.95f, org.lwjgl.nanovg.NanoVG.NVG_CW);
            org.lwjgl.nanovg.NanoVG.nvgStrokeColor(c, dev.noxclient.gui.nvg.Nvg.col(st, ACC, .9f * A));
            org.lwjgl.nanovg.NanoVG.nvgStrokeWidth(c, 4.6f); org.lwjgl.nanovg.NanoVG.nvgStroke(c);
            org.lwjgl.nanovg.NanoVG.nvgRestore(c);
            // inner counter-arc
            org.lwjgl.nanovg.NanoVG.nvgSave(c);
            org.lwjgl.nanovg.NanoVG.nvgTranslate(c, cx, icy);
            org.lwjgl.nanovg.NanoVG.nvgScale(c, 1f, .32f);
            org.lwjgl.nanovg.NanoVG.nvgRotate(c, -(float) (t / 3900.0 * Math.PI * 2));
            org.lwjgl.nanovg.NanoVG.nvgBeginPath(c);
            org.lwjgl.nanovg.NanoVG.nvgArc(c, 0, 0, is * .62f, 0, (float) Math.PI * .8f,
                org.lwjgl.nanovg.NanoVG.NVG_CW);
            org.lwjgl.nanovg.NanoVG.nvgStrokeColor(c, dev.noxclient.gui.nvg.Nvg.col(st, 0xFFE8D8FF, .5f * A));
            org.lwjgl.nanovg.NanoVG.nvgStrokeWidth(c, 2.2f); org.lwjgl.nanovg.NanoVG.nvgStroke(c);
            org.lwjgl.nanovg.NanoVG.nvgRestore(c);

            /* icon — soft accent glow + image */
            dev.noxclient.gui.nvg.Nvg.shadow(c, st, cx - is * .34f, icy - is * .34f,
                is * .68f, is * .68f, is * .3f, 26,
                dev.noxclient.gui.nvg.Nvg.fade(ACC, .30f * A));
            if (kk >= 0) {
                int imgH = dev.noxclient.gui.nvg.NvgOverlay.imageHandle(kk);
                if (imgH != 0)
                    dev.noxclient.gui.nvg.Nvg.img(c, st, imgH, cx - is * .32f, icy - is * .32f,
                        is * .64f, is * .64f, is * .14f, A);
            }

            /* wordmark + subtitle */
            dev.noxclient.gui.nvg.Nvg.textL(c, st, "inter_b", 26, cx, icy + is * .58f,
                "N O X", dev.noxclient.gui.nvg.Nvg.fade(0xFFFFFFFF, A), 4f,
                org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
            dev.noxclient.gui.nvg.Nvg.textL(c, st, "inter", 11.5f, cx, icy + is * .58f + 22,
                "DonutSMP client", dev.noxclient.gui.nvg.Nvg.fade(0xFF8A8A9E, A), .4f,
                org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);

            /* progress — well track + gradient fill + glowing head + sheen */
            float bw = Math.min(250, w - 90), bx = cx - bw / 2, by = cy + is * .72f;
            dev.noxclient.gui.nvg.Nvg.well(c, st, bx, by, bw, 5, 2.5f,
                dev.noxclient.gui.nvg.Nvg.fade(0xFF14151E, A));
            float fw = Math.max(6, bw * p);
            dev.noxclient.gui.nvg.Nvg.hgrad(c, st, bx, by, fw, 5, 2.5f,
                dev.noxclient.gui.nvg.Nvg.fade(ACC, A), dev.noxclient.gui.nvg.Nvg.fade(0xFFFF8AD5, A));
            if (p > 0.01f) {
                dev.noxclient.gui.nvg.Nvg.shadow(c, st, bx + fw - 6, by - 3, 12, 11, 5, 10,
                    dev.noxclient.gui.nvg.Nvg.fade(0xFFFF8AD5, .7f * A));
                dev.noxclient.gui.nvg.Nvg.circle(c, st, bx + fw - 1, by + 2.5f, 2.4f,
                    dev.noxclient.gui.nvg.Nvg.fade(0xFFFFFFFF, .9f * A));
                int swp = (int) ((t / 5) % (bw + 60)) - 30;
                if (swp < fw && swp + 26 > 0)
                    dev.noxclient.gui.nvg.Nvg.hgrad(c, st,
                        bx + Math.max(0, swp), by + 1, Math.min(26, fw - Math.max(0, swp)), 3, 1.5f,
                        0x00FFFFFF, dev.noxclient.gui.nvg.Nvg.fade(0xFFE8D8FF, .35f * A));
            }
            dev.noxclient.gui.nvg.Nvg.text(c, st, "mono", 9.5f, cx, by + 18,
                (int) (p * 100) + " %", dev.noxclient.gui.nvg.Nvg.fade(0xFF8A8A9E, A * .8f),
                org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);

            /* rotating tips — cycle every 3s, soft crossfade between them */
            String[] TIPS = {
                "tip · type to search — no click needed",
                "tip · Ctrl+K opens the module palette",
                "tip · MMB pins a module to the footer",
                "tip · RMB a row expands its settings",
                "tip · PANIC kills every risky module",
                "tip · AutoScout + ActivityFind hunts bases afk",
                "tip · /nox share exports your config",
            };
            int ti = (int) (t / 3000) % TIPS.length;
            float tipFade = 1f - Math.abs((t % 3000) / 1500f - 1f);   // 0→1→0
            dev.noxclient.gui.nvg.Nvg.text(c, st, "inter", 10f, cx, by + 36,
                TIPS[ti], dev.noxclient.gui.nvg.Nvg.fade(0xFF8A8A9E, A * .55f * (0.4f + 0.6f * tipFade)),
                org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);

            /* bottom brand hairline */
            dev.noxclient.gui.nvg.Nvg.hgrad(c, st, 0, h - 1.5f, w * .5f, 1.5f, 0,
                0x00FFFFFF, dev.noxclient.gui.nvg.Nvg.fade(ACC, .5f * A));
            dev.noxclient.gui.nvg.Nvg.hgrad(c, st, w * .5f, h - 1.5f, w * .5f, 1.5f, 0,
                dev.noxclient.gui.nvg.Nvg.fade(ACC, .5f * A), 0x00FFFFFF);
        });
    }
}
