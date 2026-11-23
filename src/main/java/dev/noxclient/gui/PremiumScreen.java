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
    private final Screen parent;
    private EditBox keyBox;
    private String status = "";
    private int statusColor = 0xFFFF6666;

    public PremiumScreen() { this(null); }
    public PremiumScreen(Screen parent) { super(Component.literal("NOX Premium")); this.parent = parent; }

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
        if (LicenseManager.premium())
            addRenderableWidget(new NoxButton(cx - 120, top + 166, 118, 16, "Deactivate",
                () -> { dev.noxclient.premium.PremiumVault.unload(); LicenseManager.deactivate(); status = "License deactivated"; statusColor = 0xFFFFCC55; }));
        addRenderableWidget(new NoxButton(cx - 120, top + 166, LicenseManager.premium() ? 118 : 240, 16,
            LicenseManager.premium() ? "Deactivate" : "Close",
            LicenseManager.premium()
                ? () -> { dev.noxclient.premium.PremiumVault.unload(); LicenseManager.deactivate(); status = "License deactivated"; statusColor = 0xFFFFCC55; }
                : this::onClose));
        if (LicenseManager.premium())
            addRenderableWidget(new NoxButton(cx + 2, top + 166, 118, 16, "Close", this::onClose));
    }

    private void activate() {
        switch (LicenseManager.unlock(keyBox.getValue())) {
            case OK -> {
                dev.noxclient.premium.PremiumVault.load();
                if (dev.noxclient.premium.PremiumVault.loaded()) {
                    dev.noxclient.NoxClient.INSTANCE.config.load();
                    status = "License activated — " + dev.noxclient.premium.PremiumVault.modules().size()
                        + " premium modules unlocked";
                } else {
                    status = "License activated — welcome to NOX Premium";
                }
                statusColor = 0xFF66FF99;
            }
            case EXPIRED -> { status = "Key expired"; statusColor = 0xFFFF6666; }
            case BAD_SIGNATURE -> { status = "Invalid signature — key not issued by NOX"; statusColor = 0xFFFF6666; }
            default -> { status = "Invalid key format (NOX1.…)"; statusColor = 0xFFFF6666; }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        Theme t = Theme.current();
        Gui.backdrop(g, width, height, 1f);
        int cx = width / 2, top = height / 2 - 96, w = 272, h = 200;
        int x0 = cx - w / 2;
        Gui.shadow(g, x0, top, w, h, 12);
        Gui.roundBorder(g, x0, top, w, h, 12, t.accent(), 0xF2101018);
        Gui.windowFx(x0, top, w, h, 12);
        Gui.windowFrost(x0, top, w, h, 12);

        Gui.textCenterBold(g, font, "★  NOX PREMIUM", cx, top + 12, t.accent());
        Gui.textCenter(g, font, "This module requires a Premium license.", cx, top + 30, t.text());
        Gui.textCenter(g, font, "Pay with crypto — open a ticket in the Discord.", cx, top + 44, 0xFF9C9CB8);
        Gui.textCenterBold(g, font, "BTC · LTC · ETH · SOL", cx, top + 58, t.accent());
        Gui.textCenter(g, font, "You get a NOX1 key instantly after payment.", cx, top + 72, 0xFF9C9CB8);
        Gui.textCenter(g, font, dev.noxclient.discord.DiscordLink.get(), cx, top + 86, 0xFF6677AA);
        Gui.fill(g, x0 + 16, top + 100, x0 + w - 16, top + 101, 0xFF2A2A3C);
        Gui.text(g, font, "License key", x0 + 16, top + 106, t.text());
        if (LicenseManager.premium()) {
            long days = LicenseManager.daysLeft();
            String st = "● active · " + days + "d left";
            int sw = Gui.width(font, st) + 12;
            Gui.pill(g, x0 + w - 16 - sw, top + 104, sw, 15, 0x3366D9A0);
            Gui.text(g, font, st, x0 + w - 16 - sw + 6, top + 108, 0xFF66D9A0);
        }
        if (!status.isEmpty()) Gui.textCenter(g, font, status, cx, top + 190, statusColor);
        super.extractRenderState(g, mx, my, dt);
    }

    @Override
    public void onClose() { minecraft.setScreenAndShow(parent); }
    @Override
    public boolean isPauseScreen() { return false; }
}
