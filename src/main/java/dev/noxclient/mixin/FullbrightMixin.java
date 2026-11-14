package dev.noxclient.mixin;

import dev.noxclient.NoxClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fullbright: force the night-vision lightmap factor to max for the local player. */
@Mixin(GameRenderer.class)
public abstract class FullbrightMixin {
    @Inject(method = "nightVisionScale", at = @At("HEAD"), cancellable = true)
    private static void nox$fb(LivingEntity entity, float tickDelta,
                               CallbackInfoReturnable<Float> cir) {
        if (entity == Minecraft.getInstance().player
            && NoxClient.INSTANCE != null
            && NoxClient.INSTANCE.modules.get("Fullbright") instanceof dev.noxclient.module.Module m
            && m.isEnabled()) {
            float target = m instanceof dev.noxclient.module.render.Fullbright fb
                ? fb.scale() : 1.0f;
            cir.setReturnValue(target);
        }
    }
}
