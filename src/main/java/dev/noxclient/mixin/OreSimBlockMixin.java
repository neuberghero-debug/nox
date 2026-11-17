package dev.noxclient.mixin;

import dev.noxclient.module.donutsmp.OreSim;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Air-recheck: mined/broken blocks invalidate simulated ore markers. */
@Mixin(ClientPacketListener.class)
public abstract class OreSimBlockMixin {
    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    private void nox$blockUpdated(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        OreSim.onBlockUpdate(packet.getPos(), packet.getBlockState());
    }
}
