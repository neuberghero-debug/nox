package dev.noxclient.mixin;

import dev.noxclient.NoxClient;
import dev.noxclient.gui.Gargantua;
import dev.noxclient.gui.ds.DS;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NOX title screen — animated sky backdrop + minimalist NOX header in place
 * of the MINECRAFT logo: centered mark + letterspaced wordmark + accent
 * hairline. Vanilla keeps buttons, splash text and copyright — MC style.
 * Footer adds version/build and the session account.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {

    private static final Identifier NOX_MARK =
        Identifier.fromNamespaceAndPath("noxclient", "textures/icon.png");

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void nox$sky(GuiGraphicsExtractor g, int mx, int my, float dt, CallbackInfo ci) {
        ci.cancel();
        Screen s = (Screen) (Object) this;
        // real raymarched shader sky first; video frames then procedural fallback
        if (!dev.noxclient.gui.AnimatedSky.render(g, s.width, s.height)
            && !dev.noxclient.render.MenuSky.render(g, s.width, s.height))
            Gargantua.render(g, s.width, s.height);
        g.fill(0, 0, s.width, s.height, 0x38050810);

        var inst = NoxClient.INSTANCE;
        if (inst == null || inst.modules == null) return;
        var lm = inst.modules.get("LyricsSky");
        if (lm instanceof dev.noxclient.module.render.LyricsSky ls && ls.isEnabled() && ls.titleScreen.on())
            dev.noxclient.gui.LyricsHud.render(g, s.width / 2, (int) (s.height * 0.78),
                (int) (double) ls.lines.get());
    }

    /** MINECRAFT logo → minimalist NOX header (mark + wordmark + hairline). */
    @Redirect(method = "extractRenderState",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/LogoRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IF)V"))
    private void nox$logo(LogoRenderer lr, GuiGraphicsExtractor g, int sw, float alpha) {
        int a = Math.round(255 * Math.clamp(alpha, 0f, 1f));
        if (a == 0) return;
        var font = Minecraft.getInstance().font;
        int cx = sw / 2;
        int mark = 26;
        String brand = "N O X";
        // wordmark at 2x font scale — reads ~16px tall next to a 26px mark
        int bw = font.width(brand) * 2;
        int total = mark + 10 + bw;
        int x = cx - total / 2, y = 36;
        g.blit(NOX_MARK, x, y - 2, x + mark, y - 2 + mark, 0f, 1f, 0f, 1f);
        var pose = g.pose();
        pose.pushMatrix();
        pose.scale(2f, 2f);
        dev.noxclient.gui.Gui.textBold(g, font, brand,
            (x + mark + 10) / 2, (y + 6) / 2, DS.withAlpha(0xFFE8E8F0, a));
        pose.popMatrix();
        // accent hairline, centered under the lockup
        int lw = 96, lx = cx - lw / 2;
        g.fill(lx, y + mark + 6, lx + lw, y + mark + 7,
            DS.withAlpha(DS.accent(), Math.min(a, 160)));
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
            Minecraft.getInstance().setScreenAndShow(
                new dev.noxclient.gui.PrivacyScreen((TitleScreen) (Object) this));
        }
    }
}
