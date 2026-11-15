package dev.noxclient.mixin;

import dev.noxclient.physics.AnimationFrame;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public abstract class AnimationStateMixin implements AnimationFrame.Access {
    @Unique private final AnimationFrame nox$frame = new AnimationFrame();
    @Override public AnimationFrame nox$animation() { return nox$frame; }
}
