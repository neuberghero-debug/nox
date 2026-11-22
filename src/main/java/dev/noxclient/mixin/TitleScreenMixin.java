package dev.noxclient.mixin;

import dev.noxclient.NoxClient;
import dev.noxclient.gui.Gargantua;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the panorama with the NOX Gargantua sky — animated accretion disk,
 * lensed far-side arcs, photon ring, doppler beaming, starfield.
 * Also fires the one-time invite popup on first menu show.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void nox$sky(GuiGraphicsExtractor g, int mx, int my, float dt, CallbackInfo ci) {
        ci.cancel();
        Screen s = (Screen) (Object) this;
        // real raymarched shader sky first; video frames then procedural fallback
        if (!dev.noxclient.render.MenuSky.render(g, s.width, s.height)
            && !dev.noxclient.gui.AnimatedSky.render(g, s.width, s.height))
            Gargantua.render(g, s.width, s.height);
        g.fill(0, 0, s.width, s.height, 0x50000000);
        var inst = NoxClient.INSTANCE;
        if (inst == null || inst.modules == null) return;
        var lm = inst.modules.get("LyricsSky");
        if (lm instanceof dev.noxclient.module.render.LyricsSky ls && ls.isEnabled() && ls.titleScreen.on())
            dev.noxclient.gui.LyricsHud.render(g, s.width / 2, (int) (s.height * 0.78),
                (int) (double) ls.lines.get());
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void nox$inviteOnce(CallbackInfo ci) {
        var inst = NoxClient.INSTANCE;
        if (inst == null) return;
        var cfg = inst.config;
        if (cfg != null && !cfg.inviteShown) {
            cfg.inviteShown = true;
            cfg.save();
            Minecraft.getInstance().setScreenAndShow(
                new dev.noxclient.gui.NoxInviteScreen((TitleScreen) (Object) this));
            return;
        }
        // second first-run prompt: telemetry opt-in — PrivacyScreen records the
        // choice to nox/privacy.json and never asks again
        if (!dev.noxclient.telemetry.Telemetry.asked()) {
            Minecraft.getInstance().setScreenAndShow(new dev.noxclient.gui.PrivacyScreen());
        }
    }
}
