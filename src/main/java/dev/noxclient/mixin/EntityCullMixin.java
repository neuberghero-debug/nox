package dev.noxclient.mixin;

import dev.noxclient.module.donutsmp.FarmOptimizer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** FarmOptimizer entity cull — drops farm spam from the render list. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityCullMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void nox$cull(Entity e, net.minecraft.client.renderer.culling.Frustum frustum,
                          double x, double y, double z, float partial,
                          CallbackInfoReturnable<Boolean> cir) {
        try {
            if (FarmOptimizer.cullEntity(e)) cir.setReturnValue(false);
        } catch (Throwable ignored) {}
    }
}
