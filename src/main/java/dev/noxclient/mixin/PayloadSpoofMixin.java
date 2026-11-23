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
        if (packet instanceof ServerboundCustomPayloadPacket p) {
            if (p.payload() instanceof BrandPayload) {
                // always — the brand leaks "fabric" on login/config phase too,
                // and DonutSMP reads it. A vanilla client always says "vanilla".
                ci.cancel();
                // send on the raw Connection — going through this.send() would
                // re-enter this mixin and recurse forever
                connection.send(new ServerboundCustomPayloadPacket(new BrandPayload("vanilla")));
            } else if (ModSpoofer.active() && ModSpoofer.INSTANCE.stripAll.on()) {
                ci.cancel(); // fabric handshake / mod channels never leave the wire
            }
            return;
        }
        // login-phase fingerprint: the server can send custom queries and a
        // modded client answers differently than vanilla. With stripAll we
        // answer exactly what vanilla answers — DiscardedQueryAnswerPayload.
        if (ModSpoofer.active() && ModSpoofer.INSTANCE.stripAll.on()
            && packet instanceof net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket q
            && !(q.payload() instanceof net.minecraft.network.protocol.login.custom.DiscardedQueryAnswerPayload)) {
            ci.cancel();
            connection.send(new net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket(
                q.transactionId(),
                net.minecraft.network.protocol.login.custom.DiscardedQueryAnswerPayload.INSTANCE));
            return;
        }
        // locale leak: DonutSMP records game language at join (privacy policy
        // K) — a German locale behind a US VPN exit is a fingerprint mismatch.
        // Everything else in ClientInformation stays identical to vanilla.
        if (packet instanceof net.minecraft.network.protocol.common.ServerboundClientInformationPacket s
            && ModSpoofer.active() && !"en_us".equals(s.information().language())) {
            var i = s.information();
            ci.cancel();
            connection.send(new net.minecraft.network.protocol.common.ServerboundClientInformationPacket(
                new net.minecraft.server.level.ClientInformation(
                    "en_us", i.viewDistance(), i.chatVisibility(), i.chatColors(),
                    i.modelCustomisation(), i.mainHand(), i.textFilteringEnabled(),
                    i.allowsListing(), i.particleStatus())));
        }
    }
}
