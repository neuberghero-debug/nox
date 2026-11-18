package dev.noxclient.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Submits the NOX menu-sky offscreen pass at the very start of frame
 * extraction — BEFORE the gui batch and any lazy glyph-atlas uploads record.
 * Submitting our encoder mid-extract raced vanilla writeToTexture calls and
 * corrupted font atlas pages (missing glyphs); the frame boundary is safe.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "extract", at = @At("HEAD"))
    private void nox$menuSkyPass(DeltaTracker tracker, boolean running, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        var scr = mc.gui.screen();
        if (scr instanceof TitleScreen
            || (scr != null && "Sky".equals(dev.noxclient.gui.UICustom.backdrop))) {
            // physical framebuffer size — gui-scaled res upscales blurry
            dev.noxclient.render.MenuSky.submitPass(
                mc.getWindow().getWidth(), mc.getWindow().getHeight());
        }
    }
}
