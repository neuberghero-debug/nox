package dev.noxclient.mixin;

import dev.noxclient.physics.AnimationSystem;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndermanModel.class)
public abstract class EndermanAnimationMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/EndermanRenderState;)V", at = @At("TAIL"))
    private void nox$pose(EndermanRenderState s, CallbackInfo ci) { AnimationSystem.humanoid((EndermanModel<?>) (Object) this, s); }
}
