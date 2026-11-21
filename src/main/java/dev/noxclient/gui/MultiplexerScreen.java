package dev.noxclient.gui;

import dev.noxclient.NoxClient;
import dev.noxclient.gui.theme.Theme;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Opsec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Multiplexer — accounts-style window grouping identity/opsec controls
 * (left) and every multiplier-ish numeric setting across all modules
 * (right, auto-collected sliders). Baseline spoof is shown locked-on:
 * brand→vanilla and channel-strip are not settings anymore.
 */
public class MultiplexerScreen extends Screen {
    private final Screen parent;
    private final List<int[]> hot = new ArrayList<>();
    private static final int TOGGLE = 1, SLIDER = 2, CLOSE = 3;
    private static final Pattern MULTI = Pattern.compile(
        "multiplier|multi|cps|speed|rate|delay|interval|strength|scale|amount|range|radius|count|limit|percent|chance|ticks|pitch|yaw|size",
        Pattern.CASE_INSENSITIVE);
    private final List<Object[]> sliders = new ArrayList<>();   // {Num, Module}
    private int scroll;
    private int dragIdx = -1;

    public MultiplexerScreen(Screen parent) {
        super(Component.literal("NOX Multiplexer"));
        this.parent = parent;
        for (Module m : NoxClient.INSTANCE.modules.all())
            for (Setting<?> s : m.settings)
                if (s instanceof Setting.Num n && MULTI.matcher(s.name).find())
                    sliders.add(new Object[]{n, m});
    }

    private int winW() { return Math.min(560, width - 40); }
    private int winH() { return Math.min(400, height - 40); }
    private int winX() { return (width - winW()) / 2; }
    private int winY() { return (height - winH()) / 2; }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        Theme t = Theme.current();
        var font = Minecraft.getInstance().font;
        Gui.backdrop(g, width, height, 1f);
        int x0 = winX(), y0 = winY(), w = winW(), h = winH();
        hot.clear();
        Gui.shadow(g, x0, y0, w, h, UICustom.radius);
        Gui.round(g, x0, y0, w, h, UICustom.radius, UICustom.winFill);
        Gui.windowFx(x0, y0, w, h, UICustom.radius);
        Gui.windowFrost(x0, y0, w, h, UICustom.radius);
        Gui.textBold(g, font, "M U L T I P L E X E R", x0 + 16, y0 + 12, t.accent());
        Gui.text(g, font, "identity · opsec · tuning", x0 + 16, y0 + 26, UICustom.textMute);
        boolean xHov = ModuleCard.inside(mx, my, x0 + w - 26, y0 + 8, 18, 16);
        Gui.textCenter(g, font, "×", x0 + w - 17, y0 + 12, xHov ? 0xFFFF8888 : UICustom.textMute);
        hot.add(new int[]{x0 + w - 26, y0 + 8, 18, 16, CLOSE, -1});

        // ---- left: opsec ----
        int lx = x0 + 14, lw = 190, ly = y0 + 44;
        Gui.round(g, lx, ly, lw, h - 60, 8, UICustom.cardFillBottom);
        Gui.text(g, font, "OPSEC", lx + 10, ly + 8, UICustom.textMute);
        int oy = ly + 22;
        oy = opRow(g, font, t, mx, my, lx + 8, oy, lw - 16, "Brand → vanilla", true, -1);
        oy = opRow(g, font, t, mx, my, lx + 8, oy, lw - 16, "Mod channels stripped", true, -1);
        oy = opRow(g, font, t, mx, my, lx + 8, oy, lw - 16, "Full payload hardening", Opsec.stripAll, 0);
        oy = opRow(g, font, t, mx, my, lx + 8, oy, lw - 16,
            "Debug console gated", !java.io.File.separator.equals("/") ||
            new java.io.File(Minecraft.getInstance().gameDirectory, "nox_dbg").exists(), 1);
        Gui.text(g, font, "locked = always-on baseline", lx + 10, oy + 4, UICustom.textMute);
        Gui.text(g, font, "accounts & proxy live in Alts", lx + 10, oy + 16, UICustom.textMute);

        // ---- right: multiplier sliders ----
        int rx = lx + lw + 12, rw = x0 + w - 14 - rx;
        Gui.round(g, rx, ly, rw, h - 60, 8, UICustom.cardFillBottom);
        Gui.text(g, font, "MULTIPLIERS  ·  " + sliders.size() + " settings", rx + 10, ly + 8, UICustom.textMute);
        int sy = ly + 22, rowH = 30, visRows = (h - 84) / rowH;
        int maxScroll = Math.max(0, sliders.size() - visRows);
        scroll = Math.min(scroll, maxScroll);
        for (int i = scroll; i < Math.min(sliders.size(), scroll + visRows); i++) {
            var n = (Setting.Num) sliders.get(i)[0];
            var m = (Module) sliders.get(i)[1];
            int si = i;
            Gui.text(g, font, n.name, rx + 10, sy, 0xFFE0E0F0);
            Gui.text(g, font, m.name, rx + rw - 10 - font.width(m.name), sy, UICustom.textMute);
            int bx = rx + 10, bw = rw - 20, by = sy + 14;
            Gui.pill(g, bx, by + 3, bw, 4, 0xFF2A2A38);
            double frac = (n.get() - n.min) / Math.max(1e-9, n.max - n.min);
            Gui.pill(g, bx, by + 3, (int) (bw * frac), 4, t.accent());
            boolean hov = ModuleCard.inside(mx, my, bx, by - 3, bw, 16);
            Gui.circle(g, bx + (int) (bw * frac), by + 5, hov || dragIdx == si ? 5 : 4,
                hov || dragIdx == si ? t.accent() : 0xFFCCCCDD);
            String val = n.get() == Math.rint(n.get()) ? String.valueOf(n.intVal())
                : String.format("%.2f", n.get());
            Gui.text(g, font, val, bx + bw - font.width(val), by - 12, UICustom.textMute);
            hot.add(new int[]{bx, by - 4, bw, 18, SLIDER, si});
            sy += rowH;
        }
        if (maxScroll > 0) {
            int th = Math.max(16, (h - 84) * visRows / sliders.size());
            int ty = ly + 22 + (int) ((h - 84 - th) * (scroll / (float) maxScroll));
            Gui.pill(g, rx + rw - 5, ty, 3, th, 0xFF4A4A5E);
        }
        super.extractRenderState(g, mx, my, dt);
    }

    private int opRow(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, Theme t,
                      int mx, int my, int x, int y, int w, String label, boolean on, int idx) {
        boolean hov = idx >= 0 && ModuleCard.inside(mx, my, x, y, w, 16);
        Gui.round(g, x, y, w, 16, 8, hov ? UICustom.cardFillTop : 0xFF14141C);
        Gui.text(g, font, label, x + 8, y + 4, on ? 0xFFE0E0F0 : UICustom.textMute);
        String tag = idx < 0 ? "locked" : on ? "on" : "off";
        int tagCol = idx < 0 ? 0xFF55DD66 : on ? 0xFF55DD66 : 0xFFFF5C7A;
        Gui.text(g, font, tag, x + w - 8 - font.width(tag), y + 4, tagCol);
        if (idx >= 0) hot.add(new int[]{x, y, w, 16, TOGGLE, idx});
        return y + 20;
    }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        int mx = (int) e.x(), my = (int) e.y();
        for (int[] r : hot) {
            if (!ModuleCard.inside(mx, my, r[0], r[1], r[2], r[3])) continue;
            switch (r[4]) {
                case CLOSE -> { onClose(); return true; }
                case TOGGLE -> {
                    if (r[5] == 0) Opsec.stripAll = !Opsec.stripAll;
                    return true;
                }
                case SLIDER -> { dragIdx = r[5]; applySlider(mx); return true; }
            }
        }
        return super.mouseClicked(e, dbl);
    }

    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragIdx >= 0) { applySlider((int) e.x()); return true; }
        return super.mouseDragged(e, dx, dy);
    }

    @Override public boolean mouseReleased(MouseButtonEvent e) {
        dragIdx = -1;
        return super.mouseReleased(e);
    }

    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.max(0, scroll - (int) sy * 3);
        return true;
    }

    private void applySlider(int mx) {
        int x0 = winX();
        int bx = x0 + 14 + 190 + 12 + 10, bw = winW() - 14 - (14 + 190 + 12) - 14 - 20;
        var n = (Setting.Num) sliders.get(dragIdx)[0];
        double frac = Math.max(0, Math.min(1, (mx - bx) / (double) bw));
        n.set(n.min + frac * (n.max - n.min));
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreenAndShow(parent); }
}
