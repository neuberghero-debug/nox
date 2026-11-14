package dev.noxclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.noxclient.gui.AnimatedCape;
import dev.noxclient.gui.CustomCape;
import dev.noxclient.module.render.Cosmetics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swaps the local player's cape (and matching elytra texture) for the branded
 * or custom NOX cape. The player is matched by skin body texture id — a
 * reference compare against getSkin() can miss when the skin object is
 * rebuilt (resource reload, dimension change).
 */
@Mixin(CapeLayer.class)
public abstract class CapeMixin {
    private static final ClientAsset.Texture NOX_CAPE = new ClientAsset.ResourceTexture(
        Identifier.fromNamespaceAndPath("noxclient", "textures/cape/nox_cape.png"));
    private static final ClientAsset.Texture INFERNO_CAPE = new ClientAsset.ResourceTexture(
        Identifier.fromNamespaceAndPath("noxclient", "textures/cape/nox_cape_inferno.png"));
    private static final ClientAsset.Texture VOID_CAPE = new ClientAsset.ResourceTexture(
        Identifier.fromNamespaceAndPath("noxclient", "textures/cape/nox_cape_void.png"));
    private static ClientAsset.Texture animCape, customCape;

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/client/renderer/SubmitNodeCollector;I"
        + "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
        at = @At("HEAD"))
    private void nox$swapCape(PoseStack pose, SubmitNodeCollector collector, int light,
                              AvatarRenderState state, float f1, float f2, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !Cosmetics.capeActive()) return;
        PlayerSkin skin = state.skin, own = mc.player.getSkin();
        if (skin == null || own == null) return;
        if (!skin.body().texturePath().equals(own.body().texturePath())) return;

        ClientAsset.Texture cape = switch (Cosmetics.INSTANCE.capeStyle.get()) {
            case "Inferno" -> INFERNO_CAPE;
            case "Void" -> VOID_CAPE;
            case "Animated" -> {
                if (animCape == null)
                    animCape = new ClientAsset.ResourceTexture(AnimatedCape.id());
                yield animCape;
            }
            case "Custom" -> {
                if (!CustomCape.loaded() && CustomCape.reload() != null) yield NOX_CAPE;
                if (customCape == null)
                    customCape = new ClientAsset.ResourceTexture(CustomCape.id());
                yield customCape;
            }
            default -> NOX_CAPE;
        };
        state.skin = new PlayerSkin(skin.body(), cape, cape, skin.model(), skin.secure());
        // cloth cape (NoxCapeLayer) draws instead — hide the rigid vanilla one
        state.showCape = !Cosmetics.physCapeActive();
    }
}
