package dev.noxclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * MC 26.3 builds its pipeline ShaderSource from a cached ResourceManager that
 * predates the mod resource-pack mount, so shaders living in mod jars throw
 * FileNotFoundException at preload even though the live manager resolves them.
 * Resolve through the live manager first; fall through to vanilla on a miss.
 */
@Mixin(targets = "net.minecraft.client.renderer.GameRenderer$1")
public abstract class ShaderSourceMixin {
    @Inject(method = "getShader", at = @At("HEAD"), cancellable = true)
    private void nox$liveShaderSource(Identifier id, com.mojang.renderpearl.api.pipeline.ShaderType type,
                                      CallbackInfoReturnable<String> cir) {
        if (Minecraft.getInstance() == null) return;
        Identifier file = type.idConverter().idToFile(id);
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(file);
        if (res.isEmpty()) return;
        try {
            String src = res.get().readAllAsString();
            if (src != null) cir.setReturnValue(src);
        } catch (Throwable ignored) {}
    }
}
