package dev.noxclient.gui;

import dev.noxclient.NoxClient;
import dev.noxclient.gui.theme.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Desktop;
import java.net.URI;

/** One-time Discord invite popup shown at first main-menu entry. */
public class NoxInviteScreen extends Screen {
    private final Screen parent;

    public NoxInviteScreen(Screen parent) {
        super(Component.literal("NOX"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = width / 2, y = height / 2 + 10;
        addRenderableWidget(new NoxButton(cx - 105, y, 100, 20, "Join Discord", () -> {
            try { Desktop.getDesktop().browse(new URI(dev.noxclient.discord.DiscordLink.get())); } catch (Exception ignored) {}
            onClose();
        }).primary());
        addRenderableWidget(new NoxButton(cx + 5, y, 100, 20, "Maybe later", this::onClose));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        Theme t = Theme.current();
        if (parent != null) parent.extractRenderState(g, mx, my, dt);
        g.fill(0, 0, width, height, 0x99050808);
        int cx = width / 2, by = height / 2 - 70;
        Gui.shadow(g, cx - 124, by - 4, 248, 118, 14);
        Gui.roundBorder(g, cx - 124, by - 4, 248, 118, 14, t.accent() & 0x55FFFFFF, t.background());
        Gui.fill(g, cx - 100, by + 2, cx + 100, by + 3, t.accent());
        Gui.textCenter(g, font, "N O X", cx, by + 12, t.accent());
        Gui.textCenter(g, font, "Thanks for installing NOX.", cx, by + 30, t.text());
        Gui.textCenter(g, font, "Configs, updates and support live on our Discord:", cx, by + 42, t.text());
        Gui.textCenter(g, font, dev.noxclient.discord.DiscordLink.shortLabel(), cx, by + 56, t.accent());
        super.extractRenderState(g, mx, my, dt);
    }

    @Override
    public void onClose() { minecraft.setScreenAndShow(parent); }
    @Override
    public boolean isPauseScreen() { return false; }
}
