package dev.noxclient.gui;

import dev.noxclient.gui.ds.DS;
import dev.noxclient.telemetry.Telemetry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * First-start telemetry consent (Modul 20.1). Shown once; the choice is
 * persisted to nox/privacy.json. Default is OFF — declining needs no dark
 * pattern to find. Both buttons close the screen.
 *
 * Compact, centered, resizable — vanilla Button widgets so mouse AND
 * keyboard (Tab/Enter) always work, even on tiny windows.
 */
public class PrivacyScreen extends Screen {
    private static final int PANEL_H = 136;
    private static final int PAD = 14;

    private final Screen parent;
    private int panelX, panelY, panelW;
    private Button decline;

    public PrivacyScreen() { this(null); }

    public PrivacyScreen(Screen parent) {
        super(Component.literal("Privacy settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelW = Math.min(280, width - 16);
        panelX = (width - panelW) / 2;
        panelY = (height - PANEL_H) / 2;
        int gap = 8, bw = (panelW - 2 * PAD - gap) / 2;
        int y = panelY + PANEL_H - PAD - 22;
        decline = addRenderableWidget(Button.builder(Component.literal("No thanks"),
                b -> choose(false))
            .bounds(panelX + PAD, y, bw, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Enable"),
                b -> choose(true))
            .bounds(panelX + PAD + bw + gap, y, bw, 22).build());
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
        SmoothShapes.roundBorder(g, panelX, panelY, panelW, PANEL_H, 10,
            DS.border(), DS.winFill());
        int x = panelX + PAD, y = panelY + PAD;
        g.text(font, "NOX / PRIVACY", x, y, DS.accent());
        g.text(font, "Share anonymous usage stats?", x, y + 18, DS.text());
        g.textWithWordWrap(font,
            Component.literal("Sends module toggles and session length. "
                + "No chat, names or coordinates."),
            x, y + 34, panelW - 2 * PAD, DS.secondary());
        g.text(font, "Off unless you enable it.", x, y + 70, DS.text());
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
