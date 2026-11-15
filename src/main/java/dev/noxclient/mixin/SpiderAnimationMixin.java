package dev.noxclient.mixin;

import dev.noxclient.physics.AnimationSystem;
import net.minecraft.client.model.monster.spider.SpiderModel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpiderModel.class)
public abstract class SpiderAnimationMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)V", at = @At("TAIL"))
    private void nox$pose(LivingEntityRenderState s, CallbackInfo ci) { AnimationSystem.creature((SpiderModel) (Object) this, s); }
}
