package dev.noxclient.mixin;

import dev.noxclient.alts.AltManager;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import net.minecraft.network.BandwidthDebugMonitor;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Injects the alt-manager proxy into every outbound connection (join + ping). */
@Mixin(Connection.class)
public class ConnectionMixin {
    @Inject(method = "configureSerialization", at = @At("HEAD"))
    private static void nox$proxy(ChannelPipeline pipeline, PacketFlow flow,
                                  boolean memoryLeak, BandwidthDebugMonitor mon, CallbackInfo ci) {
        if (flow != PacketFlow.CLIENTBOUND) return;
        var h = AltManager.proxyHandler(pipeline.channel() instanceof SocketChannel);
        if (h != null) pipeline.addFirst("nox_proxy", h);
    }
}
