package dev.noxclient.mixin;

import dev.noxclient.stats.Stats;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stats hooks: block break counter (Modul 26.1). */
@Mixin(MultiPlayerGameMode.class)
public abstract class StatsHooksMixin {
    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void nox$broken(net.minecraft.core.BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) Stats.onBlockBroken();
    }
}
