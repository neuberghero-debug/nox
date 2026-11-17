package dev.noxclient.gui.comp;

import dev.noxclient.gui.Gui;
import dev.noxclient.gui.ds.DS;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Floating helpers: Tooltip (400ms delay) + Scrollable (4px auto-hide) (14.5). */
public final class Overlays {

    /** Card-style tooltip with 400ms hover delay — caller stores one per widget. */
    public static final class Tooltip {
        public String title, body;
        private long hoverSince = -1;

        public Tooltip(String title, String body) { this.title = title; this.body = body; }

        public void render(GuiGraphicsExtractor g, Font f, double mx, double my,
                           boolean hovered, int sw, int sh) {
            if (!hovered) { hoverSince = -1; return; }
            if (hoverSince < 0) { hoverSince = System.currentTimeMillis(); return; }
            if (System.currentTimeMillis() - hoverSince < 400) return;
            int w = Math.max(Gui.width(f, title), Gui.width(f, body)) + 18;
            int h = body.isEmpty() ? 26 : 36;
            int x = (int) mx + 12, y = (int) my + 10;
            if (x + w > sw - 4) x = (int) mx - w - 8;
            if (y + h > sh - 4) y = sh - h - 4;
            Gui.shadow(g, x, y, w, h, 7);
            Gui.round(g, x, y, w, h, 7, DS.surfaceAlt());
            Gui.roundBorder(g, x, y, w, h, 7, DS.border(), 0);
            Gui.textBold(g, f, title, x + 9, y + 6, DS.accent());
            if (!body.isEmpty())
                Gui.text(g, f, body, x + 9, y + 17, DS.secondary());
        }
    }

    /** Scrollable region: content offset, 4px rounded bar, auto-hides 2s idle. */
    public static final class Scrollable {
        public float scroll;          // current offset px
        public float target;
        public int viewH, contentH;
        private long lastScroll;

        public Scrollable(int viewH) { this.viewH = viewH; }

        public int maxScroll() { return Math.max(0, contentH - viewH); }

        /** feed a scroll-wheel delta (vanilla units ~±1). */
        public void onScroll(double dy) {
            target -= (float) dy * 18;
            target = Math.max(0, Math.min(maxScroll(), target));
            lastScroll = System.currentTimeMillis();
        }

        /** dt smoothing — call per frame. Returns the y offset to subtract. */
        public int tick(float dt) {
            target = Math.max(0, Math.min(maxScroll(), target));
            scroll = DS.approach(scroll, target, 14f, dt);
            return Math.round(scroll);
        }

        /** draw the 4px auto-hiding bar at the right edge. */
        public void renderBar(GuiGraphicsExtractor g, int x, int y) {
            if (maxScroll() <= 0) return;
            long idle = System.currentTimeMillis() - lastScroll;
            float vis = idle < 2000 ? 1f : Math.max(0f, 1f - (idle - 2000) / 500f);
            if (vis <= 0.02f) return;
            int th = Math.max(18, viewH * viewH / contentH);
            int ty = y + (int) ((viewH - th) * (scroll / Math.max(1, maxScroll())));
            Gui.round(g, x, ty, 4, th, 2, DS.fade(DS.secondary(), 0.55f * vis));
        }

        /** NVG variant — same auto-hide, vector-rounded thumb. */
        public void renderBarNvg(long c, org.lwjgl.system.MemoryStack st, int x, int y) {
            if (maxScroll() <= 0) return;
            long idle = System.currentTimeMillis() - lastScroll;
            float vis = idle < 2000 ? 1f : Math.max(0f, 1f - (idle - 2000) / 500f);
            if (vis <= 0.02f) return;
            int th = Math.max(18, viewH * viewH / contentH);
            int ty = y + (int) ((viewH - th) * (scroll / Math.max(1, maxScroll())));
            dev.noxclient.gui.nvg.Nvg.round(c, st, x, ty, 4, th, 2,
                dev.noxclient.gui.nvg.Nvg.fade(DS.secondary(), 0.55f * vis));
        }

        public boolean active() { return maxScroll() > 0; }
    }
}
