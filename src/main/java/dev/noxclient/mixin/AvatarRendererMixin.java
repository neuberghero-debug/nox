package dev.noxclient.mixin;

import dev.noxclient.cosmetics.CosmeticLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Attaches the NOX cosmetic layer (shoulder pets / hats / mesh auras)
 *  to every player renderer — wide and slim variants share the mixin. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void nox$addCosmetics(EntityRendererProvider.Context ctx, boolean slim,
            CallbackInfo ci) {
        ((LivingEntityRendererAccessor) this).nox$addLayer(
            new CosmeticLayer((AvatarRenderer<?>) (Object) this));
        ((LivingEntityRendererAccessor) this).nox$addLayer(
            new dev.noxclient.cosmetics.NoxCapeLayer((AvatarRenderer<?>) (Object) this));
    }
}
