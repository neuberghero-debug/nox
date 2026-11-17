package dev.noxclient.gui;

import dev.noxclient.telemetry.Telemetry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * First-start telemetry consent (Modul 20.1). Shown once; the choice is
 * persisted to nox/privacy.json. Default is OFF — "Ablehnen" needs no
 * dark pattern to find. Both buttons close the screen.
 */
public class PrivacyScreen extends Screen {
    public PrivacyScreen() { super(Component.literal("NOX Privacy")); }

    @Override
    protected void init() {
        int cx = width / 2, y = height / 2 + 52;
        addRenderableWidget(new NoxButton(cx - 116, y, 110, 18, "Aktivieren", () -> {
            Telemetry.setEnabled(true); onClose();
        }).primary());
        addRenderableWidget(new NoxButton(cx + 6, y, 110, 18, "Ablehnen", () -> {
            Telemetry.setEnabled(false); onClose();
        }));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        g.fill(0, 0, width, height, 0xCC08090C);
        int cx = width / 2, top = height / 2 - 78, w = 340, h = 176;
        Gui.shadow(g, cx - w / 2, top, w, h, 12);
        Gui.roundBorder(g, cx - w / 2, top, w, h, 12, 0x267A2BE2, 0xF8141821);

        Gui.textCenter(g, font, "Anonyme Statistiken?", cx, top + 16, 0xFFE8E8F0);
        Gui.textCenter(g, font, "NOX kann anonyme Nutzungsdaten senden:", cx, top + 38, 0xFF9B9BA3);
        Gui.textCenter(g, font, "Modul-Toggles · Session-Länge · Client-Version", cx, top + 52, 0xFF9B9BA3);
        Gui.textCenter(g, font, "NIEMALS: Server-IPs · Namen · Koordinaten · Chat", cx, top + 70, 0xFF66D9A0);
        Gui.textCenter(g, font, "Standard: AUS. Änderbar jederzeit in den Settings.", cx, top + 88, 0xFF5A5A6E);
        super.extractRenderState(g, mx, my, dt);
    }

    @Override
    public void onClose() { Telemetry.markAsked(); super.onClose(); }
    @Override
    public boolean isPauseScreen() { return false; }
}
