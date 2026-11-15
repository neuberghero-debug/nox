package dev.noxclient.mixin;

import dev.noxclient.physics.AnimationSystem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EntityTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidAnimationMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void nox$pose(HumanoidRenderState s, CallbackInfo ci) {
        if (s.entityType == EntityTypes.ZOMBIE || s.entityType == EntityTypes.SKELETON)
            AnimationSystem.humanoid((HumanoidModel<?>) (Object) this, s);
    }
}
