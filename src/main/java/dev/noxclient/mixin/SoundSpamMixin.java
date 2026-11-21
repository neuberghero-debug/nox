package dev.noxclient.mixin;

import dev.noxclient.module.donutsmp.FarmOptimizer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** FarmOptimizer sound spam — repeated sounds above the limit get muted. */
@Mixin(SoundEngine.class)
public abstract class SoundSpamMixin {
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void nox$soundSpam(SoundInstance si,
                               CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        try {
            if (FarmOptimizer.muteSound(si))
                cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        } catch (Throwable ignored) {}
    }
}
