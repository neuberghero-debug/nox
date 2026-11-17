package dev.noxclient.gui.comp;

import dev.noxclient.gui.Gui;
import dev.noxclient.gui.ds.DS;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Input-family components: Toggle (iOS pill), Slider, TextInput (14.5). */
public final class Inputs {

    /** iOS-pill toggle 36x20, 150ms bounce. */
    public static final class Toggle extends Comp {
        public boolean on;
        public Runnable onChange = () -> {};
        private float anim;   // knob travel 0..1
        private float press;  // overshoot bounce

        public Toggle(int x, int y, boolean on) {
            super(x, y, 36, 20); this.on = on; this.anim = on ? 1 : 0;
        }

        public void render(GuiGraphicsExtractor g, Font f, double mx, double my, float dt) {
            animate(mx, my, dt);
            anim = DS.approach(anim, on ? 1 : 0, 14f, dt);
            press = DS.approach(press, 0, 10f, dt);
            int track = DS.lerp(DS.surfaceAlt(), DS.accent(), anim);
            Gui.pill(g, x, y, w, h, track);
            if (on || anim > 0.02f)
                Gui.pill(g, x, y, w, h, DS.fade(DS.accent(), anim));
            // knob: slight overshoot on toggle
            float t = anim + press * 0.08f;
            int knob = (int) (x + 3 + t * (w - 6 - 14));
            Gui.circle(g, knob + 7, y + 10, 7, DS.fade(0xFF000000, 0.25f * anim));
            Gui.circle(g, knob + 7, y + 10, 7, enabled ? 0xFFFFFFFF : 0xFF9B9BA3);
            focusRing(g, h / 2);
        }

        @Override public boolean click(double mx, double my, int btn) {
            if (!inside(mx, my)) return false;
            on = !on; press = on ? 1 : -1; onChange.run(); return true;
        }
    }

    /** 4px track, accent fill, 12px thumb, value badge (14.5). */
    public static final class Slider extends Comp {
        public double value, min, max, step = 1;
        public String label = "";
        public Runnable onChange = () -> {};
        private boolean dragging;

        public Slider(int x, int y, int w, String label, double min, double max, double value) {
            super(x, y, w, 26); this.label = label; this.min = min; this.max = max; this.value = value;
        }

        private double snap(double v) {
            v = Math.round(v / step) * step;
            return Math.max(min, Math.min(max, v));
        }
        private double fromX(double mx) { return snap(min + (mx - x - 6) / (w - 12) * (max - min)); }
        private float frac() { return (float) ((value - min) / Math.max(1e-9, max - min)); }

        public void render(GuiGraphicsExtractor g, Font f, double mx, double my, float dt) {
            animate(mx, my, dt);
            if (dragging) { double nv = fromX(mx); if (nv != value) { value = nv; onChange.run(); } }
            int ty = y + h - 10;
            Gui.round(g, x, ty, w, 4, 2, DS.surfaceAlt());
            int fill = (int) ((w - 12) * frac());
            if (fill > 0) Gui.round(g, x + 6, ty, fill, 4, 2, DS.accent());
            int thx = x + 6 + fill;
            boolean hot = inside(mx, my) || dragging;
            Gui.circle(g, thx, ty + 2, hot ? 7 : 6, DS.fade(0xFF000000, 0.3f));
            Gui.circle(g, thx, ty + 2, hot ? 6 : 5, hot ? DS.hover() : 0xFFFFFFFF);
            if (!label.isEmpty())
                text(g, f, label, x, y + 1, DS.secondary());
            // value badge right-aligned
            String vs = step == (long) step ? String.valueOf((long) value) : String.format("%.2f", value);
            textB(g, f, vs, x + w - tw(f, vs), y + 1, DS.text());
            focusRing(g, 4);
        }

        @Override public boolean click(double mx, double my, int btn) {
            if (!inside(mx, my)) return false;
            dragging = true; value = fromX(mx); onChange.run(); return true;
        }
        public void release() { dragging = false; }
        public boolean dragging() { return dragging; }
    }

    /** Text input with focus ring + caret + placeholder (14.5). */
    public static final class Input extends Comp {
        public String value = "";
        public String placeholder = "";
        public int maxLen = 64;
        private int caret;
        private long caretBlink;

        public Input(int x, int y, int w, String placeholder) {
            super(x, y, w, 28); this.placeholder = placeholder;
        }

        public void render(GuiGraphicsExtractor g, Font f, double mx, double my, float dt) {
            animate(mx, my, dt);
            Gui.round(g, x, y, w, h, DS.R_INPUT, focused ? DS.surfaceAlt() : DS.surface());
            Gui.roundBorder(g, x, y, w, h, DS.R_INPUT,
                focused ? DS.accent() : (hover > 0.05f ? DS.lighten(DS.border(), 0.3f) : DS.border()), 0);
            String shown = value.isEmpty() && !focused ? placeholder : value;
            int col = value.isEmpty() && !focused ? DS.muted() : DS.text();
            text(g, f, shown, x + 9, y + (h - 8) / 2, col);
            if (focused && (System.currentTimeMillis() - caretBlink) % 1000 < 550) {
                String pre = value.substring(0, Math.min(caret, value.length()));
                int cx = x + 9 + tw(f, pre);
                g.fill(cx, y + 6, cx + 1, y + h - 6, DS.accent());
            }
            focusRing(g, DS.R_INPUT);
        }

        @Override public boolean click(double mx, double my, int btn) {
            focused = inside(mx, my);
            if (focused) { caret = value.length(); caretBlink = System.currentTimeMillis(); }
            return focused;
        }
        public void unfocus() { focused = false; }

        @Override public boolean key(int code) {
            if (!focused) return false;
            caretBlink = System.currentTimeMillis();
            if (code == 42 && caret > 0) {                 // BACKSPACE (SDL 42)
                value = value.substring(0, caret - 1) + value.substring(caret); caret--;
            } else if (code == 79) caret = Math.min(value.length(), caret + 1);  // RIGHT
            else if (code == 80) caret = Math.max(0, caret - 1);                 // LEFT
            else if (code == 40) { focused = false; return false; }              // RETURN
            else return false;
            return true;
        }
        @Override public boolean type(char c) {
            if (!focused || value.length() >= maxLen || c < 32 || c == 127) return false;
            value = value.substring(0, caret) + c + value.substring(caret); caret++;
            caretBlink = System.currentTimeMillis();
            return true;
        }
    }
}
