package dev.noxclient.mixin;

import dev.noxclient.module.donutsmp.SoundFind;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Passive base-finding taps: block events, level events, sounds,
 * entity sounds, particles — all reach us anyway, we just listen.
 */
@Mixin(ClientPacketListener.class)
public abstract class SoundSniffMixin {
    @Inject(method = "handleBlockEvent", at = @At("TAIL"))
    private void nox$blockEvent(ClientboundBlockEventPacket p, CallbackInfo ci) {
        SoundFind.onBlockEvent(p);
    }

    @Inject(method = "handleLevelEvent", at = @At("TAIL"))
    private void nox$levelEvent(ClientboundLevelEventPacket p, CallbackInfo ci) {
        SoundFind.onLevelEvent(p);
    }

    @Inject(method = "handleSoundEvent", at = @At("TAIL"))
    private void nox$soundEvent(ClientboundSoundPacket p, CallbackInfo ci) {
        SoundFind.onSound(p);
    }

    @Inject(method = "handleSoundEntityEvent", at = @At("TAIL"))
    private void nox$entitySoundEvent(ClientboundSoundEntityPacket p, CallbackInfo ci) {
        SoundFind.onEntitySound(p);
    }

    @Inject(method = "handleParticleEvent", at = @At("TAIL"))
    private void nox$particleEvent(ClientboundLevelParticlesPacket p, CallbackInfo ci) {
        SoundFind.onParticles(p);
    }
}
