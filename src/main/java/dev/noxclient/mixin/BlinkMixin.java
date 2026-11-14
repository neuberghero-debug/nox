package dev.noxclient.mixin;

import dev.noxclient.module.movement.Blink;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds Blink's packet queue instead of sending while it holds. */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class BlinkMixin {
    @Inject(method = "send", at = @At("HEAD"), cancellable = true)
    private void nox$buffer(Packet<?> packet, CallbackInfo ci) {
        // keep-alives bypass the queue entirely — buffering the reply made the
        // server drop us with "Timed out" after ~15s of blink
        if (packet instanceof net.minecraft.network.protocol.common.ServerboundKeepAlivePacket) return;
        if (Blink.holding()) {
            Blink.QUEUE.offer(packet);
            ci.cancel();
        }
    }
}
