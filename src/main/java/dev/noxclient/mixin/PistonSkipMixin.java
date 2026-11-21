package dev.noxclient.mixin;

import dev.noxclient.module.donutsmp.FarmOptimizer;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FarmOptimizer piston skip — progress is always 1 so pushed blocks render
 * straight at their end position instead of sliding.
 */
@Mixin(PistonMovingBlockEntity.class)
public abstract class PistonSkipMixin {
    @Inject(method = "getProgress", at = @At("HEAD"), cancellable = true)
    private void nox$skipPiston(float partial, CallbackInfoReturnable<Float> cir) {
        try {
            if (FarmOptimizer.skipPistonAnim()) cir.setReturnValue(1f);
        } catch (Throwable ignored) {}
    }
}
