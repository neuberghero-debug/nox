package dev.noxclient.gui.comp;

import dev.noxclient.gui.Gui;
import dev.noxclient.gui.ds.DS;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Button-family components: Button, IconButton, TabPill, Select (14.5). */
public final class Buttons {

    /** Primary/ghost/danger button, 8px radius. */
    public static final class Button extends Comp {
        public enum Style { PRIMARY, GHOST, DANGER }
        public String label;
        public Style style = Style.PRIMARY;
        public Runnable onClick = () -> {};

        public Button(int x, int y, int w, int h, String label) {
            super(x, y, w, h); this.label = label;
        }
        public Button style(Style s) { style = s; return this; }
        public Button on(Runnable r) { onClick = r; return this; }

        public void render(GuiGraphicsExtractor g, Font f, double mx, double my, float dt) {
            animate(mx, my, dt);
            int fill = switch (style) {
                case PRIMARY -> DS.lerp(DS.accent(), DS.hover(), hover);
                case GHOST -> DS.lerp(DS.surfaceAlt(), DS.lighten(DS.surfaceAlt(), 0.08f), hover);
                case DANGER -> DS.lerp(0xFF3A2126, 0xFF4A2930, hover);
            };
            Gui.round(g, x, y, w, h, DS.R_INPUT, fill);
            if (style == Style.GHOST)
                Gui.roundBorder(g, x, y, w, h, DS.R_INPUT,
                    DS.lerp(DS.border(), DS.accent(), hover), 0);
            int tc = switch (style) {
                case PRIMARY -> DS.onAccent();
                case GHOST -> DS.text();
                case DANGER -> DS.danger();
            };
            textB(g, f, label, x + (w - tw(f, label)) / 2, y + (h - 8) / 2, DS.fade(tc, enabled ? 1 : 0.4f));
            focusRing(g, DS.R_INPUT);
        }

        @Override public boolean click(double mx, double my, int btn) {
            if (!inside(mx, my)) return false;
            onClick.run(); return true;
        }
    }

    /** 20x20 icon-only button — tooltip expected from caller. */
    public static final class IconButton extends Comp {
        public Icons.Icon icon;
        public Runnable onClick = () -> {};
        public int tint = -1;   // -1 = secondary, set for accent

        public IconButton(int x, int y, Icons.Icon icon) {
            super(x, y, 20, 20); this.icon = icon;
        }
        public IconButton on(Runnable r) { onClick = r; return this; }

        public void render(GuiGraphicsExtractor g, Font f, double mx, double my, float dt) {
            animate(mx, my, dt);
            if (hover > 0.02f)
                Gui.round(g, x, y, w, h, 6, DS.fade(DS.surfaceAlt(), 0.5f + 0.5f * hover));
            int c = tint >= 0 ? DS.lerp(tint, DS.hover(), hover)
                              : DS.lerp(DS.secondary(), DS.text(), hover);
            Icons.draw(g, icon, x + (w - 12) / 2f, y + (h - 12) / 2f, 12, c);
            focusRing(g, 6);
        }

        @Override public boolean click(double mx, double my, int btn) {
            if (!inside(mx, my)) return false;
            onClick.run(); return true;
        }
    }

    /** Pill tab — icon + label, active = accent fill + white text (15.3). */
    public static final class TabPill extends Comp {
        public String label;
        public Icons.Icon icon;
        public boolean active;
        public Runnable onClick = () -> {};

        public TabPill(int x, int y, String label, Icons.Icon icon) {
            super(x, y, 10, 24); this.label = label; this.icon = icon;
        }

        public int naturalWidth(Font f) { return 14 + tw(f, label) + 20; }

        public void render(GuiGraphicsExtractor g, Font f, double mx, double my, float dt) {
            animate(mx, my, dt);
            int fill = active ? DS.accent() : DS.lerp(DS.surface(), DS.surfaceAlt(), hover);
            Gui.pill(g, x, y, w, h, fill);
            int tc = active ? DS.onAccent() : DS.lerp(DS.secondary(), DS.text(), hover);
            int ix = x + 8;
            if (icon != null) { Icons.draw(g, icon, ix, y + (h - 11) / 2f, 11, tc); ix += 15; }
            text(g, f, label, ix, y + (h - 8) / 2, tc);
            focusRing(g, h / 2);
        }

        @Override public boolean click(double mx, double my, int btn) {
            if (!inside(mx, my)) return false;
            onClick.run(); return true;
        }
    }

    /** Dropdown select — closed = input-style row; open = card list (14.5). */
    public static final class Select extends Comp {
        public String[] options;
        public int selected;
        public boolean open;
        public Runnable onChange = () -> {};
        private static final int ITEM_H = 22;

        public Select(int x, int y, int w, String[] options, int selected) {
            super(x, y, w, 26); this.options = options; this.selected = selected;
        }

        public int openHeight() { return h + options.length * ITEM_H + 6; }

        public void render(GuiGraphicsExtractor g, Font f, double mx, double my, float dt) {
            animate(mx, my, dt);
            Gui.round(g, x, y, w, h, DS.R_INPUT, DS.surface());
            Gui.roundBorder(g, x, y, w, h, DS.R_INPUT,
                open ? DS.accent() : DS.border(), 0);
            text(g, f, options[selected], x + 9, y + (h - 8) / 2, DS.text());
            Icons.draw(g, Icons.Icon.CHEVRON, x + w - 17, y + (h - 10) / 2f, 10,
                open ? DS.accent() : DS.secondary());
            if (open) {
                int ly = y + h + 4;
                Gui.shadow(g, x, ly, w, options.length * ITEM_H + 4, DS.R_CARD);
                Gui.round(g, x, ly, w, options.length * ITEM_H + 4, DS.R_CARD, DS.surface());
                Gui.roundBorder(g, x, ly, w, options.length * ITEM_H + 4, DS.R_CARD, DS.border(), 0);
                for (int i = 0; i < options.length; i++) {
                    int iy = ly + 2 + i * ITEM_H;
                    boolean hov = mx >= x && mx < x + w && my >= iy && my < iy + ITEM_H;
                    if (hov) Gui.round(g, x + 2, iy, w - 4, ITEM_H, 6, DS.surfaceAlt());
                    if (i == selected)
                        Gui.circle(g, x + 10, iy + ITEM_H / 2, 2, DS.accent());
                    text(g, f, options[i], x + 18, iy + (ITEM_H - 8) / 2,
                        i == selected ? DS.text() : DS.secondary());
                }
            }
            focusRing(g, DS.R_INPUT);
        }

        @Override public boolean click(double mx, double my, int btn) {
            if (inside(mx, my)) { open = !open; return true; }
            if (open && mx >= x && mx < x + w) {
                int i = (int) ((my - (y + h + 4)) / ITEM_H);
                if (i >= 0 && i < options.length) {
                    if (i != selected) { selected = i; onChange.run(); }
                    open = false; return true;
                }
            }
            open = false;
            return false;
        }
    }
}
