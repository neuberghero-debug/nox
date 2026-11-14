package dev.noxclient.gui;

import dev.noxclient.gui.theme.Theme;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * Floating settings popup for one module — its own little draggable window
 * rendered on top of the card grid. Replaces the old inline dropdown.
 */
public class SettingsPopup {
    private static final int PAD = 14, TITLE = 30, W = 250;

    private final Module module;
    private int px, py;
    private boolean dragging;
    private int dragOX, dragOY;
    private Setting.Num draggingSlider;
    private Setting.Bind capturing;
    @SuppressWarnings("unchecked")
    private Setting<String> editingStr;
    private String strBuf = "";

    public SettingsPopup(Module m, int x, int y, int screenW, int screenH) {
        module = m;
        int h = height();
        px = Math.max(4, Math.min(x, screenW - W - 4));
        py = Math.max(4, Math.min(y, screenH - h - 4));
    }

    private static int rowH(Setting<?> s) { return s instanceof Setting.Num ? 34 : 26; }

    private int height() {
        int h = TITLE + 8 + (module.settings.isEmpty() ? 18 : 0);
        for (Setting<?> s : module.settings) h += rowH(s);
        return h + 8;
    }

    boolean contains(double mx, double my) {
        return mx >= px && mx <= px + W && my >= py && my <= py + height();
    }

    void render(GuiGraphicsExtractor g, int mx, int my) {
        Theme t = Theme.current();
        var font = Minecraft.getInstance().font;
        int h = height();

        Gui.shadow(g, px, py, W, h, 11);
        Gui.round(g, px, py, W, h, 11, t.accent() & 0x66FFFFFF);
        Gui.vgrad(g, px + 1, py + 1, W - 2, h - 2, 10, UICustom.cardFillTop, UICustom.cardFillBottom);

        // header
        Gui.textBold(g, font, module.name, px + PAD, py + 10, t.accent());
        Gui.text(g, font, "settings", px + PAD + Gui.width(font, module.name) + 6, py + 10, 0xFF6A6A80);
        g.fill(px + PAD, py + TITLE - 1, px + W - PAD, py + TITLE, UICustom.border);
        boolean ch = ModuleCard.inside(mx, my, px + W - 24, py + 6, 18, 18);
        if (ch) Gui.circle(g, px + W - 15, py + 15, 9, 0x40FF5555);
        Gui.text(g, font, "✕", px + W - 19, py + 10, ch ? 0xFFFF5555 : 0xFF888899);
        boolean sh = ModuleCard.inside(mx, my, px + W - 44, py + 6, 18, 18);
        if (sh) Gui.circle(g, px + W - 35, py + 15, 9, 0x30FFFFFF);
        Gui.text(g, font, "↗", px + W - 39, py + 10, sh ? t.accent() : 0xFF888899);
        if (module instanceof dev.noxclient.module.render.Cosmetics) {
            boolean eh = ModuleCard.inside(mx, my, px + W - 64, py + 6, 18, 18);
            if (eh) Gui.circle(g, px + W - 55, py + 15, 9, 0x30FFFFFF);
            Gui.text(g, font, "✎", px + W - 59, py + 10, eh ? t.accent() : 0xFF888899);
        }

        int sy = py + TITLE + 6;
        if (module.settings.isEmpty())
            Gui.textCenter(g, font, "no settings", px + W / 2, sy + 4, 0xFF5A5A6E);
        for (Setting<?> s : module.settings) {
            row(g, font, t, s, sy, mx, my);
            sy += rowH(s);
        }
    }

    private void row(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font,
                     Theme t, Setting<?> s, int sy, int mx, int my) {
        if (s instanceof Setting.Bool b) {
            Gui.text(g, font, s.name, px + PAD, sy + 8, t.text());
            int bx = px + W - PAD - 28, by = sy + 6;
            Gui.pill(g, bx, by, 28, 13, b.on() ? t.accent() : 0xFF32324A);
            Gui.circle(g, bx + (b.on() ? 20 : 7), by + 6, 5, 0xFFFFFFFF);
        } else if (s instanceof Setting.Num n) {
            Gui.text(g, font, s.name, px + PAD, sy + 5, t.text());
            String v = fmt(n.get());
            Gui.text(g, font, v, px + W - PAD - Gui.width(font, v), sy + 5, t.accent());
            int sx = px + PAD, sw = W - PAD * 2, ly = sy + 20;
            double pct = (n.get() - n.min) / (n.max - n.min);
            Gui.pill(g, sx, ly, sw, 4, UICustom.border);
            Gui.pill(g, sx, ly, Math.max(4, (int) (sw * pct)), 4, t.accent());
            boolean knobHov = ModuleCard.inside(mx, my, sx + (int) (sw * pct) - 6, ly - 4, 12, 12);
            Gui.circle(g, sx + (int) (sw * pct), ly + 2, knobHov || draggingSlider == n ? 5 : 4, 0xFFFFFFFF);
        } else if (s instanceof Setting.Mode mo) {
            Gui.text(g, font, s.name, px + PAD, sy + 8, t.text());
            String cur = "‹ " + mo.get() + " ›";
            int mw = Gui.width(font, cur) + 12;
            boolean hov = ModuleCard.inside(mx, my, px + W - PAD - mw, sy + 4, mw, 15);
            Gui.pill(g, px + W - PAD - mw, sy + 4, mw, 15, hov ? t.accent() & 0x88FFFFFF : t.accent() & 0x44FFFFFF);
            Gui.text(g, font, cur, px + W - PAD - mw + 6, sy + 7, hov ? 0xFFFFFFFF : t.accent());
        } else if (s instanceof Setting.Bind bi) {
            Gui.text(g, font, s.name, px + PAD, sy + 8, t.text());
            String kn = capturing == bi ? "press key…" : keyName(bi.get());
            int kw = Gui.width(font, kn) + 14;
            boolean hov = ModuleCard.inside(mx, my, px + W - PAD - kw, sy + 4, kw, 15);
            Gui.pill(g, px + W - PAD - kw, sy + 4, kw, 15,
                capturing == bi ? t.accent() : hov ? t.accent() & 0x88FFFFFF : 0xFF32324A);
            Gui.text(g, font, kn, px + W - PAD - kw + 7, sy + 7,
                capturing == bi ? 0xFF101018 : hov ? 0xFFFFFFFF : t.accent());
        } else if (s instanceof Setting.Str) {
            Gui.text(g, font, s.name, px + PAD, sy + 8, t.text());
            boolean editing = editingStr == s;
            String val = editing ? strBuf + "▌" : String.valueOf(s.get());
            val = clip(font, val, W - PAD * 2 - 90);
            int vw = Math.max(60, Gui.width(font, val) + 14);
            boolean hov = ModuleCard.inside(mx, my, px + W - PAD - vw, sy + 4, vw, 15);
            Gui.pill(g, px + W - PAD - vw, sy + 4, vw, 15,
                editing ? t.accent() : hov ? t.accent() & 0x88FFFFFF : 0xFF32324A);
            Gui.text(g, font, val, px + W - PAD - vw + 7, sy + 7,
                editing ? 0xFF101018 : hov ? 0xFFFFFFFF : 0xFF9C9CB8);
        } else if (s instanceof Setting.Col c) {
            Gui.text(g, font, s.name, px + PAD, sy + 8, t.text());
            String hex = String.format("#%06X", c.get() & 0xFFFFFF);
            Gui.text(g, font, hex, px + W - PAD - 30 - Gui.width(font, hex), sy + 8, 0xFF6A6A80);
            Gui.roundBorder(g, px + W - PAD - 22, sy + 5, 22, 14, 4, t.accent(), c.get() | 0xFF000000);
        } else {
            Gui.text(g, font, s.name + ":  " + s.get(), px + PAD, sy + 8, t.text());
        }
    }

    /** @return true when the popup consumed the click (or should close via 2) */
    int mouseClicked(double mx, double my, int btn) {
        if (!contains(mx, my)) return 0;
        // a click anywhere else commits/cancels pending text edit or keybind capture
        if (editingStr != null) { editingStr.set(strBuf); editingStr = null; }
        if (capturing != null) { capturing.set(-1); capturing = null; }
        if (ModuleCard.inside(mx, my, px + W - 24, py + 6, 18, 18)) return 2;
        if (module instanceof dev.noxclient.module.render.Cosmetics
            && ModuleCard.inside(mx, my, px + W - 64, py + 6, 18, 18)) {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreenAndShow(new CapeEditorScreen(mc.gui.screen()));
            return 1;
        }
        if (ModuleCard.inside(mx, my, px + W - 44, py + 6, 18, 18)) {
            dev.noxclient.NoxClient.INSTANCE.config.shareModule(module);
            Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            return 1;
        }
        if (ModuleCard.inside(mx, my, px, py, W, TITLE)) {
            dragging = true; dragOX = (int) mx - px; dragOY = (int) my - py; return 1;
        }
        int sy = py + TITLE + 6;
        for (Setting<?> s : module.settings) {
            if (ModuleCard.inside(mx, my, px + 8, sy, W - 16, rowH(s))) { clickSetting(s, mx, btn); return 1; }
            sy += rowH(s);
        }
        return 1;
    }

    private void clickSetting(Setting<?> s, double mx, int btn) {
        if (s instanceof Setting.Bool b) b.set(!b.on());
        else if (s instanceof Setting.Mode mo) {
            int i = java.util.Arrays.asList(mo.modes).indexOf(mo.get());
            i = btn == 1 ? (i - 1 + mo.modes.length) % mo.modes.length : (i + 1) % mo.modes.length;
            mo.set(mo.modes[i]);
        } else if (s instanceof Setting.Str) {
            @SuppressWarnings("unchecked") var st = (Setting<String>) s;
            editingStr = st; strBuf = st.get();
        } else if (s instanceof Setting.Col c) {
            int[] pal = {0xFF6EC7, 0xFF5555, 0xFFAA00, 0x55FF88, 0x5599FF, 0xB44DFF, 0xFFFFFF};
            int i = 0;
            // compare RGB only — stored value carries full alpha, entries are 24-bit
            for (int k = 0; k < pal.length; k++) if ((c.get() & 0xFFFFFF) == (pal[k] & 0xFFFFFF)) i = k;
            c.set(pal[(i + 1) % pal.length]);
        } else if (s instanceof Setting.Bind bi) {
            capturing = bi;
        } else if (s instanceof Setting.Num n) {
            draggingSlider = n;
            n.set(n.min + pct(mx) * (n.max - n.min));
        }
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private double pct(double mx) {
        return Math.max(0, Math.min(1, (mx - (px + PAD)) / (W - PAD * 2.0)));
    }

    void mouseDragged(double mx, double my) {
        if (draggingSlider != null) {
            draggingSlider.set(draggingSlider.min + pct(mx) * (draggingSlider.max - draggingSlider.min));
            return;
        }
        if (dragging) {
            var win = Minecraft.getInstance().getWindow();
            px = Math.max(-W + 40, Math.min((int) mx - dragOX, win.getGuiScaledWidth() - 40));
            py = Math.max(0, Math.min((int) my - dragOY, win.getGuiScaledHeight() - 20));
        }
    }

    void mouseReleased() { dragging = false; draggingSlider = null; }

    /** @return true when a bind capture / str edit consumed the key. */
    boolean keyPressed(int key) {
        if (editingStr != null) {
            if (key == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN) {
                editingStr.set(strBuf); editingStr = null;
            } else if (key == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
                editingStr = null; // cancel
            } else if (key == com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE
                && !strBuf.isEmpty()) {
                strBuf = strBuf.substring(0, strBuf.length() - 1);
            }
            return true;
        }
        if (capturing == null) return false;
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) capturing.set(-1);
        else capturing.set(key);
        capturing = null;
        return true;
    }

    boolean charTyped(char c) {
        if (editingStr == null) return false;
        if (strBuf.length() < 120 && c >= 32 && c != 127) strBuf += c;
        return true;
    }

    private static String clip(net.minecraft.client.gui.Font font, String s, int w) {
        String out = s;
        while (Gui.width(font, out + "…") > w && !out.isEmpty())
            out = out.substring(0, out.length() - 1);
        return out.equals(s) ? s : out + "…";
    }

    static String keyName(int code) {
        if (code <= 0) return "none";
        try { return com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD
            .getOrCreate(code).getDisplayName().getString(); }
        catch (Throwable t) { return "key " + code; }
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format("%.2f", v);
    }
}
