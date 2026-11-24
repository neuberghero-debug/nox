package dev.noxclient.mixin;

import dev.noxclient.module.donutsmp.ActivityFind;
import dev.noxclient.module.donutsmp.SoundFind;
import dev.noxclient.module.donutsmp.VanishSense;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
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
    // every tap is isolated — a module bug must never kill packet handling
    // (an uncaught throw here would disconnect the client)
    @Inject(method = "handleBlockEvent", at = @At("TAIL"))
    private void nox$blockEvent(ClientboundBlockEventPacket p, CallbackInfo ci) {
        try { SoundFind.onBlockEvent(p); } catch (Throwable ignored) {}
        try { VanishSense.onBlockEvent(p); } catch (Throwable ignored) {}
    }

    @Inject(method = "handleLevelEvent", at = @At("TAIL"))
    private void nox$levelEvent(ClientboundLevelEventPacket p, CallbackInfo ci) {
        try { SoundFind.onLevelEvent(p); } catch (Throwable ignored) {}
    }

    @Inject(method = "handleSoundEvent", at = @At("TAIL"))
    private void nox$soundEvent(ClientboundSoundPacket p, CallbackInfo ci) {
        try { SoundFind.onSound(p); } catch (Throwable ignored) {}
    }

    @Inject(method = "handleSoundEntityEvent", at = @At("TAIL"))
    private void nox$entitySoundEvent(ClientboundSoundEntityPacket p, CallbackInfo ci) {
        try { SoundFind.onEntitySound(p); } catch (Throwable ignored) {}
    }

    @Inject(method = "handleParticleEvent", at = @At("TAIL"))
    private void nox$particleEvent(ClientboundLevelParticlesPacket p, CallbackInfo ci) {
        try { SoundFind.onParticles(p); } catch (Throwable ignored) {}
    }

    // ---- ActivityFind taps — HEAD for block changes so the OLD state is
    // still in the chunk (TAIL would already have the new state applied) ----

    @Inject(method = "handleBlockUpdate", at = @At("HEAD"))
    private void nox$blockUpdate(ClientboundBlockUpdatePacket p, CallbackInfo ci) {
        try {
            var level = Minecraft.getInstance().level;
            if (level != null)
                ActivityFind.onBlockUpdate(p, level.getBlockState(p.getPos()));
        } catch (Throwable ignored) {}
        try { VanishSense.onBlockUpdate(p); } catch (Throwable ignored) {}
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("HEAD"))
    private void nox$sectionBlocks(ClientboundSectionBlocksUpdatePacket p, CallbackInfo ci) {
        try { ActivityFind.onSectionBlocks(p); } catch (Throwable ignored) {}
    }

    @Inject(method = "handleExplosion", at = @At("TAIL"))
    private void nox$explosion(ClientboundExplodePacket p, CallbackInfo ci) {
        try { ActivityFind.onExplosion(p); } catch (Throwable ignored) {}
    }

    @Inject(method = "handleBlockEntityData", at = @At("TAIL"))
    private void nox$blockEntityData(ClientboundBlockEntityDataPacket p, CallbackInfo ci) {
        try { ActivityFind.onBlockEntityData(p); } catch (Throwable ignored) {}
    }

    @Inject(method = "handleTakeItemEntity", at = @At("TAIL"))
    private void nox$takeItem(ClientboundTakeItemEntityPacket p, CallbackInfo ci) {
        try { ActivityFind.onTakeItem(p); } catch (Throwable ignored) {}
    }

    @Inject(method = "handleDamageEvent", at = @At("TAIL"))
    private void nox$damageEvent(ClientboundDamageEventPacket p, CallbackInfo ci) {
        try { ActivityFind.onDamage(p); } catch (Throwable ignored) {}
    }
}
