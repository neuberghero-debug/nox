package dev.noxclient.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MultiPlayerGameMode.class)
public interface GameModeAccessor {
    @Accessor("destroyDelay") int nox$getDestroyDelay();
    @Accessor("destroyDelay") void nox$setDestroyDelay(int v);
}
