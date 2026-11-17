package dev.noxclient.gui;

import dev.noxclient.gui.theme.Theme;
import dev.noxclient.module.render.Interface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Visual GUI customization — theme cards with live swatch preview, backdrop
 * picker, layout styles, accent palette and motion toggles. Every control
 * writes into the Interface module's settings so changes persist through the
 * normal config path and sync to UICustom on the next tick.
 */
public class CustomizeScreen extends Screen {
    private static final int[] ACCENTS = {
        0xFF7A2BE2, 0xFF1E90FF, 0xFF39FFC4, 0xFF5AC8E8,
        0xFFFFB347, 0xFFFF5E8A, 0xFF7CE38B, 0xFFD9742B };
    private final Screen parent;
    private final List<int[]> hot = new ArrayList<>();   // {x,y,w,h,kind,arg}
    private static final int THEME = 0, SEASON = 1, BACKDROP = 2, LEVEL = 3,
        CLICK = 4, CARD = 5, WIN = 6, ACCENT = 7, DENSITY = 8, RADIUS = 9,
        HUDSCALE = 10, MOTION = 11, HUE = 12, SV = 13, HEX = 14;
    private int scroll;
    private float hue = -1, sat, val;            // -1 → derive from current accent
    private int[] dragRect;                      // hot[] entry being dragged
    private String hexBuf;
    private boolean hexFocused;

    public CustomizeScreen(Screen parent) {
        super(Component.literal("NOX Style")); this.parent = parent;
    }

    private static Interface iface() {
        return dev.noxclient.NoxClient.INSTANCE.modules.get("Interface") instanceof Interface i ? i : null;
    }

    private int winW() { return Math.min(560, width - 40); }
    private int winH() { return Math.min(430, height - 40); }
    private int winX() { return (width - winW()) / 2; }
    private int winY() { return (height - winH()) / 2; }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        Theme t = Theme.current();
        var font = Minecraft.getInstance().font;
        Gui.backdrop(g, width, height, 1f);
        int x0 = winX(), y0 = winY(), w = winW(), h = winH();
        Gui.shadow(g, x0, y0, w, h, UICustom.radius);
        Gui.round(g, x0, y0, w, h, UICustom.radius, UICustom.winFill);
        Gui.windowFx(x0, y0, w, h, UICustom.radius);
        Gui.windowFrost(x0, y0, w, h, UICustom.radius);
        Gui.textBold(g, font, "S T Y L E", x0 + 16, y0 + 12, t.accent());
        Gui.text(g, font, "Live GUI customization", x0 + 16 + Gui.width(font, "S T Y L E") + 10,
            y0 + 13, UICustom.textMute);
        boolean xHov = ModuleCard.inside(mx, my, x0 + w - 26, y0 + 8, 18, 16);
        Gui.textCenter(g, font, "✕", x0 + w - 17, y0 + 12, xHov ? 0xFFFF8888 : UICustom.textDim);
        hot.clear(); hot.add(new int[]{x0 + w - 26, y0 + 8, 18, 16, -1, 0}); // close

        Interface i = iface();
        int y = y0 + 34 - scroll;
        Gui.clipOn(x0 + 4, y0 + 30, x0 + w - 4, y0 + h - 30);

        // — THEME —
        y = section(g, font, "Theme", x0, y, w);
        int cw = (w - 32 - 18) / 4;
        for (int k = 0; k < Theme.ALL.length; k++) {
            Theme th = Theme.ALL[k];
            int cx = x0 + 16 + (k % 4) * (cw + 6), cy = y + (k / 4) * 52;
            boolean sel = Theme.index() == k;
            boolean hov = ModuleCard.inside(mx, my, cx, cy, cw, 46);
            Gui.roundBorder(g, cx, cy, cw, 46, 8,
                sel ? t.accent() : hov ? UICustom.borderHover : UICustom.border,
                UICustom.cardFillBottom);
            Gui.round(g, cx + 6, cy + 6, cw - 12, 18, 5, th.header());
            Gui.circle(g, cx + 16, cy + 15, 5, th.accent());
            Gui.text(g, font, th.name(), cx + 6, cy + 30, sel ? t.text() : UICustom.textDim);
            if (sel) Gui.textCenter(g, font, "✓", cx + cw - 10, cy + 15, t.accent());
            hot.add(new int[]{cx, cy, cw, 46, THEME, k});
        }
        y += ((Theme.ALL.length + 3) / 4) * 52 + 6;

        // — SEASON —
        y = section(g, font, "Season", x0, y, w);
        y = pills(g, font, mx, my, x0 + 16, y,
            new String[]{"Auto", "Winter", "Spring", "Summer", "Autumn"},
            i == null ? "Auto" : i.season.get(), SEASON, t) + 6;

        // — BACKDROP —
        y = section(g, font, "Screen backdrop", x0, y, w);
        String[] bdNames = {"Dim", "Frost", "Prism"};
        int bw = (w - 32 - 12) / 3;
        for (int k = 0; k < 3; k++) {
            int bx = x0 + 16 + k * (bw + 6);
            boolean sel = i != null && i.backdrop.get().equals(bdNames[k]);
            boolean hov = ModuleCard.inside(mx, my, bx, y, bw, 40);
            Gui.roundBorder(g, bx, y, bw, 40, 8,
                sel ? t.accent() : hov ? UICustom.borderHover : UICustom.border,
                UICustom.cardFillBottom);
            // mini preview: dim = flat bar, frost = layered sheen, prism = edge glow
            if (k == 0) Gui.round(g, bx + 8, y + 8, bw - 16, 14, 4, 0x66000000);
            if (k == 1) {
                Gui.round(g, bx + 8, y + 8, bw - 16, 14, 4, 0x4488BBEE);
                Gui.round(g, bx + 8, y + 8, bw - 16, 6, 3, 0x33FFFFFF);
            }
            if (k == 2) {
                Gui.round(g, bx + 8, y + 8, bw - 16, 14, 4, 0x33FFFFFF);
                Gui.roundBorder(g, bx + 8, y + 8, bw - 16, 14, 4, t.accent(), 0);
            }
            Gui.textCenter(g, font, bdNames[k], bx + bw / 2, y + 28,
                sel ? t.text() : UICustom.textDim);
            hot.add(new int[]{bx, y, bw, 40, BACKDROP, k});
        }
        y += 46;
        y = pills(g, font, mx, my, x0 + 16, y,
            new String[]{"Light", "Medium", "Strong"},
            i == null ? "Medium" : i.backdropLevel.get(), LEVEL, t) + 6;

        // — LAYOUT —
        y = section(g, font, "ClickGUI layout", x0, y, w);
        y = pills(g, font, mx, my, x0 + 16, y,
            new String[]{"Studio", "Cards", "Compact rows", "Sidebar", "Panels", "Hub"},
            i == null ? "Studio" : i.clickStyle.get(), CLICK, t) + 4;
        y = pills(g, font, mx, my, x0 + 16, y,
            new String[]{"Flat", "Glass", "Neon"},
            i == null ? "Glass" : i.cardStyle.get(), CARD, t) + 4;
        y = pills(g, font, mx, my, x0 + 16, y,
            new String[]{"Solid", "Sky", "Glass"},
            i == null ? "Solid" : i.winStyle.get(), WIN, t) + 6;

        // — ACCENT —
        y = section(g, font, "Accent color", x0, y, w);
        int ax = x0 + 16;
        int curAcc = i == null ? UICustom.accent : i.accent.get() | 0xFF000000;
        for (int c : ACCENTS) {
            boolean sel = c == curAcc;
            boolean hov = ModuleCard.inside(mx, my, ax - 2, y - 2, 22, 22);
            Gui.circle(g, ax + 9, y + 9, hov ? 9 : 8, c);
            if (sel) Gui.circle(g, ax + 9, y + 9, 10, 0);
            if (sel) Gui.roundBorder(g, ax - 2, y - 2, 22, 22, 11, t.text(), 0);
            hot.add(new int[]{ax - 2, y - 2, 22, 22, ACCENT, c});
            ax += 28;
        }
        y += 26;

        // full HSV picker — hue strip + SV square + hex field
        if (hue < 0) {
            float[] hsv = java.awt.Color.RGBtoHSB((curAcc >> 16) & 0xFF,
                (curAcc >> 8) & 0xFF, curAcc & 0xFF, null);
            hue = hsv[0]; sat = hsv[1]; val = hsv[2];
        }
        int pw = w - 32 - 96;                    // picker width, hex column right
        for (int s = 0; s < 6; s++) {
            int c1 = java.awt.Color.HSBtoRGB(s / 6f, 1f, 1f);
            int c2 = java.awt.Color.HSBtoRGB((s + 1) / 6f, 1f, 1f);
            Gui.hgrad(g, x0 + 16 + s * pw / 6, y, pw / 6 + 1, 10, 0xFF000000 | c1, 0xFF000000 | c2);
        }
        int hx = x0 + 16 + Math.round(hue * pw);
        Gui.pill(g, hx - 1, y - 2, 3, 14, 0xFFFFFFFF);
        hot.add(new int[]{x0 + 16, y - 2, pw, 14, HUE, 0});

        y += 16; int svh = 52;
        int hueC = java.awt.Color.HSBtoRGB(hue, 1f, 1f);
        Gui.fill(g, x0 + 16, y, x0 + 16 + pw, y + svh, 0xFFFFFFFF);
        Gui.hgrad(g, x0 + 16, y, pw, svh, 0xFFFFFFFF, 0xFF000000 | hueC);
        Gui.vgrad(g, x0 + 16, y, pw, svh, 0, 0, 0xFF000000);
        Gui.roundBorder(g, x0 + 16, y, pw, svh, 4, UICustom.border, 0);
        int sx = x0 + 16 + Math.round(sat * pw), sy = y + Math.round((1f - val) * svh);
        Gui.circle(g, sx, sy, 4, 0xFF000000);
        Gui.circle(g, sx, sy, 3, 0xFFFFFFFF);
        hot.add(new int[]{x0 + 16, y, pw, svh, SV, 0});

        // hex field + live preview, right of the SV box
        int fx = x0 + 16 + pw + 10, fw = w - 32 - pw - 10;
        Gui.circle(g, fx + fw / 2, y + 12, 9, 0xFF000000 | curAcc);
        Gui.roundBorder(g, fx, y + 26, fw, 16, 6,
            hexFocused ? t.accent() : UICustom.border, UICustom.cardFillBottom);
        String hex = hexFocused ? hexBuf : String.format("%06X", curAcc & 0xFFFFFF);
        Gui.textCenter(g, font, "#" + hex + (hexFocused ? "▏" : ""), fx + fw / 2, y + 30,
            hexFocused ? t.accent() : t.text());
        hot.add(new int[]{fx, y + 26, fw, 16, HEX, 0});
        y += svh + 8;

        // — DENSITY / RADIUS / HUD SCALE —
        y = section(g, font, "Density & scale", x0, y, w);
        y = pills(g, font, mx, my, x0 + 16, y,
            new String[]{"Compact", "Normal", "Spacious"},
            i == null ? "Normal" : i.density.get(), DENSITY, t) + 4;
        y = stepper(g, font, mx, my, x0 + 16, y, "Corner radius",
            i == null ? 12 : i.radius.intVal(), "", RADIUS, t) + 4;
        y = stepper(g, font, mx, my, x0 + 16, y, "HUD scale",
            i == null ? 1.0 : (double) i.hudScale.get(), "x", HUDSCALE, t) + 6;

        // — MOTION —
        y = section(g, font, "Motion", x0, y, w);
        boolean rm = i != null && i.reducedMotion.on();
        boolean mHov = ModuleCard.inside(mx, my, x0 + 16, y, 200, 16);
        Gui.round(g, x0 + 16, y + 2, 30, 14, 7, rm ? t.accent() : 0xFF2A2A3A);
        Gui.circle(g, x0 + 16 + (rm ? 22 : 8), y + 9, 5, 0xFFFFFFFF);
        Gui.text(g, font, "Reduced motion", x0 + 54, y + 4, t.text());
        Gui.text(g, font, "Skips animated transitions", x0 + 54 + Gui.width(font, "Reduced motion") + 10,
            y + 5, UICustom.textMute);
        hot.add(new int[]{x0 + 16, y, 200, 16, MOTION, mHov ? 1 : 0});
        y += 24;

        Gui.clipOff();
        int total = y - (y0 + 34 - scroll);
        if (total > winH() - 64) {
            int sh = winH() - 64, barH = Math.max(24, sh * sh / total);
            int barY = y0 + 32 + (sh - barH) * scroll / Math.max(1, total - sh);
            Gui.pill(g, x0 + w - 7, y0 + 32, 3, sh, 0xFF2A2A38);
            Gui.pill(g, x0 + w - 7, barY, 3, barH, t.accent());
        }
        Gui.fill(g, x0 + UICustom.radius, y0 + h - 26, x0 + w - UICustom.radius, y0 + h - 25, UICustom.border);
        Gui.textCenter(g, font, "Changes apply live and persist via the Interface module",
            width / 2, y0 + h - 18, UICustom.textMute);
        super.extractRenderState(g, mx, my, dt);
    }

    private int section(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font,
                        String name, int x0, int y, int w) {
        Gui.textBold(g, font, name.toUpperCase(), x0 + 16, y, UICustom.textMute);
        Gui.fill(g, x0 + 16 + Gui.width(font, name.toUpperCase()) + 8, y + 4, x0 + w - 16, y + 5, UICustom.border);
        return y + 14;
    }

    /** Pill row, wraps on overflow. Returns y after the row(s). */
    private int pills(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int mx, int my,
                      int x, int y, String[] opts, String sel, int kind, Theme t) {
        for (int k = 0; k < opts.length; k++) {
            int pw = Gui.width(font, opts[k]) + 16;
            if (x + pw > winX() + winW() - 16) { x = winX() + 16; y += 20; }
            boolean on = opts[k].equals(sel);
            boolean hov = ModuleCard.inside(mx, my, x, y, pw, 16);
            Gui.pill(g, x, y, pw, 16, on ? t.accent() : hov ? UICustom.cardFillTop : UICustom.cardFillBottom);
            if (!on && hov) Gui.pill(g, x, y, pw, 16, 0x14FFFFFF);
            Gui.text(g, font, opts[k], x + 8, y + 4, on ? 0xFFFFFFFF : UICustom.textDim);
            hot.add(new int[]{x, y, pw, 16, kind, k});
            x += pw + 6;
        }
        return y + 22;
    }

    /** Label + −/value/+ stepper. kind decides the step. */
    private int stepper(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int mx, int my,
                        int x, int y, String label, double val, String unit, int kind, Theme t) {
        Gui.text(g, font, label, x, y + 4, t.text());
        String vs = kind == RADIUS ? String.valueOf((int) val)
                  : String.format("%.1f%s", val, unit);
        int bx = winX() + winW() - 16 - 92;
        for (int s = 0; s < 2; s++) {
            String sym = s == 0 ? "−" : "+";
            boolean hov = ModuleCard.inside(mx, my, bx + (s == 0 ? 0 : 66), y, 26, 16);
            Gui.roundBorder(g, bx + (s == 0 ? 0 : 66), y, 26, 16, 6,
                hov ? UICustom.borderHover : UICustom.border, UICustom.cardFillBottom);
            Gui.textCenter(g, font, sym, bx + (s == 0 ? 13 : 79), y + 4,
                hov ? t.accent() : UICustom.textDim);
            hot.add(new int[]{bx + (s == 0 ? 0 : 66), y, 26, 16, kind, s == 0 ? -1 : 1});
        }
        Gui.textCenter(g, font, vs, bx + 46, y + 4, t.text());
        return y + 22;
    }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        for (int[] r : hot) {
            if (!ModuleCard.inside(e.x(), e.y(), r[0], r[1], r[2], r[3])) continue;
            Interface i = iface();
            switch (r[4]) {
                case -1 -> { onClose(); return true; }
                case THEME -> { Theme.set(r[5]);
                    if (i != null) i.theme.set(Theme.ALL[r[5]].name()); }
                case SEASON -> { if (i != null)
                    i.season.set(new String[]{"Auto", "Winter", "Spring", "Summer", "Autumn"}[r[5]]); }
                case BACKDROP -> { if (i != null)
                    i.backdrop.set(new String[]{"Dim", "Frost", "Prism"}[r[5]]); }
                case LEVEL -> { if (i != null)
                    i.backdropLevel.set(new String[]{"Light", "Medium", "Strong"}[r[5]]); }
                case CLICK -> { if (i != null)
                    i.clickStyle.set(new String[]{"Studio", "Cards", "Compact rows", "Sidebar", "Panels", "Hub"}[r[5]]); }
                case CARD -> { if (i != null)
                    i.cardStyle.set(new String[]{"Flat", "Glass", "Neon"}[r[5]]); }
                case WIN -> { if (i != null)
                    i.winStyle.set(new String[]{"Solid", "Sky", "Glass"}[r[5]]); }
                case ACCENT -> { if (i != null) { i.accent.set(r[5]); i.cardAcc.set(r[5]);
                    float[] hsv = java.awt.Color.RGBtoHSB((r[5] >> 16) & 0xFF,
                        (r[5] >> 8) & 0xFF, r[5] & 0xFF, null);
                    hue = hsv[0]; sat = hsv[1]; val = hsv[2]; } }
                case HUE -> { dragRect = r;
                    hue = Math.clamp((float) (e.x() - r[0]) / r[2], 0f, 1f); applyAccent(); }
                case SV -> { dragRect = r;
                    sat = Math.clamp((float) (e.x() - r[0]) / r[2], 0f, 1f);
                    val = 1f - Math.clamp((float) (e.y() - r[1]) / r[3], 0f, 1f); applyAccent(); }
                case HEX -> { hexFocused = true;
                    Interface ii = iface();
                    hexBuf = String.format("%06X", (ii == null ? UICustom.accent
                        : ii.accent.get()) & 0xFFFFFF); }
                case DENSITY -> { if (i != null)
                    i.density.set(new String[]{"Compact", "Normal", "Spacious"}[r[5]]); }
                case RADIUS -> { if (i != null)
                    i.radius.set(Math.max(0, Math.min(24, i.radius.intVal() + r[5])) * 1.0); }
                case HUDSCALE -> { if (i != null)
                    i.hudScale.set(Math.max(0.5, Math.min(2.0,
                        (double) i.hudScale.get() + r[5] * 0.1))); }
                case MOTION -> { if (i != null) i.reducedMotion.set(!i.reducedMotion.on()); }
            }
            ModuleCard.click();
            return true;
        }
        hexFocused = false;
        return super.mouseClicked(e, dbl);
    }

    private void applyAccent() {
        Interface i = iface();
        if (i == null) return;
        int rgb = java.awt.Color.HSBtoRGB(hue, sat, val);
        i.accent.set(rgb);
        i.cardAcc.set(rgb);
    }

    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragRect != null) {
            if (dragRect[4] == HUE) {
                hue = Math.clamp((float) (e.x() - dragRect[0]) / dragRect[2], 0f, 1f);
            } else if (dragRect[4] == SV) {
                sat = Math.clamp((float) (e.x() - dragRect[0]) / dragRect[2], 0f, 1f);
                val = 1f - Math.clamp((float) (e.y() - dragRect[1]) / dragRect[3], 0f, 1f);
            }
            applyAccent();
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override public boolean mouseReleased(MouseButtonEvent e) {
        dragRect = null;
        return super.mouseReleased(e);
    }

    // 26.3: SDL text input must be claimed by a listener or char events die
    @Override public void added() { super.added(); minecraft.onTextInputFocusChange(this, true); }
    @Override public void removed() { minecraft.onTextInputFocusChange(this, false); super.removed(); }

    @Override public boolean charTyped(net.minecraft.client.input.CharacterEvent e) {
        if (hexFocused) {
            String c = e.codepointAsString().toUpperCase();
            if (c.matches("[0-9A-F]") && hexBuf.length() < 6) {
                hexBuf += c;
                if (hexBuf.length() == 6) applyHex();
            }
            return true;
        }
        return super.charTyped(e);
    }

    private void applyHex() {
        try {
            int rgb = Integer.parseInt(hexBuf, 16);
            Interface i = iface();
            if (i != null) { i.accent.set(0xFF000000 | rgb); i.cardAcc.set(0xFF000000 | rgb); }
            float[] hsv = java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF,
                (rgb >> 8) & 0xFF, rgb & 0xFF, null);
            hue = hsv[0]; sat = hsv[1]; val = hsv[2];
        } catch (NumberFormatException ignored) {}
    }

    @Override public boolean mouseScrolled(double mx, double my, double h, double v) {
        scroll = Math.max(0, scroll - (int) v * 20);
        return true;
    }

    @Override public boolean keyPressed(KeyEvent e) {
        if (hexFocused) {
            if (e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE
                && !hexBuf.isEmpty()) hexBuf = hexBuf.substring(0, hexBuf.length() - 1);
            else if (e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN) {
                applyHex(); hexFocused = false;
            } else if (e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE)
                hexFocused = false;
            return true;
        }
        if (e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            Minecraft.getInstance().setScreenAndShow(parent); return true;
        }
        return super.keyPressed(e);
    }

    @Override public boolean isPauseScreen() { return false; }
}
