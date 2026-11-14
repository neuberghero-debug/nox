package dev.noxclient.gui;

import dev.noxclient.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** NOX-styled button — rounded pill, Inter label, accent hover. */
public class NoxButton extends AbstractWidget {
    private final Runnable action;
    private boolean primary;

    public NoxButton(int x, int y, int w, int h, String label, Runnable action) {
        super(x, y, w, h, Component.literal(label));
        this.action = action;
    }

    public NoxButton primary() { primary = true; return this; }

    @Override
    public void onClick(MouseButtonEvent e, boolean dbl) {
        action.run();
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        Theme t = Theme.current();
        boolean hov = isHoveredOrFocused();
        int r = Math.min(8, height / 2);
        int fill, border, text;
        if (primary) {
            fill = hov ? t.accent() : (t.accent() & 0x00FFFFFF) | 0xCC000000;
            border = t.accent();
            text = 0xFFFFFFFF;
        } else {
            fill = hov ? 0xFF23233A : 0xFF181826;
            border = hov ? t.accent() : 0xFF2E2E44;
            text = hov ? 0xFFFFFFFF : t.text();
        }
        Gui.round(g, getX(), getY(), width, height, r, border);
        if (r > 1) Gui.round(g, getX() + 1, getY() + 1, width - 2, height - 2, r - 1, fill);
        else g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, fill);
        Gui.textCenter(g, Minecraft.getInstance().font, getMessage().getString(),
            getX() + width / 2, getY() + (height - 8) / 2 + 1, text);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
