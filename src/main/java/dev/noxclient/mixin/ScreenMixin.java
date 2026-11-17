package dev.noxclient.mixin;

import dev.noxclient.NoxClient;
import dev.noxclient.gui.Gui;
import dev.noxclient.gui.NoxWindowScreen;
import dev.noxclient.gui.theme.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Universal NOX chrome on EVERY screen: a thin accent line + footer with the
 * community invite, subtle and out of the way. Loading screens additionally
 * get the full brand block (title + bar).
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Shadow public int width;
    @Shadow public int height;
    @Shadow protected net.minecraft.client.gui.Font font;

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("TAIL"))
    private void nox$chrome(GuiGraphicsExtractor g, int mx, int my, float dt, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (self instanceof NoxWindowScreen || self instanceof dev.noxclient.gui.NoxInviteScreen
            || self instanceof dev.noxclient.gui.NoxClickGui) return;
        Theme t = Theme.current();

        boolean loading = self instanceof ProgressScreen
            || self instanceof LevelLoadingScreen
            || self instanceof ConnectScreen
            || self.getClass().getSimpleName().contains("Receiving");

        if (loading) {
            // brand block in the top quarter — vanilla status text sits at
            // screen center, so it must stay well clear of that band
            int cx = width / 2, top = height / 5;
            g.fill(cx - 70, top, cx + 70, top + 2, t.accent());
            g.centeredText(font, "N O X", cx, top + 8, t.accent());
            g.centeredText(font, "DonutSMP client", cx, top + 20, t.text());
        }

        // subtle footer: accent hairline bottom, invite top-right (no overlap)
        g.fill(0, height - 1, width, height, t.accent());
        String lic = dev.noxclient.premium.LicenseManager.premium()
            ? "PREMIUM ✓" : "FREE";
        int licCol = dev.noxclient.premium.LicenseManager.premium()
            ? 0xFF7BE0A8 : 0x66FFFFFF;
        String tag = "NOX " + NoxClient.VERSION + "  ·  " + dev.noxclient.discord.DiscordLink.shortLabel();
        int tx = width - 6 - Gui.width(font, tag);
        Gui.text(g, font, tag, tx, 4, 0x99FFFFFF);
        Gui.text(g, font, lic, tx - 10 - Gui.width(font, lic), 4, licCol);
    }
}
