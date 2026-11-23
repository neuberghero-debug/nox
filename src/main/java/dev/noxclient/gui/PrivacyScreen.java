package dev.noxclient.gui;

import dev.noxclient.gui.ds.DS;
import dev.noxclient.telemetry.Telemetry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * First-start telemetry consent (Modul 20.1). Shown once; the choice is
 * persisted to nox/privacy.json. Default is OFF — declining needs no dark
 * pattern to find. Both buttons close the screen.
 *
 * Compact, centered, resizable — NoxButton widgets so mouse AND keyboard
 * (Tab/Enter) always work, even on tiny windows.
 */
public class PrivacyScreen extends Screen {
    private static final int PANEL_H = 142;
    private static final int PAD = 16;

    private final Screen parent;
    private int panelX, panelY, panelW;
    private NoxButton decline;

    public PrivacyScreen() { this(null); }

    public PrivacyScreen(Screen parent) {
        super(Component.literal("Privacy settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelW = Math.min(300, width - 16);
        panelX = (width - panelW) / 2;
        panelY = (height - PANEL_H) / 2;
        int gap = 8, bw = (panelW - 2 * PAD - gap) / 2;
        int y = panelY + PANEL_H - PAD - 24;
        decline = addRenderableWidget(
            new NoxButton(panelX + PAD, y, bw, 24, "No thanks", () -> choose(false)));
        addRenderableWidget(
            new NoxButton(panelX + PAD + bw + gap, y, bw, 24, "Enable",
                () -> choose(true)).primary());
    }

    /** Decline is the default focus — keyboard flow lands on opt-out first. */
    @Override
    protected void setInitialFocus() { setInitialFocus(decline); }

    private void choose(boolean enabled) {
        Telemetry.setEnabled(enabled);
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        g.fill(0, 0, width, height, 0xB0080A10);
        Gui.shadow(g, panelX, panelY, panelW, PANEL_H, 12);
        Gui.roundBorder(g, panelX, panelY, panelW, PANEL_H, 12, DS.border(), DS.winFill());
        Gui.windowFx(panelX, panelY, panelW, PANEL_H, 12);

        int x = panelX + PAD, y = panelY + 12;
        Gui.fill(g, x, y + 2, x + 18, y + 3, DS.accent());
        Gui.textBold(g, font, "P R I V A C Y", x + 26, y, DS.accent());
        Gui.text(g, font, "Share anonymous usage stats?", x, y + 22, DS.text());
        Gui.text(g, font, "Sends module toggles and session length.", x, y + 40, DS.secondary());
        Gui.text(g, font, "No chat, names or coordinates.", x, y + 52, DS.secondary());
        Gui.text(g, font, "Off unless you enable it.", x, y + 72, DS.muted());
        super.extractRenderState(g, mx, my, dt);
    }

    @Override
    public void onClose() {
        Telemetry.markAsked();
        minecraft.setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
