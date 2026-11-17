package dev.noxclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.noxclient.gui.theme.Theme;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Fixed header/footer and scrollable typed controls, bounded to the viewport. */
public class SettingsPopup {
    private static final int PAD = 16, TITLE = 44, FOOT = 32;
    private final Module module;
    private int px, py, width, height;
    private double scroll;
    private boolean dragging;
    private int dragOX, dragOY, focus = -1, colorDrag;
    private Setting.Num draggingSlider;
    private Setting.Bind capturing;
    private Setting<?> editing;
    private Setting.Mode expandedMode;
    private Setting.Col expandedColor;
    private String buffer = "", error = "";
    private boolean replaceText;
    private float hue, saturation, brightness, alpha;
    private String filter = "";
    private boolean searchFocus;

    SettingsPopup(Module m, int atX, int atY, int scrW, int scrH) {
        module = m;
        px = Math.max(4, Math.min(atX, scrW - 424));
        py = Math.max(4, Math.min(atY, scrH - 200));
    }

    /** Settings rows after the in-popup search filter (>8 settings → search box). */
    private List<Setting<?>> rows() {
        if (filter.isEmpty()) return module.settings;
        List<Setting<?>> out = new ArrayList<>(module.settings.size());
        String f = filter.toLowerCase(java.util.Locale.ROOT);
        for (Setting<?> s : module.settings)
            if (s.name.toLowerCase(java.util.Locale.ROOT).contains(f)) out.add(s);
        return out;
    }

    private int rowH(Setting<?> s) {
        if (s == expandedColor) return 196;
        if (s == expandedMode) return 36 + expandedMode.modes.length * 22;
        return s instanceof Setting.Num ? 48 : 36;
    }
    private int contentHeight() {
        int h = 0; for (Setting<?> s : rows()) h += rowH(s); return h + 8;
    }
    private void layout() {
        width = Math.min(420, Minecraft.getInstance().getWindow().getGuiScaledWidth() - 24);
        height = Math.min(Minecraft.getInstance().getWindow().getGuiScaledHeight() - 24,
            Math.max(180, TITLE + FOOT + 14 + Math.min(contentHeight(), 360)));
        px = Math.max(4, Math.min(px, Minecraft.getInstance().getWindow().getGuiScaledWidth() - width - 4));
        py = Math.max(4, Math.min(py, Minecraft.getInstance().getWindow().getGuiScaledHeight() - height - 4));
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight() - bodyHeight())));
    }
    private int bodyHeight() { return Math.max(1, height - TITLE - FOOT - 12); }
    private int bodyTop() { return py + TITLE + 6; }
    private int rowY(Setting<?> target) {
        int y = bodyTop() - (int) scroll;
        for (Setting<?> s : rows()) {
            if (s == target) return y;
            y += rowH(s);
        }
        return y;
    }
    boolean contains(double x, double y) { return hit(x, y, px, py, width, height); }

    void render(GuiGraphicsExtractor g, int mx, int my) {
        layout();
        var font = Minecraft.getInstance().font;
        Theme t = Theme.current();
        Gui.shadow(g, px, py, width, height, 12);
        Gui.roundBorder(g, px, py, width, height, 12, UICustom.borderHover, UICustom.winFill);
        Gui.windowFx(px, py, width, height, 12);
        Gui.windowFrost(px, py, width, height, 12);
        Gui.round(g, px + PAD, py + 8, 26, 26, 7, UICustom.cardFillTop);
        Gui.roundBorder(g, px + PAD, py + 8, 26, 26, 7, UICustom.border, 0);
        Gui.textCenter(g, font, ModuleCard.glyph(module), px + PAD + 13, py + 17, t.accent());
        Gui.textBold(g, font, clip(module.name, width - 170), px + PAD + 34, py + 10, t.text());
        Gui.text(g, font, module.category.label + " / Settings", px + PAD + 34, py + 26, UICustom.textDim);
        button(g, "Close", px + width - 56, py + 10, 44, mx, my);
        button(g, "Copy", px + width - 106, py + 10, 44, mx, my);
        // in-popup settings search for modules with >8 settings
        if (module.settings.size() > 8) {
            int sw = 92, sx = px + width - 106 - sw - 6, sy = py + 11;
            Gui.roundBorder(g, sx, sy, sw, 20, 5,
                searchFocus ? t.accent() : UICustom.border, 0xFF0E131C);
            String sv = filter.isEmpty() && !searchFocus ? "Find…"
                : filter + (searchFocus && (System.currentTimeMillis() / 500) % 2 == 0 ? "▏" : "");
            Gui.text(g, font, clip(sv, sw - 10), sx + 6, sy + 7,
                filter.isEmpty() && !searchFocus ? UICustom.textMute : t.text());
        }
        Gui.fill(g, px + 1, py + TITLE, px + width - 1, py + TITLE + 1, UICustom.border);
        Gui.clipOn(px + 4, bodyTop(), px + width - 4, bodyTop() + bodyHeight());
        int y = bodyTop() - (int) scroll;
        List<Setting<?>> rs = rows();
        for (int i = 0; i < rs.size(); i++) {
            Setting<?> s = rs.get(i);
            int rh = rowH(s);
            if (y + rh > bodyTop() && y < bodyTop() + bodyHeight()) {
                if (i == focus) Gui.round(g, px + 8, y, width - 16, rh - 2, 5, 0x0DFFFFFF);
                row(g, s, y, mx, my);
            }
            y += rh;
        }
        Gui.clipOff();
        if (rs.isEmpty()) Gui.textCenter(g, font, "No settings match", px + width / 2, bodyTop() + 20, UICustom.textDim);
        if (contentHeight() > bodyHeight()) {
            int thumb = Math.max(16, bodyHeight() * bodyHeight() / contentHeight());
            int ty = bodyTop() + (int) ((bodyHeight() - thumb) * scroll / (contentHeight() - bodyHeight()));
            Gui.round(g, px + width - 7, ty, 2, thumb, 1, UICustom.borderHover);
        }
        int fy = py + height - FOOT;
        Gui.fill(g, px + 1, fy, px + width - 1, fy + 1, UICustom.border);
        button(g, "Reset", px + PAD, fy + 6, 52, mx, my);
        if (module instanceof dev.noxclient.module.render.Cosmetics)
            button(g, "Cape editor", px + PAD + 60, fy + 6, 78, mx, my);
        button(g, "Done", px + width - 68, fy + 6, 52, mx, my);
        if (!error.isEmpty())
            Gui.text(g, font, clip(error, width - 32), px + PAD, fy - 11, 0xFFFF8C8C);
        // setting-description tooltip — hovered row with a desc
        for (int i = 0; i < rs.size(); i++) {
            Setting<?> s = rs.get(i);
            int ry = rowY(s), rh = rowH(s);
            if (ry + rh <= bodyTop() || ry >= bodyTop() + bodyHeight()) continue;
            int cy = Math.max(ry, bodyTop()), ch = Math.min(ry + rh, bodyTop() + bodyHeight()) - cy;
            if (!hit(mx, my, px + 8, cy, width - 16, ch) || s.desc.isEmpty()) continue;
            int tw = Gui.width(font, s.desc) + 16;
            int tx = Math.max(px + 8, Math.min(px + width - 8 - tw, mx + 12));
            int ty2 = my + 16 + 26 > py + height ? my - 32 : my + 14;
            Gui.shadow(g, tx, ty2, tw, 24, 6);
            Gui.roundBorder(g, tx, ty2, tw, 24, 6, UICustom.border, 0xF6141821);
            Gui.text(g, font, s.desc, tx + 8, ty2 + 8, t.text());
            break;
        }
    }

    private void row(GuiGraphicsExtractor g, Setting<?> s, int y, int mx, int my) {
        var font = Minecraft.getInstance().font;
        int right = px + width - PAD, fieldX = px + width / 2;
        int fieldW = right - fieldX;
        Gui.text(g, font, clip(s.name, width / 2 - PAD - 8), px + PAD, y + 12, Theme.current().text());
        // per-setting reset ↺ — sits between label and the control
        boolean rhov = hit(mx, my, fieldX - 18, y + 10, 16, 16);
        Gui.text(g, font, "↺", fieldX - 16, y + 12, rhov ? Theme.current().accent() : UICustom.textMute);
        if (s instanceof Setting.Bool b) {
            Gui.pill(g, right - 30, y + 10, 30, 14, b.on() ? Theme.current().accent() : 0xFF373D4A);
            Gui.circle(g, right - (b.on() ? 8 : 22), y + 17, 5, 0xFFF1F2F6);
        } else if (s instanceof Setting.Num n) {
            field(g, s, y, fieldX, fieldW, fmt(n.get()), mx, my);
            double span = n.max - n.min;
            double pct = span <= 0 ? 0 : Math.max(0, Math.min(1, (n.get() - n.min) / span));
            int barW = width - PAD * 2 - 8, barY = y + 34;
            Gui.pill(g, px + PAD, barY, barW, 3, UICustom.border);
            Gui.pill(g, px + PAD, barY, Math.max(3, (int) (barW * pct)), 3, Theme.current().accent());
            Gui.circle(g, px + PAD + (int) (barW * pct), barY + 1, 4, 0xFFFFFFFF);
            Gui.text(g, font, fmt(n.min), px + PAD, barY + 8, UICustom.textMute);
            Gui.text(g, font, fmt(n.max), px + PAD + barW + 4, barY - 1, UICustom.textMute);
        } else if (s instanceof Setting.Mode m) {
            field(g, s, y, fieldX, fieldW, m.get() + (expandedMode == m ? "  −" : "  +"), mx, my);
            if (expandedMode == m) {
                for (int i = 0; i < m.modes.length; i++) {
                    int yy = y + 36 + i * 22;
                    boolean selected = m.get().equals(m.modes[i]);
                    Gui.round(g, px + PAD, yy, width - PAD * 2, 20, 4,
                        selected ? Theme.current().accent() & 0x33FFFFFF : 0xFF1C222E);
                    Gui.text(g, font, m.modes[i], px + PAD + 8, yy + 6, selected ? Theme.current().text() : UICustom.textDim);
                }
            }
        } else if (s instanceof Setting.Col c) {
            field(g, s, y, fieldX, fieldW - 28, String.format("#%08X", c.get()), mx, my);
            Gui.roundBorder(g, right - 22, y + 8, 22, 20, 4, UICustom.borderHover, c.get() | 0xFF000000);
            if (expandedColor == c) colorPicker(g, y);
        } else {
            String value = s instanceof Setting.Bind b
                ? (capturing == b ? "Press a key…" : keyName(b.get()))
                : s instanceof Setting.Str st && st.get().isEmpty() ? "(empty)" : String.valueOf(s.get());
            field(g, s, y, fieldX, fieldW, value, mx, my);
        }
    }
    private void field(GuiGraphicsExtractor g, Setting<?> s, int y, int x, int w, String value, int mx, int my) {
        boolean active = editing == s || capturing == s;
        Gui.roundBorder(g, x, y + 6, w, 24, 5,
            active ? Theme.current().accent() : hit(mx, my, x, y + 6, w, 24) ? UICustom.borderHover : UICustom.border,
            0xFF10141C);
        String display = editing == s ? buffer + ((System.currentTimeMillis() / 500) % 2 == 0 ? "|" : "") : value;
        Gui.text(g, Minecraft.getInstance().font, clip(display, w - 12), x + 6, y + 14,
            active ? Theme.current().text() : UICustom.textDim);
    }
    private void colorPicker(GuiGraphicsExtractor g, int y) {
        for (int i = 0; i < 86; i++) {
            float p = i / 86f;
            for (int j = 0; j < 4; j++)
                Gui.fill(g, px + PAD, y + 40 + i, px + width - PAD, y + 40 + i + 1,
                    java.awt.Color.HSBtoRGB(hue, p, (4 - j) / 4f) | 0xFF000000);
            break;
        }
        // saturation×brightness plane (sampled bands — extractor only does fills)
        for (int i = 0; i < 86; i += 2) {
            float p = i / 86f;
            Gui.fill(g, px + PAD, y + 40 + i, px + width - PAD, y + 40 + i + 2,
                java.awt.Color.HSBtoRGB(hue, p, 1f) | 0xFF000000);
        }
        for (int i = 0; i < 86; i += 2) {
            float p = i / 86f;
            int dark = (int) (p * 255) << 24;
            Gui.fill(g, px + PAD, y + 40 + i, px + width - PAD, y + 40 + i + 2, dark);
        }
        int sy = y + 40 + (int) ((1 - brightness) * 82), sx2 = px + PAD + (int) (saturation * (width - PAD * 2));
        Gui.circle(g, Math.min(px + width - PAD - 3, Math.max(px + PAD + 3, sx2)), Math.min(y + 126, Math.max(y + 43, sy)), 3, 0xFFFFFFFF);
        int hy = y + 132;
        for (int i = 0; i < width - PAD * 2; i += 3)
            Gui.fill(g, px + PAD + i, hy, px + PAD + i + 3, hy + 8,
                java.awt.Color.HSBtoRGB(i / (float) (width - PAD * 2), 1f, 1f) | 0xFF000000);
        Gui.roundBorder(g, px + PAD, hy, width - PAD * 2, 8, 3, UICustom.border, 0);
        Gui.circle(g, px + PAD + (int) (hue * (width - PAD * 2)), hy + 4, 3, 0xFFFFFFFF);
        int ay = y + 146;
        Gui.clipOn(px + PAD, ay, px + width - PAD, ay + 8);
        Gui.fill(g, px + PAD, ay, px + width - PAD, ay + 8, 0xFF000000);
        Gui.fill(g, px + PAD, ay, px + PAD + (int) ((width - PAD * 2) * alpha), ay + 8,
            java.awt.Color.HSBtoRGB(hue, saturation, brightness) | 0xFF000000);
        Gui.clipOff();
        Gui.roundBorder(g, px + PAD, ay, width - PAD * 2, 8, 3, UICustom.border, 0);
    }

    int mouseClicked(double mx, double my, int btn) {
        layout();
        if (!contains(mx, my)) {
            if (editing != null) commit();   // outside click commits, never silently drops the edit
            capturing = null; searchFocus = false; return 2;
        }
        if (editing != null && !commit()) return 1;
        capturing = null; error = "";
        if (hit(mx, my, px + width - 56, py + 10, 44, 22)) return 2;
        if (hit(mx, my, px + width - 106, py + 10, 44, 22)) {
            dev.noxclient.NoxClient.INSTANCE.config.shareModule(module); return 1;
        }
        searchFocus = module.settings.size() > 8
            && hit(mx, my, px + width - 106 - 92 - 6, py + 11, 92, 20);
        int fy = py + height - FOOT;
        if (my >= fy) {
            if (hit(mx, my, px + width - 68, fy + 6, 52, 22)) return 2;
            if (hit(mx, my, px + PAD, fy + 6, 52, 22)) {
                for (Setting<?> s : module.settings) s.reset();
                expandedColor = null; expandedMode = null;
            }
            if (module instanceof dev.noxclient.module.render.Cosmetics && hit(mx, my, px + PAD + 60, fy + 6, 78, 22)) {
                Minecraft mc = Minecraft.getInstance();
                mc.setScreenAndShow(new CapeEditorScreen(mc.gui.screen()));
            }
            return 1;
        }
        if (my < py + TITLE) { dragging = true; dragOX = (int) mx - px; dragOY = (int) my - py; return 1; }
        if (my < bodyTop() || my >= bodyTop() + bodyHeight()) return 1;
        int y = bodyTop() - (int) scroll;
        List<Setting<?>> rs = rows();
        for (int i = 0; i < rs.size(); i++) {
            Setting<?> s = rs.get(i);
            if (my >= y && my < y + rowH(s)) {
                focus = i;
                if (hit(mx, my, px + width / 2 - 18, y + 10, 16, 16)) { s.reset(); return 1; }
                if (s == expandedColor && my >= y + 40) {
                    colorDrag = my < y + 126 ? 1 : my < y + 148 ? 2 : 3;
                    updateColor(mx, my); return 1;
                }
                if (s == expandedMode && my >= y + 36) {
                    int index = (int) (my - y - 36) / 22;
                    if (index < expandedMode.modes.length) expandedMode.set(expandedMode.modes[index]);
                    expandedMode = null; return 1;
                }
                if (s instanceof Setting.Bool b) b.set(!b.on());
                else if (s instanceof Setting.Mode m) { expandedMode = expandedMode == m ? null : m; expandedColor = null; }
                else if (s instanceof Setting.Bind b) capturing = b;
                else if (s instanceof Setting.Num n && my >= y + 30) { draggingSlider = n; updateSlider(mx); }
                else if (s instanceof Setting.Col c && mx > px + width - PAD - 28) {
                    expandedColor = expandedColor == c ? null : c; expandedMode = null;
                    float[] hsv = java.awt.Color.RGBtoHSB((c.get() >> 16) & 255, (c.get() >> 8) & 255, c.get() & 255, null);
                    hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]; alpha = (c.get() >>> 24) / 255f;
                } else beginEdit(s);
                return 1;
            }
            y += rowH(s);
        }
        return 1;
    }
    private void beginEdit(Setting<?> s) {
        editing = s; replaceText = true; error = "";
        buffer = s instanceof Setting.Col c ? String.format("#%08X", c.get()) : String.valueOf(s.get());
    }
    @SuppressWarnings("unchecked")
    private boolean commit() {
        try {
            String v = buffer.trim();
            if (editing instanceof Setting.Num n) {
                double d = Double.parseDouble(v);
                if (!Double.isFinite(d)) throw new IllegalArgumentException();
                ((Setting<Double>) n).set(Math.min(n.max, Math.max(n.min, d)));
            } else if (editing instanceof Setting.Col c) {
                ((Setting<Integer>) c).set((int) Long.parseLong(v.startsWith("#") ? v.substring(1) : v, 16));
            } else if (editing instanceof Setting.Str st) {
                ((Setting<String>) st).set(v);
            } else if (editing != null) {
                ((Setting<String>) editing).set(v);
            }
            editing = null; error = ""; return true;
        } catch (IllegalArgumentException ex) {
            error = editing instanceof Setting.Col ? "Enter RGB or ARGB hex" : "Enter a finite number";
            return false;
        }
    }
    private void updateSlider(double mx) {
        double pct = Math.max(0, Math.min(1, (mx - px - PAD) / (width - PAD * 2.0)));
        draggingSlider.set(draggingSlider.min + pct * (draggingSlider.max - draggingSlider.min));
    }
    private void updateColor(double mx, double my) {
        if (expandedColor == null) return;
        float f = (float) Math.max(0, Math.min(1, (mx - px - PAD) / (width - PAD * 2.0)));
        if (colorDrag == 1) {
            saturation = f; brightness = (float) (1 - Math.max(0, Math.min(1, (my - rowY(expandedColor) - 40) / 82)));
        } else if (colorDrag == 2) hue = f;
        else if (colorDrag == 3) alpha = f;
        expandedColor.set((java.awt.Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF) | ((int) (alpha * 255) << 24));
    }
    void mouseDragged(double mx, double my) {
        if (draggingSlider != null) updateSlider(mx);
        else if (colorDrag != 0) updateColor(mx, my);
        else if (dragging) { px = (int) mx - dragOX; py = (int) my - dragOY; layout(); }
    }
    void mouseReleased() { dragging = false; draggingSlider = null; colorDrag = 0; }
    void mouseScrolled(double mx, double my, double amount) { scroll -= amount * 30; layout(); }
    boolean keyPressed(int key) {
        if (searchFocus) {
            if (key == InputConstants.KEY_ESCAPE) { if (filter.isEmpty()) searchFocus = false; else filter = ""; }
            else if (key == InputConstants.KEY_BACKSPACE && !filter.isEmpty())
                filter = filter.substring(0, filter.offsetByCodePoints(filter.length(), -1));
            return true;
        }
        if (editing != null) {
            if (key == InputConstants.KEY_ESCAPE) { editing = null; error = ""; }
            else if (key == InputConstants.KEY_RETURN) commit();
            else if (key == InputConstants.KEY_BACKSPACE && !buffer.isEmpty()) {
                buffer = replaceText ? "" : buffer.substring(0, buffer.offsetByCodePoints(buffer.length(), -1));
                replaceText = false;
            }
            return true;
        }
        if (capturing != null) {
            if (key != InputConstants.KEY_ESCAPE) {
                boolean unbind = key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE;
                capturing.set(unbind ? -1 : key);
                if (!unbind) warnCollision(key);
            }
            capturing = null; return true;
        }
        List<Setting<?>> rs = rows();
        if (!rs.isEmpty() && focus >= 0 && focus < rs.size()
                && rs.get(focus) instanceof Setting.Mode m
                && (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT)) {
            int i = Arrays.asList(m.modes).indexOf(m.get());
            if (i < 0) i = 0;
            m.set(m.modes[Math.floorMod(i + (key == InputConstants.KEY_RIGHT ? 1 : -1), m.modes.length)]);
            return true;
        }
        if (key == InputConstants.KEY_TAB && !rs.isEmpty()) {
            focus = (focus + 1) % rs.size();
            Setting<?> s = rs.get(focus);
            int y = rowY(s);
            if (y < bodyTop()) scroll -= bodyTop() - y;
            else if (y + rowH(s) > bodyTop() + bodyHeight()) scroll += y + rowH(s) - bodyTop() - bodyHeight();
            layout(); return true;
        }
        if (key == InputConstants.KEY_RETURN && focus >= 0 && focus < rs.size()) {
            Setting<?> s = rs.get(focus);
            if (s instanceof Setting.Bool b) b.set(!b.on());
            else if (s instanceof Setting.Bind b) capturing = b;
            else if (s instanceof Setting.Mode m) expandedMode = expandedMode == m ? null : m;
            else beginEdit(s);
            return true;
        }
        return false;
    }
    /** Inline warning when a captured key collides with another module's bind. */
    private void warnCollision(int key) {
        for (Module om : dev.noxclient.NoxClient.INSTANCE.modules.all()) {
            if (om != module && om.keybind() == key) { error = "Conflicts with " + om.name; return; }
            for (Setting<?> os : om.settings)
                if (os instanceof Setting.Bind b && os != capturing && b.get() == key) {
                    error = "Conflicts with " + om.name + " " + os.name; return;
                }
        }
    }
    boolean charTyped(char c) {
        if (searchFocus) {
            if (c >= 32 && c != 127 && filter.length() < 32) { filter += c; scroll = 0; focus = -1; }
            return true;
        }
        if (editing == null) return false;
        if (c >= 32 && c != 127 && buffer.length() < 512) {
            if (replaceText) { buffer = ""; replaceText = false; }
            buffer += c;
        }
        return true;
    }
    private static boolean hit(double x, double y, int rx, int ry, int w, int h) {
        return x >= rx && x < rx + w && y >= ry && y < ry + h;
    }
    private static void button(GuiGraphicsExtractor g, String text, int x, int y, int w, int mx, int my) {
        Gui.round(g, x, y, w, 22, 5, hit(mx, my, x, y, w, 22) ? 0xFF303747 : 0xFF222936);
        Gui.textCenter(g, Minecraft.getInstance().font, text, x + w / 2, y + 7, Theme.current().text());
    }
    private static String clip(String s, int w) {
        var font = Minecraft.getInstance().font;
        if (Gui.width(font, s) <= w) return s;
        int end = s.length();
        while (end > 0 && Gui.width(font, s.substring(0, end) + "…") > w) end = s.offsetByCodePoints(end, -1);
        return s.substring(0, end) + "…";
    }
    static String keyName(int code) {
        if (code <= 0) return "Unbound";
        return InputConstants.Type.KEYBOARD.getOrCreate(code).getDisplayName().getString();
    }
    private static String fmt(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
