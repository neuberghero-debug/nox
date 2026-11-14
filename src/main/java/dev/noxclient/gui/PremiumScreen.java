package dev.noxclient.gui;

import dev.noxclient.NoxClient;
import dev.noxclient.gui.theme.Theme;
import dev.noxclient.premium.LicenseManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Desktop;
import java.net.URI;

/**
 * Premium unlock — crypto payment flow. Panel lists accepted coins, points at
 * the Discord ticket system, and takes a signed NOX1 license key for instant
 * offline activation.
 */
public class PremiumScreen extends Screen {
    private EditBox keyBox;
    private String status = "";
    private int statusColor = 0xFFFF6666;

    public PremiumScreen() { super(Component.literal("NOX Premium")); }

    @Override
    protected void init() {
        int cx = width / 2, top = height / 2 - 96;
        keyBox = new EditBox(font, cx - 120, top + 118, 240, 18, Component.literal("License key"));
        keyBox.setMaxLength(256);
        addRenderableWidget(keyBox);
        addRenderableWidget(new NoxButton(cx - 120, top + 142, 118, 18, "Activate", this::activate).primary());
        addRenderableWidget(new NoxButton(cx + 2, top + 142, 118, 18, "Discord", () -> {
            try { Desktop.getDesktop().browse(new URI(dev.noxclient.discord.DiscordLink.get())); } catch (Exception ignored) {}
        }));
        addRenderableWidget(new NoxButton(cx - 120, top + 166, 240, 16, "Close", this::onClose));
    }

    private void activate() {
        switch (LicenseManager.unlock(keyBox.getValue())) {
            case OK -> { status = "License activated — welcome to NOX Premium"; statusColor = 0xFF66FF99; }
            case EXPIRED -> { status = "Key expired"; statusColor = 0xFFFF6666; }
            case BAD_SIGNATURE -> { status = "Invalid signature — key not issued by NOX"; statusColor = 0xFFFF6666; }
            default -> { status = "Invalid key format (NOX1.…)"; statusColor = 0xFFFF6666; }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        Theme t = Theme.current();
        g.fill(0, 0, width, height, 0xA0040408);
        int cx = width / 2, top = height / 2 - 96, w = 272, h = 200;
        int x0 = cx - w / 2;
        Gui.shadow(g, x0, top, w, h, 12);
        Gui.roundBorder(g, x0, top, w, h, 12, t.accent(), 0xF2101018);

        Gui.textCenterBold(g, font, "★  NOX PREMIUM", cx, top + 12, t.accent());
        Gui.textCenter(g, font, "This module requires a Premium license.", cx, top + 30, t.text());
        Gui.textCenter(g, font, "Pay with crypto — open a ticket in the Discord.", cx, top + 44, 0xFF9C9CB8);
        Gui.textCenterBold(g, font, "BTC · LTC · ETH · SOL", cx, top + 58, t.accent());
        Gui.textCenter(g, font, "You get a NOX1 key instantly after payment.", cx, top + 72, 0xFF9C9CB8);
        Gui.textCenter(g, font, dev.noxclient.discord.DiscordLink.get(), cx, top + 86, 0xFF6677AA);
        g.fill(x0 + 16, top + 100, x0 + w - 16, top + 101, 0xFF2A2A3C);
        Gui.text(g, font, "License key", x0 + 16, top + 106, t.text());
        if (!status.isEmpty()) Gui.textCenter(g, font, status, cx, top + 190, statusColor);
        super.extractRenderState(g, mx, my, dt);
    }

    @Override
    public void onClose() { minecraft.setScreenAndShow(null); }
    @Override
    public boolean isPauseScreen() { return false; }
}
