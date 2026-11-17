package dev.noxclient.mixin;

import dev.noxclient.module.donutsmp.OreSim;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Feeds chunk load/unload events to OreSim's seed-simulation. */
@Mixin(ClientChunkCache.class)
public abstract class OreSimChunkMixin {
    @Inject(method = "replaceWithPacketData", at = @At("RETURN"))
    private void nox$chunkLoaded(int x, int z, ClientboundLevelChunkPacketData data,
                               CallbackInfoReturnable<LevelChunk> cir) {
        OreSim.onChunkLoad(cir.getReturnValue());
    }

    @Inject(method = "drop", at = @At("HEAD"))
    private void nox$chunkDropped(ChunkPos pos, CallbackInfo ci) {
        OreSim.onChunkUnload(pos);
    }
}
