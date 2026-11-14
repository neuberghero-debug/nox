package dev.noxclient.mixin;

import dev.noxclient.NoxClient;
import dev.noxclient.module.render.Zoom;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optifine-style zoom without touching options.fov — vanilla clamps fov() to
 * [30,110] and error-spams on out-of-range writes; dividing Camera.getFov()
 * sidesteps both and allows the real zoom factor.
 */
@Mixin(Camera.class)
public abstract class CameraFovMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void nox$zoom(CallbackInfoReturnable<Float> cir) {
        var m = NoxClient.INSTANCE.modules.get("Zoom");
        if (m instanceof Zoom z && z.isEnabled())
            cir.setReturnValue(cir.getReturnValue() / Math.max(1, z.factor.intVal()));
    }
}
