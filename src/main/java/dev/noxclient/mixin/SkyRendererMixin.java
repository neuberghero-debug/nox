package dev.noxclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import dev.noxclient.render.NoxShaderSky;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.dimension.DimensionType;
import org.joml.Vector3fc;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the vanilla sky with the NOX shader sky when CustomSky is in a
 * shader mode. Runs inside vanilla's own render pass — no extra pass, no
 * packet side effects. */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    @Unique private boolean nox$overworld;

    @Inject(method = "render", at = @At("HEAD"))
    private void nox$capture(GpuBufferSlice slice, SkyRenderState state, CallbackInfo ci) {
        nox$overworld = state.skybox == DimensionType.Skybox.OVERWORLD;
        if (nox$overworld) NoxShaderSky.prepareFrame();
    }

    @Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true)
    private void nox$shaderDisc(RenderPass pass, Vector3fc skyColor, CallbackInfo ci) {
        if (nox$overworld && NoxShaderSky.render(pass)) ci.cancel();
    }

    @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"), cancellable = true)
    private void nox$noCelestials(RenderPass pass, PoseStack pose, float sun, float moon,
                                  float star, MoonPhase phase, float rain, float bright,
                                  CallbackInfo ci) {
        if (nox$overworld && NoxShaderSky.active()) ci.cancel();
    }

    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
    private void nox$noSunrise(RenderPass pass, PoseStack pose, float angle,
                               Vector4fc color, CallbackInfo ci) {
        if (nox$overworld && NoxShaderSky.active()) ci.cancel();
    }

    @Inject(method = "renderDarkDisc", at = @At("HEAD"), cancellable = true)
    private void nox$noDarkDisc(RenderPass pass, CallbackInfo ci) {
        if (nox$overworld && NoxShaderSky.active()) ci.cancel();
    }
}
