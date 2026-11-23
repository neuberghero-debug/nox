package dev.noxclient.mixin;

import dev.noxclient.NoxClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inbound packet tap — the whole base-finding pipeline hangs off this.
 * Everything here is purely passive: we read what the server sends anyway
 * (sounds, block events/updates, entity spawns, chunk block-entity tags,
 * light updates, explosions) — zero extra packets leave the client, so the
 * finders are undetectable by construction.
 */
@Mixin(ClientPacketListener.class)
public abstract class GamePacketMixin {

    @Inject(method = "handleSoundEvent", at = @At("HEAD"))
    private void nox$sound(ClientboundSoundPacket p, CallbackInfo ci) { dispatch(p); }

    @Inject(method = "handleSoundEntityEvent", at = @At("HEAD"))
    private void nox$entitySound(ClientboundSoundEntityPacket p, CallbackInfo ci) { dispatch(p); }

    @Inject(method = "handleLevelEvent", at = @At("HEAD"))
    private void nox$levelEvent(ClientboundLevelEventPacket p, CallbackInfo ci) { dispatch(p); }

    @Inject(method = "handleBlockEvent", at = @At("HEAD"))
    private void nox$blockEvent(ClientboundBlockEventPacket p, CallbackInfo ci) { dispatch(p); }

    @Inject(method = "handleBlockUpdate", at = @At("HEAD"))
    private void nox$blockUpdate(ClientboundBlockUpdatePacket p, CallbackInfo ci) { dispatch(p); }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("HEAD"))
    private void nox$sectionUpdate(ClientboundSectionBlocksUpdatePacket p, CallbackInfo ci) { dispatch(p); }

    @Inject(method = "handleBlockDestruction", at = @At("HEAD"))
    private void nox$destruction(ClientboundBlockDestructionPacket p, CallbackInfo ci) { dispatch(p); }

    @Inject(method = "handleAddEntity", at = @At("HEAD"))
    private void nox$addEntity(ClientboundAddEntityPacket p, CallbackInfo ci) { dispatch(p); }

    @Inject(method = "handleBlockEntityData", at = @At("HEAD"))
    private void nox$beData(ClientboundBlockEntityDataPacket p, CallbackInfo ci) { dispatch(p); }

    @Inject(method = "handleLevelChunkWithLight", at = @At("HEAD"))
    private void nox$chunk(ClientboundLevelChunkWithLightPacket p, CallbackInfo ci) { dispatch(p); }

    @Inject(method = "handleLightUpdatePacket", at = @At("HEAD"))
    private void nox$light(ClientboundLightUpdatePacket p, CallbackInfo ci) { dispatch(p); }

    @Inject(method = "handleParticleEvent", at = @At("HEAD"))
    private void nox$particle(ClientboundLevelParticlesPacket p, CallbackInfo ci) { dispatch(p); }

    @Inject(method = "handleTakeItemEntity", at = @At("HEAD"))
    private void nox$takeItem(ClientboundTakeItemEntityPacket p, CallbackInfo ci) { dispatch(p); }

    @Inject(method = "handleExplosion", at = @At("HEAD"))
    private void nox$explosion(ClientboundExplodePacket p, CallbackInfo ci) { dispatch(p); }

    @Inject(method = "handleAddTransientBlockPacket", at = @At("HEAD"))
    private void nox$transient(ClientboundAddTransientBlockPacket p, CallbackInfo ci) { dispatch(p); }

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void nox$entityEvent(ClientboundEntityEventPacket p, CallbackInfo ci) { dispatch(p); }

    @Inject(method = "handleSetEntityData", at = @At("HEAD"))
    private void nox$entityData(ClientboundSetEntityDataPacket p, CallbackInfo ci) { dispatch(p); }

    @Inject(method = "handleDamageEvent", at = @At("HEAD"))
    private void nox$damage(ClientboundDamageEventPacket p, CallbackInfo ci) { dispatch(p); }

    @Inject(method = "handleSetEntityPassengersPacket", at = @At("HEAD"))
    private void nox$passengers(ClientboundSetPassengersPacket p, CallbackInfo ci) { dispatch(p); }

    @Inject(method = "handleAnimate", at = @At("HEAD"))
    private void nox$animate(ClientboundAnimatePacket p, CallbackInfo ci) { dispatch(p); }

    private void dispatch(Packet<?> p) {
        var n = NoxClient.INSTANCE;
        if (n != null && n.modules != null) {
            try { n.modules.dispatchPacket(p); } catch (Throwable ignored) {}
        }
    }
}
