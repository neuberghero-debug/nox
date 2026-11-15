package dev.noxclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.noxclient.module.render.Animations;
import dev.noxclient.physics.AnimationSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class CameraMotionMixin {
    @Inject(method = "bobView", at = @At("TAIL"))
    private void nox$camera(CameraRenderState camera, PoseStack pose, CallbackInfo ci) {
        Animations cfg = Animations.active();
        var player = Minecraft.getInstance().player;
        if (cfg == null || player == null || !camera.isFirstPerson || player.isUsingItem()
            || AnimationSystem.deferPlayer()) return;
        var frame = AnimationSystem.local(camera.cameraEntityPartialTicks);
        if (!frame.active) return;
        float amount = cfg.cameraIntensity.floatVal();
        if (cfg.cameraRoll.on()) pose.rotateDegrees(Axis.ZP, Math.max(-.6f, Math.min(.6f, frame.strafe * 3)) * amount);
        if (cfg.cameraBreath.on()) pose.translate(0, Math.sin((player.tickCount + camera.cameraEntityPartialTicks) * .09 + frame.phase) * .0015 * amount, 0);
        if (cfg.cameraLanding.on()) pose.translate(0, -frame.landing * .018 * amount, 0);
    }
}
