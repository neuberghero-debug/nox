package dev.noxclient.mixin;

import dev.noxclient.gui.nvg.NvgOverlay;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After the GUI batch is submitted, runs the NanoVG finish pass
 * (bloom/sheen/vignette ops queued by NOX screens during extractRenderState).
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void nox$nvgFinish(CallbackInfo ci) {
        NvgOverlay.render();
    }
}
