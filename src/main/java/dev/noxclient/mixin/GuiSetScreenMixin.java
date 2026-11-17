package dev.noxclient.mixin;

import dev.noxclient.gui.TamperBlockScreen;
import dev.noxclient.util.TamperGuard;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Hard tamper lock: once TamperGuard flags the jar, EVERY screen change is
 * rewritten to TamperBlockScreen — including setScreen(null) (the path back
 * into the game). Nothing can reach title, world, menus or disconnects.
 * The block screen itself passes through so init/re-show works.
 */
@Mixin(Gui.class)
public abstract class GuiSetScreenMixin {

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen nox$tamperBlock(Screen s) {
        if (TamperGuard.compromised() && !(s instanceof TamperBlockScreen))
            return new TamperBlockScreen();
        return s;
    }
}
