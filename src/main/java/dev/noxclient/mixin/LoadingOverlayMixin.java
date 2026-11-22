package dev.noxclient.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.noxclient.gui.ds.DS;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
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
    @Unique private static final Identifier NOX_ICON =
        Identifier.fromNamespaceAndPath("noxclient", "loading_icon");
    @Unique private static boolean noxIconReady;

    @Shadow private float currentProgress;
    @Shadow @Final private ReloadInstance reload;

    @Inject(method = "registerTextures", at = @At("TAIL"))
    private static void nox$register(TextureManager tm, CallbackInfo ci) {
        try (var in = LoadingOverlay.class
                .getResourceAsStream("/assets/noxclient/textures/icon_glow.png")) {
            if (in == null) return;
            var texture = new DynamicTexture(() -> "NOX loading icon", NativeImage.read(in));
            texture.upload();
            tm.register(NOX_ICON, texture);
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
        int cx = width / 2, cy = height / 2 - 14;
        int size = Math.max(48, Math.min(128, Math.min(width / 3, height * 2 / 5)));
        int a = color >>> 24;
        if (noxIconReady) {
            // icon_glow.png = mark + baked radial halo — a code-drawn glow
            // plate can only be a square and rendered as a visible box edge.
            // The mark fills only ~62% of the texture → draw larger so it
            // keeps the same on-screen size.
            int gs = Math.round(size / 0.62f);
            g.blit(NOX_ICON, cx - gs / 2, cy - gs / 2,
                cx + gs / 2, cy + gs / 2, 0f, 1f, 0f, 1f);
        } else {
            int r = size / 2;
            g.outline(cx - r, cy - r, size, size, DS.withAlpha(DS.accent(), a));
        }
        // accent hairline under the mark
        int lineW = size + 40, lx = cx - lineW / 2, ly = cy + size / 2 + 10;
        g.fill(lx, ly, lx + lineW, ly + 1, DS.withAlpha(DS.accent(), a / 2));
        // wordmark + version under the hairline — the bare logo read as
        // "unfinished", not branded
        var font = net.minecraft.client.Minecraft.getInstance().font;
        String brand = "N O X";
        int bx = cx - font.width(brand) / 2;
        dev.noxclient.gui.Gui.textBold(g, font, brand, bx, ly + 8,
            DS.withAlpha(0xFFE8E8F0, a));
        String ver = "v" + dev.noxclient.NoxClient.VERSION;
        int vx = cx - font.width(ver) / 2;
        dev.noxclient.gui.Gui.text(g, font, ver, vx, ly + 20,
            DS.withAlpha(0xFF8A8A9E, a));
    }

    /** Vanilla bar → slim accent bar centered under the icon. */
    @Inject(method = "extractProgressBar", at = @At("HEAD"), cancellable = true)
    private void nox$progress(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2,
                              float fade, CallbackInfo ci) {
        ci.cancel();
        int width = g.guiWidth(), height = g.guiHeight();
        int span = Math.min(180, Math.max(4, width - 32));
        int bx = (width - span) / 2;
        int iconSize = Math.max(48, Math.min(128, Math.min(width / 3, height * 2 / 5)));
        int by = height / 2 - 14 + iconSize / 2 + 18;
        int alpha = Math.round(255 * Math.clamp(fade, 0f, 1f));
        float p = reload.isDone() ? 1f : Math.clamp(currentProgress, 0f, 1f);
        int filled = Math.round(span * p);
        g.fill(bx, by, bx + span, by + 3, DS.withAlpha(0xFF23283A, alpha));
        if (filled > 0)
            g.fill(bx, by, bx + filled, by + 3, DS.withAlpha(DS.accent(), alpha));
        // progress % right of the bar
        var font = net.minecraft.client.Minecraft.getInstance().font;
        String pct = Math.round(p * 100) + "%";
        dev.noxclient.gui.Gui.text(g, font, pct, bx + span + 8, by - 2,
            DS.withAlpha(0xFF8A8A9E, alpha));
    }
}
