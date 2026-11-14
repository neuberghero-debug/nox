package dev.noxclient.mixin;

import dev.noxclient.gui.theme.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Re-skins every vanilla Button: flat dark body, accent border on hover,
 * inactive state dimmed — consistent with the NOX window theme.
 */
@Mixin(AbstractButton.class)
public abstract class ButtonMixin {
    @Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void nox$buttonSkin(GuiGraphicsExtractor g, CallbackInfo ci) {
        ci.cancel();
        Theme t = Theme.current();
        AbstractWidget w = (AbstractWidget) (Object) this;
        int x = w.getX(), y = w.getY(), wdt = w.getWidth(), h = w.getHeight();
        if (!w.active) {
            g.fill(x, y, x + wdt, y + h, 0xFF15151C);
            g.outline(x, y, wdt, h, 0xFF333340);
        } else if (w.isHoveredOrFocused()) {
            g.fill(x, y, x + wdt, y + h, 0xFF1E1E2E);
            g.outline(x, y, wdt, h, t.accent());
            g.fill(x, y, x + wdt, y + 1, t.accent());
        } else {
            g.fill(x, y, x + wdt, y + h, 0xFF14141E);
            g.outline(x, y, wdt, h, t.accent() & 0x88FFFFFF);
        }
    }
}
