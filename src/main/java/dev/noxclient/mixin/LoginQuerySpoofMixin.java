package dev.noxclient.mixin;

import dev.noxclient.util.Opsec;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.network.protocol.login.custom.DiscardedQueryAnswerPayload;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Login-phase fingerprint guard. The login listener does NOT extend
 * ClientCommonPacketListenerImpl, so PayloadSpoofMixin can't see its
 * ServerboundCustomQueryAnswerPacket. While Opsec.stripAll is on we
 * pre-empt the whole handler: answer exactly like vanilla does for an
 * unknown query — DiscardedQueryAnswerPayload — before any mod gets to
 * answer differently.
 */
@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class LoginQuerySpoofMixin {
    @Shadow @Final private Connection connection;

    @Inject(method = "handleCustomQuery", at = @At("HEAD"), cancellable = true)
    private void nox$discardQuery(ClientboundCustomQueryPacket p, CallbackInfo ci) {
        if (!Opsec.stripAll) return;
        connection.send(new ServerboundCustomQueryAnswerPacket(
            p.transactionId(), DiscardedQueryAnswerPayload.INSTANCE));
        ci.cancel();
    }
}
