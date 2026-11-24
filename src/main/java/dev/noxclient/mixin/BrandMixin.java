package dev.noxclient.mixin;

import dev.noxclient.util.AntiCheats;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the server brand (clientbound) for the AC badge in the GUI. */
@Mixin(ClientPacketListener.class)
public abstract class BrandMixin {
    @Inject(method = "handleCustomPayload", at = @At("HEAD"))
    private void nox$brand(CustomPacketPayload payload, CallbackInfo ci) {
        AntiCheats.onChannel(payload.type().id().toString());
        if (payload instanceof BrandPayload b) AntiCheats.onBrand(b.brand());
    }
}
