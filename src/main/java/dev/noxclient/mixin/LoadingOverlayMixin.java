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

    private static boolean iconReady;

    @Inject(method = "registerTextures", at = @At("TAIL"))
    private static void nox$register(TextureManager tm, CallbackInfo ci) {
        // DynamicTexture uploads on construction — safe to blit from frame one,
        // unlike SimpleTexture whose contents load during the reload itself.
        try (var in = LoadingOverlayMixin.class.getResourceAsStream("/assets/noxclient/textures/icon.png")) {
            if (in != null) {
                tm.register(NOX_ICON, new DynamicTexture(() -> "nox_icon", NativeImage.read(in)));
                iconReady = true;
            }
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
        int A = (int) (a * 255) << 24;

        g.fillGradient(0, 0, w, h, A | 0x0B0716, A | 0x030309);

        // aurora glows — soft concentric discs, top-left accent + bottom-right
        // cool blue (deterministic, no textures, no per-frame allocations)
        int aurA = (A >>> 24) / 7 << 24;
        for (int i = 5; i >= 1; i--) {
            int r = i * Math.min(w, h) / 10;
            dev.noxclient.gui.Gui.circle(g, w / 5 - r / 2, h / 6, r, (aurA / (i + 1)) | 0x7A2BE2);
            dev.noxclient.gui.Gui.circle(g, w * 4 / 5 + r / 3, h * 5 / 6, r, (aurA / (i + 2)) | 0x3A5AFF);
        }

        // drifting bokeh dust — deterministic positions + slow twinkle,
        // rounded dots only (no pixel stars)
        int dustA = (A >>> 24) / 3 << 24;
        for (int i = 0; i < 70; i++) {
            int hx = (int) (i * 2654435761L), hz = i * 40503;
            int sx = (hx & 0x7FFFFFFF) % w, sy = (hz & 0x7FFFFFFF) % h;
            int drift = (int) (now / (40 + i % 30)) % (w + 40) - 20;
            sx = (sx + drift) % (w + 20); if (sx < 0) sx += w + 20; sx -= 10;
            boolean lit = (i * 31 + (int) (now / 14)) % 100 < 55;
            int alpha = lit ? dustA : dustA / 2;
            int r = 1 + (i % 5 == 0 ? 1 : 0);
            dev.noxclient.gui.Gui.circle(g, sx, sy, r,
                alpha | (i % 7 == 0 ? 0x9B7BFF : 0xB8C4FF));
        }

        // radial accent glow behind the icon (concentric fading discs)
        int is = Math.min(96, h / 5);
        int glow = ((A >>> 24) / 5) << 24;
        for (int i = 4; i >= 1; i--)
            dev.noxclient.gui.Gui.round(g, cx - is / 2 - 6 * i, cy - is / 2 - 24 - 6 * i,
                is + 12 * i, is + 12 * i, 18 + i * 6, (glow / (i + 1)) | 0x7A2BE2);
        dev.noxclient.gui.Gui.round(g, cx - is / 2 - 6, cy - is / 2 - 24,
            is + 12, is + 12, 18, glow | 0x7A2BE2);
        if (iconReady)
            g.blit(RenderPipelines.GUI_TEXTURED, NOX_ICON,
                cx - is / 2, cy - is / 2 - 20, 0, 0, is, is, is, is);

        // progress bar — drive smoothing ourselves (vanilla does it in the cancelled body)
        currentProgress += (Math.min(1f, reload.getActualProgress()) - currentProgress) * 0.2f;
        float p = Math.min(1f, currentProgress);
        int bw = Math.min(240, w - 80), bx = cx - bw / 2, by = cy + is / 2;
        dev.noxclient.gui.Gui.pill(g, bx - 1, by - 1, bw + 2, 6, A | 0x15151C);
        dev.noxclient.gui.Gui.pill(g, bx, by, Math.max(6, (int) (bw * p)), 4, A | 0x7A2BE2);
        if (p > 0.01f) {
            // rounded head + traveling shine inside the fill
            dev.noxclient.gui.Gui.circle(g, bx + (int) (bw * p) - 1, by + 2, 3, A | 0xE8D8FF);
            int sw = 26, sweep = (int) ((now / 6) % (bw + sw * 2)) - sw;
            int fillW = (int) (bw * p);
            if (sweep < fillW && sweep + sw > 0)
                dev.noxclient.gui.Gui.pill(g, bx + Math.max(0, sweep), by + 1,
                    Math.min(sw, fillW - Math.max(0, sweep)), 2, (A / 3) | 0xE8D8FF);
        }

        // bottom brand hairline
        int lineA = (A >>> 24) / 2 << 24;
        dev.noxclient.gui.Gui.hgrad(g, 0, h - 2, w / 2, 1, 0, lineA | 0x7A2BE2);
        dev.noxclient.gui.Gui.hgrad(g, w / 2, h - 2, w - w / 2, 1, lineA | 0x7A2BE2, 0);
    }
}
