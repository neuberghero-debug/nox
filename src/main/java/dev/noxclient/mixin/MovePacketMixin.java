package dev.noxclient.mixin;

import dev.noxclient.util.MoveGuard;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds MoveGuard — mirrors the server's receivedPositionThisTick rule: at
 * most one hasPosition() move packet between two ServerboundClientTickEnd
 * packets. Second-in-window packets are held and released into the next
 * window at {@link #nox$tickEnd}. REPLACE sends via the raw {@link #connection}
 * so the rewritten packet does not re-enter this hook.
 * Priority 1100 so it runs after BlinkMixin.
 */
@Mixin(value = ClientCommonPacketListenerImpl.class, priority = 1100)
public abstract class MovePacketMixin {
    @Shadow @Final protected Connection connection;

    @Inject(method = "send", at = @At("HEAD"), cancellable = true)
    private void nox$moveGuard(Packet<?> packet, CallbackInfo ci) {
        // already cancelled upstream (Blink queued it) — decide() would mark
        // the window used for a packet that never leaves the wire
        if (ci.isCancelled()) return;
        if (!(packet instanceof ServerboundMovePlayerPacket p)) return;
        switch (MoveGuard.decide(p)) {
            case CANCEL -> ci.cancel();
            case REPLACE -> {
                ci.cancel();
                connection.send(MoveGuard.offsetCopy(p));
            }
            case PASS -> { }
        }
    }

    @Inject(method = "send", at = @At("TAIL"))
    private void nox$tickEnd(Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof ServerboundClientTickEndPacket && !ci.isCancelled())
            MoveGuard.tickEnd();
    }
}
