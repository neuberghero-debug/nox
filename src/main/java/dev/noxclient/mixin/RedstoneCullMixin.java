package dev.noxclient.mixin;

import dev.noxclient.module.donutsmp.FarmOptimizer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FarmOptimizer redstone hide — section bakers see air where redstone dust
 * (and optionally components) would be. Levers, buttons and lamps keep
 * rendering; a mode change triggers a full section rebuild.
 */
@Mixin(RenderSectionRegion.class)
public abstract class RedstoneCullMixin {
    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void nox$hideRedstone(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        try {
            int mode = FarmOptimizer.hideRedstone();
            if (mode == 0) return;
            var st = cir.getReturnValue();
            if (st == null) return;
            var b = st.getBlock();
            if (b == Blocks.REDSTONE_WIRE
                || (mode == 2 && (b == Blocks.REPEATER || b == Blocks.COMPARATOR
                    || b == Blocks.OBSERVER || b == Blocks.PISTON || b == Blocks.STICKY_PISTON)))
                cir.setReturnValue(Blocks.AIR.defaultBlockState());
        } catch (Throwable ignored) {}
    }
}
