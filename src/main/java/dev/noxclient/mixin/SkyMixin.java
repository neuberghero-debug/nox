package dev.noxclient.mixin;

import dev.noxclient.NoxClient;
import dev.noxclient.module.render.CustomSky;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Overrides the atmospheric fog base color = the visible sky color. */
@Mixin(AtmosphericFogEnvironment.class)
public abstract class SkyMixin {
    @Inject(method = "getBaseColor", at = @At("HEAD"), cancellable = true)
    private void nox$sky(ClientLevel level, Camera camera, int i, float f,
                         CallbackInfoReturnable<Vector3fc> cir) {
        var m = NoxClient.INSTANCE == null ? null : NoxClient.INSTANCE.modules.get("CustomSky");
        if (m instanceof CustomSky sky && sky.isEnabled()) {
            cir.setReturnValue(sky.skyColor());
        }
    }
}
