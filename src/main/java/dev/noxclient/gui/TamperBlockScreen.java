package dev.noxclient.gui;

import dev.noxclient.NoxClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Desktop;
import java.net.URI;

/**
 * Hard tamper block — replaces EVERY screen once TamperGuard flags the jar.
 * Enforced by GuiSetScreenMixin on Gui.setScreen: no path reaches the title,
 * a world, or any menu. The only ways out are downloading a clean build or
 * quitting. Nothing is dismissable — not ESC, not close, not a screen swap.
 */
public class TamperBlockScreen extends Screen {
    private long shownSince;

    public TamperBlockScreen() {
        super(Component.literal("NOX — integrity failure"));
        shownSince = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        int cx = width / 2, y = height / 2 + 62;
        addRenderableWidget(new NoxButton(cx - 172, y, 160, 20,
            "Download the real NOX", () -> {
            try {
                Desktop.getDesktop().browse(
                    new URI(dev.noxclient.discord.DiscordLink.get()));
            } catch (Exception ignored) {}
        }).primary());
        addRenderableWidget(new NoxButton(cx + 12, y, 160, 20,
            "Quit game", () -> minecraft.stop()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        g.fill(0, 0, width, height, 0xFF070309);
        int cx = width / 2, top = height / 2 - 86, w = 344, h = 204;
        Gui.shadow(g, cx - w / 2, top, w, h, 12);
        Gui.roundBorder(g, cx - w / 2, top, w, h, 12, 0xFFFF4444, 0xF2140A0E);
        Gui.fill(g, cx - w / 2 + 1, top + 1, cx + w / 2 - 1, top + 4, 0xFFFF4444);

        // pulsing warning mark
        float pulse = 0.75f + 0.25f * (float) Math.sin(
            (System.currentTimeMillis() - shownSince) / 220.0);
        int warn = ((int) (0xFF * pulse) << 24) | 0xFF4444;
        Gui.textCenter(g, font, "⚠", cx, top + 16, warn);
        Gui.textCenter(g, font, "NOX BLOCKED THIS GAME", cx, top + 34, 0xFFFF5555);
        Gui.textCenter(g, font, "This copy of " + NoxClient.NAME + " was MODIFIED after release.",
            cx, top + 56, 0xFFFFCCCC);
        Gui.textCenter(g, font, "It may contain a session / token stealer.", cx, top + 68, 0xFFFFCCCC);

        Gui.textCenter(g, font, "The game stays locked for your protection.", cx, top + 90, 0xFFFFFFFF);
        Gui.textCenter(g, font, "→ Do NOT log in here — change MC + Discord passwords.", cx, top + 108, 0xFFFFFFFF);
        Gui.textCenter(g, font, "→ Delete this jar and reinstall only from:", cx, top + 120, 0xFFFFFFFF);
        Gui.textCenter(g, font, dev.noxclient.discord.DiscordLink.get(), cx, top + 136, 0xFF6EC7FF);
        super.extractRenderState(g, mx, my, dt);
    }

    /* nothing may dismiss this screen — ESC ignored, onClose re-shows. */
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public void onClose() { minecraft.gui.setScreen(this); }
    @Override public boolean isPauseScreen() { return false; }
}
