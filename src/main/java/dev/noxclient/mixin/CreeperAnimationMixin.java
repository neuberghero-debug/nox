package dev.noxclient.mixin;

import dev.noxclient.physics.AnimationSystem;
import net.minecraft.client.model.monster.creeper.CreeperModel;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreeperModel.class)
public abstract class CreeperAnimationMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/CreeperRenderState;)V", at = @At("TAIL"))
    private void nox$pose(CreeperRenderState s, CallbackInfo ci) { AnimationSystem.creature((CreeperModel) (Object) this, s); }
}
