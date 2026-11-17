package dev.noxclient.mixin;

import dev.noxclient.gui.AltManagerScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds an "Accounts" tab to the multiplayer screen header (top-right), opening
 * the NOX account manager (token login, offline alts, per-connection proxy).
 */
@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerMixin extends Screen {

    protected JoinMultiplayerMixin() { super(Component.empty()); }

    @Inject(method = "init", at = @At("TAIL"))
    private void nox$accountsTab(CallbackInfo ci) {
        addRenderableWidget(Button.builder(Component.literal("Accounts"),
                b -> minecraft.setScreenAndShow(new AltManagerScreen(this)))
            .bounds(8, 9, 96, 20)
            .build());
    }
}
