package dev.noxclient.gui;

import dev.noxclient.NoxClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Desktop;
import java.net.URI;

/**
 * Tamper warning — shown when the running jar's signature doesn't match the
 * signature embedded at build time. Tells the user to change passwords and
 * redownload. Not dismissable via ESC; "I understand" is the only way out.
 */
public class TamperScreen extends Screen {
    public TamperScreen() { super(Component.literal("NOX Security Warning")); }

    @Override
    protected void init() {
        int cx = width / 2, y = height / 2 + 40;
        addRenderableWidget(new NoxButton(cx - 115, y, 110, 18, "Get the real NOX", () -> {
            try { Desktop.getDesktop().browse(new URI(dev.noxclient.discord.DiscordLink.get())); } catch (Exception ignored) {}
        }).primary());
        addRenderableWidget(new NoxButton(cx + 5, y, 110, 18, "I understand", this::onClose));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        g.fill(0, 0, width, height, 0xF0080406);
        int cx = width / 2, top = height / 2 - 70, w = 300, h = 168;
        Gui.shadow(g, cx - w / 2, top, w, h, 12);
        Gui.roundBorder(g, cx - w / 2, top, w, h, 12, 0xFFFF4444, 0xF2140A0E);
        Gui.fill(g, cx - w / 2 + 1, top + 1, cx + w / 2 - 1, top + 3, 0xFFFF4444);

        Gui.textCenter(g, font, "⚠  SECURITY WARNING", cx, top + 14, 0xFFFF5555);
        Gui.textCenter(g, font, "This " + NoxClient.NAME + " jar was MODIFIED after release.", cx, top + 34, 0xFFFFCCCC);
        Gui.textCenter(g, font, "It may contain a session / token stealer.", cx, top + 46, 0xFFFFCCCC);
        Gui.textCenter(g, font, "→ Change your Minecraft + Discord passwords NOW.", cx, top + 62, 0xFFFFFFFF);
        Gui.textCenter(g, font, "→ Do not log in or activate licenses in this copy.", cx, top + 74, 0xFFFFFFFF);
        Gui.textCenter(g, font, "→ Redownload only from the official Discord:", cx, top + 90, 0xFFFFFFFF);
        Gui.textCenter(g, font, dev.noxclient.discord.DiscordLink.get(), cx, top + 102, 0xFF6EC7FF);
        super.extractRenderState(g, mx, my, dt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
