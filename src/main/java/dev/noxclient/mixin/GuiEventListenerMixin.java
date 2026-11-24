package dev.noxclient.mixin;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * HUD controls (Spotify buttons) get first shot at clicks. mouseClicked is an
 * interface default in 26.3, so the hook lives on GuiEventListener — it fires
 * for screens AND children, but the rect check inside keeps it free.
 */
@Mixin(GuiEventListener.class)
public interface GuiEventListenerMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    default void nox$hudClick(MouseButtonEvent e, boolean dbl,
                              CallbackInfoReturnable<Boolean> ci) {
        try {
            if (dev.noxclient.gui.NoxOverlay.onHudClick(e.x(), e.y()))
                ci.setReturnValue(true);
        } catch (Throwable ignored) {}
    }
}
