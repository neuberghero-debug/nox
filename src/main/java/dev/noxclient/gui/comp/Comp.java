package dev.noxclient.gui.comp;

import dev.noxclient.gui.Gui;
import dev.noxclient.gui.UICustom;
import dev.noxclient.gui.ds.DS;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Component base — dt-based hover/focus animation state shared by all
 * comp/* widgets. Focus ring: 2px accent stroke + soft outer glow (14.7).
 */
public abstract class Comp {
    public int x, y, w, h;
    public boolean enabled = true, focused;
    protected float hover;        // 0..1 smoothed
    protected float focus;        // 0..1 smoothed

    protected Comp(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; }

    public boolean inside(double mx, double my) {
        return enabled && mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** per-frame animation — call once per render, before drawing. */
    protected void animate(double mx, double my, float dt) {
        float k = UICustom.reducedMotion ? 1e9f : 12f;
        hover = DS.approach(hover, inside(mx, my) ? 1 : 0, k, dt);
        focus = DS.approach(focus, focused ? 1 : 0, k, dt);
    }

    /** 2px accent ring + 8px-ish outer glow when focused/hovered (14.7). */
    protected void focusRing(GuiGraphicsExtractor g, int r) {
        float a = Math.max(focus, hover * 0.55f);
        if (a <= 0.02f) return;
        Gui.round(g, x - 2, y - 2, w + 4, h + 4, r + 2, DS.fade(DS.focusGlow(), a));
        if (focus > 0.05f)
            Gui.roundBorder(g, x - 1, y - 1, w + 2, h + 2, r + 1,
                DS.fade(DS.focusRing(), focus), 0);
    }

    public boolean click(double mx, double my, int btn) { return inside(mx, my); }
    public boolean key(int code) { return false; }
    public boolean type(char c) { return false; }

    /** shared text helpers — Inter via Gui.* */
    protected void text(GuiGraphicsExtractor g, Font f, String s, int x, int y, int c) {
        Gui.text(g, f, s, x, y, c);
    }
    protected void textB(GuiGraphicsExtractor g, Font f, String s, int x, int y, int c) {
        Gui.textBold(g, f, s, x, y, c);
    }
    protected int tw(Font f, String s) { return Gui.width(f, s); }
}
