package dev.noxclient.mixin;

import dev.noxclient.module.misc.StreamerMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Streamer-mode name masking — lives on Player (declares getName/
 * getDisplayName); only client players are masked.
 */
@Mixin(Player.class)
public class StreamerMixin {
    private boolean nox$mask() {
        Player self = (Player) (Object) this;
        if (!(self instanceof AbstractClientPlayer acp)) return false;
        StreamerMode s = StreamerMode.get();
        if (s == null || !s.isEnabled() || !s.hideName.on()) return false;
        return s.allPlayers.on() || acp == Minecraft.getInstance().player;
    }

    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void nox$name(CallbackInfoReturnable<Component> cir) {
        if (nox$mask()) cir.setReturnValue(Component.literal(StreamerMode.fakeName()));
    }

    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void nox$display(CallbackInfoReturnable<Component> cir) {
        if (nox$mask()) cir.setReturnValue(Component.literal(StreamerMode.fakeName()));
    }
}
