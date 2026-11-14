package dev.noxclient.mixin;

import dev.noxclient.module.misc.ModSpoofer;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Intercepts outgoing custom payloads: spoofs brand, optionally strips all. */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class PayloadSpoofMixin {
    @Shadow @Final protected Connection connection;

    @Inject(method = "send", at = @At("HEAD"), cancellable = true)
    private void nox$spoof(Packet<?> packet, CallbackInfo ci) {
        if (!ModSpoofer.active() || !(packet instanceof ServerboundCustomPayloadPacket p)) return;
        if (p.payload() instanceof BrandPayload) {
            ci.cancel();
            // send on the raw Connection — going through this.send() would
            // re-enter this mixin and recurse forever
            connection.send(new ServerboundCustomPayloadPacket(new BrandPayload("vanilla")));
        } else if (ModSpoofer.INSTANCE.stripAll.on()) {
            ci.cancel(); // fabric handshake / mod channels never leave the wire
        }
    }
}
