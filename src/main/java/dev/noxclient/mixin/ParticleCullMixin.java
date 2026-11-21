package dev.noxclient.mixin;

import dev.noxclient.module.donutsmp.FarmOptimizer;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** FarmOptimizer particle cull — explosion families never get created. */
@Mixin(ParticleEngine.class)
public abstract class ParticleCullMixin {
    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void nox$cullParticle(ParticleOptions options, double x, double y, double z,
                                  double xd, double yd, double zd,
                                  CallbackInfoReturnable<Particle> cir) {
        try {
            if (FarmOptimizer.cullParticle(options)) cir.setReturnValue(null);
        } catch (Throwable ignored) {}
    }
}
