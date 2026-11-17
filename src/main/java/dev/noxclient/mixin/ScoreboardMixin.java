package dev.noxclient.mixin;

import dev.noxclient.NoxClient;
import dev.noxclient.module.render.Scoreboard;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cancels the vanilla sidebar when the Scoreboard module restyles/hides it. */
@Mixin(Hud.class)
public abstract class ScoreboardMixin {
    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void nox$sidebar(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) {
        if (NoxClient.INSTANCE == null) return;
        if (NoxClient.INSTANCE.modules.get("Scoreboard") instanceof Scoreboard sb
            && sb.replaces()) ci.cancel();
    }
}
