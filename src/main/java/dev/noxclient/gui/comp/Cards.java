package dev.noxclient.gui.comp;

import dev.noxclient.gui.Gui;
import dev.noxclient.gui.ds.DS;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Container components: Card, Badge, Modal (14.5). */
public final class Cards {

    /** Surface card with title + optional accent strip (14.5). */
    public static class Card extends Comp {
        public String title = "";
        public boolean accentStrip;

        public Card(int x, int y, int w, int h) { super(x, y, w, h); }
        public Card titled(String t) { title = t; return this; }
        public Card strip() { accentStrip = true; return this; }

        public void render(GuiGraphicsExtractor g, Font f, double mx, double my, float dt) {
            animate(mx, my, dt);
            Gui.shadow(g, x, y, w, h, DS.R_CARD);
            Gui.round(g, x, y, w, h, DS.R_CARD, DS.surface());
            Gui.roundBorder(g, x, y, w, h, DS.R_CARD, DS.border(), 0);
            if (accentStrip)
                Gui.round(g, x, y, 4, h, 2, DS.accent());
            if (!title.isEmpty())
                textB(g, f, title, x + DS.S8 + (accentStrip ? 4 : 0), y + DS.S8, DS.text());
        }
    }

    /** Small status pill — colored text on tinted surface. */
    public static class Badge extends Comp {
        public String label;
        public int color;

        public Badge(int x, int y, String label, int color) {
            super(x, y, 0, 16); this.label = label; this.color = color;
        }
        public void layout(Font f) { w = tw(f, label) + 14; }

        public void render(GuiGraphicsExtractor g, Font f, double mx, double my, float dt) {
            if (w == 0) layout(f);
            Gui.pill(g, x, y, w, h, DS.fade(color, 0.16f));
            textB(g, f, label, x + 7, y + (h - 8) / 2, color);
        }
    }

    /** Centered modal — dims the screen behind, Esc/✕ closes (14.5). */
    public static abstract class Modal extends Comp {
        public boolean open;
        private float openAnim;

        protected Modal(int w, int h) { super(0, 0, w, h); }

        public void open(int sw, int sh) {
            open = true; openAnim = 0;
            x = (sw - w) / 2; y = (sh - h) / 2;
        }
        public void close() { open = false; }

        /** render dim + window; returns false when closed. */
        public boolean render(GuiGraphicsExtractor g, Font f, int sw, int sh,
                              double mx, double my, float dt) {
            if (!open) return false;
            openAnim = DS.approach(openAnim, 1, 14f, dt);
            g.fill(0, 0, sw, sh, ((int) (0x99 * openAnim)) << 24);
            Gui.shadow(g, x, y, w, h, DS.R_WIN);
            Gui.round(g, x, y, w, h, DS.R_WIN, DS.surface());
            Gui.roundBorder(g, x, y, w, h, DS.R_WIN, DS.border(), 0);
            content(g, f, mx, my, dt);
            return true;
        }

        protected abstract void content(GuiGraphicsExtractor g, Font f,
                                        double mx, double my, float dt);

        @Override public boolean click(double mx, double my, int btn) {
            if (!open) return false;
            if (!inside(mx, my)) { close(); return true; }   // click-outside closes
            return true;
        }
        @Override public boolean key(int code) {
            if (code == 41 /* SDL ESC */) { close(); return true; }
            return true;
        }
    }
}
