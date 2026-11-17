package dev.noxclient.mixin;

import dev.noxclient.module.misc.StreamerMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Streamer-mode skin mask — own player renders with the default skin. */
@Mixin(AbstractClientPlayer.class)
public class StreamerSkinMixin {
    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void nox$skin(CallbackInfoReturnable<PlayerSkin> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        StreamerMode s = StreamerMode.get();
        if (s != null && s.isEnabled() && s.hideSkin.on() && self == Minecraft.getInstance().player)
            cir.setReturnValue(DefaultPlayerSkin.getDefaultSkin());
    }
}
