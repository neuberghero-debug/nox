package dev.noxclient.mixin;

import dev.noxclient.render.NoxRender;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emits NOX world gizmos inside the per-frame main-thread collection window —
 * right after LevelExtractor.collectPerFrameMainThreadGizmos() opens the
 * collector, so our Gizmos.* calls land in the same batch as vanilla's.
 */
@Mixin(Minecraft.class)
public abstract class GizmoEmitMixin {
    @Inject(method = "renderFrame",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/extract/LevelExtractor;collectPerFrameMainThreadGizmos()Lnet/minecraft/gizmos/Gizmos$TemporaryCollection;",
            shift = At.Shift.AFTER))
    private void nox$emitGizmos(boolean tick, CallbackInfo ci) {
        if (Minecraft.getInstance().level != null) NoxRender.emit();
    }
}
