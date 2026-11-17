package dev.noxclient.mixin;

import dev.noxclient.gui.nvg.NvgOverlay;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NVG finish pass — hooked right before GpuSurface.present(): the absolute
 * last GL slot of the frame. NvgOverlay renders onto the window backbuffer
 * (FBO 0) directly, so NOTHING runs between our draw and SDL_GL_SwapWindow —
 * the overlay can't be overwritten mid-frame (every earlier slot raced
 * renderpearl submits and produced the violent flicker).
 */
@Mixin(Minecraft.class)
public abstract class PresentMixin {

    @Inject(method = "renderFrame", at = @At(value = "INVOKE",
        target = "Lcom/mojang/renderpearl/api/device/GpuSurface;present()V"))
    private void nox$nvgBeforePresent(boolean tick, CallbackInfo ci) {
        NvgOverlay.render();
    }
}
