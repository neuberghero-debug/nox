package dev.noxclient.mixin;

import dev.noxclient.util.Opsec;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Baseline opsec on every outbound custom payload — always active:
 * brand replies "vanilla", non-minecraft channels are stripped.
 * (util/Opsec owns the rules; the Multiplexer screen exposes the extras.)
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class PayloadSpoofMixin {
    @Shadow @Final protected Connection connection;

    @Inject(method = "send", at = @At("HEAD"), cancellable = true)
    private void nox$spoof(Packet<?> packet, CallbackInfo ci) {
        // locale leak: the server records game language at join — a foreign
        // locale behind a mismatched VPN exit is a fingerprint. Rewrite to
        // en_us, keep every other ClientInformation field identical.
        if (packet instanceof ServerboundClientInformationPacket s
            && !"en_us".equals(s.information().language())) {
            var i = s.information();
            ci.cancel();
            connection.send(new ServerboundClientInformationPacket(
                new net.minecraft.server.level.ClientInformation(
                    "en_us", i.viewDistance(), i.chatVisibility(), i.chatColors(),
                    i.modelCustomisation(), i.mainHand(), i.textFilteringEnabled(),
                    i.allowsListing(), i.particleStatus())));
            return;
        }
        if (!(packet instanceof ServerboundCustomPayloadPacket p)) return;
        if (Opsec.isBrand(p.payload())) {
            ci.cancel();
            // send on the raw Connection — going through this.send() would
            // re-enter this mixin and recurse forever
            connection.send(new ServerboundCustomPayloadPacket(new BrandPayload("vanilla")));
        } else if (Opsec.strip(p.payload())) {
            ci.cancel(); // fabric handshake / mod channels never leave the wire
        }
    }
}
