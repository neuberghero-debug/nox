package dev.noxclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.noxclient.gui.theme.Theme;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Fixed header/footer and scrollable typed controls, bounded to the viewport. */
public final class SettingsPopup {
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

    public SettingsPopup(Module m, int x, int y, int screenW, int screenH) {
        module = m; px = x; py = y;
        layout();
    }
    private int rowH(Setting<?> s) {
        if (s == expandedMode) return 36 + expandedMode.modes.length * 22;
        if (s == expandedColor) return 174;
        return s instanceof Setting.Num ? 48 : 36;
    }
    private int contentHeight() {
        int total = 0;
        for (Setting<?> s : module.settings) total += rowH(s);
        return total;
    }
    private void layout() {
        var win = Minecraft.getInstance().getWindow();
        int sw = win.getGuiScaledWidth(), sh = win.getGuiScaledHeight();
        width = Math.min(360, Math.max(1, sw - 16));
        height = Math.min(contentHeight() + TITLE + FOOT + 12, Math.max(1, sh - 16));
        px = Math.max(8, Math.min(px, sw - width - 8));
        py = Math.max(8, Math.min(py, sh - height - 8));
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight() - bodyHeight())));
    }
    private int bodyHeight() { return Math.max(1, height - TITLE - FOOT - 12); }
    private int bodyTop() { return py + TITLE + 6; }
    private int rowY(Setting<?> target) {
        int y = bodyTop() - (int) scroll;
        for (Setting<?> s : module.settings) {
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
        Gui.textBold(g, font, clip(module.name, width - 130), px + PAD, py + 10, t.text());
        Gui.text(g, font, module.category.label + " / Settings", px + PAD, py + 26, UICustom.textDim);
        button(g, "Close", px + width - 56, py + 10, 44, mx, my);
        button(g, "Copy", px + width - 106, py + 10, 44, mx, my);
        g.fill(px + 1, py + TITLE, px + width - 1, py + TITLE + 1, UICustom.border);
        g.enableScissor(px + 4, bodyTop(), px + width - 4, bodyTop() + bodyHeight());
        int y = bodyTop() - (int) scroll;
        for (int i = 0; i < module.settings.size(); i++) {
            Setting<?> s = module.settings.get(i);
            int rh = rowH(s);
            if (y + rh > bodyTop() && y < bodyTop() + bodyHeight()) {
                if (i == focus) Gui.round(g, px + 8, y, width - 16, rh - 2, 5, 0x0DFFFFFF);
                row(g, s, y, mx, my);
            }
            y += rh;
        }
        g.disableScissor();
        if (contentHeight() > bodyHeight()) {
            int thumb = Math.max(16, bodyHeight() * bodyHeight() / contentHeight());
            int ty = bodyTop() + (int) ((bodyHeight() - thumb) * scroll / (contentHeight() - bodyHeight()));
            Gui.round(g, px + width - 7, ty, 2, thumb, 1, UICustom.borderHover);
        }
        int fy = py + height - FOOT;
        g.fill(px + 1, fy, px + width - 1, fy + 1, UICustom.border);
        button(g, "Reset", px + PAD, fy + 6, 52, mx, my);
        if (module instanceof dev.noxclient.module.render.Cosmetics)
            button(g, "Cape editor", px + PAD + 60, fy + 6, 78, mx, my);
        button(g, "Done", px + width - 68, fy + 6, 52, mx, my);
        if (!error.isEmpty())
            Gui.text(g, font, clip(error, width - 32), px + PAD, fy - 11, 0xFFFF8C8C);
    }
    private void row(GuiGraphicsExtractor g, Setting<?> s, int y, int mx, int my) {
        var font = Minecraft.getInstance().font;
        int right = px + width - PAD, fieldX = px + width / 2;
        int fieldW = right - fieldX;
        Gui.text(g, font, clip(s.name, width / 2 - PAD - 8), px + PAD, y + 12, Theme.current().text());
        if (s instanceof Setting.Bool b) {
            Gui.pill(g, right - 30, y + 10, 30, 14, b.on() ? Theme.current().accent() : 0xFF373D4A);
            Gui.circle(g, right - (b.on() ? 8 : 22), y + 17, 5, 0xFFF1F2F6);
        } else if (s instanceof Setting.Num n) {
            field(g, s, y, fieldX, fieldW, fmt(n.get()), mx, my);
            double span = n.max - n.min;
            double pct = span <= 0 ? 0 : Math.max(0, Math.min(1, (n.get() - n.min) / span));
            int barW = width - PAD * 2, barY = y + 34;
            Gui.pill(g, px + PAD, barY, barW, 3, UICustom.border);
            Gui.pill(g, px + PAD, barY, Math.max(3, (int) (barW * pct)), 3, Theme.current().accent());
            Gui.circle(g, px + PAD + (int) (barW * pct), barY + 1, 4, 0xFFFFFFFF);
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
            String value = s instanceof Setting.Bind b ? (capturing == b ? "Press a key…" : keyName(b.get())) : String.valueOf(s.get());
            field(g, s, y, fieldX, fieldW, value, mx, my);
        }
    }
    private void field(GuiGraphicsExtractor g, Setting<?> s, int y, int x, int w, String value, int mx, int my) {
        boolean active = editing == s || capturing == s;
        Gui.roundBorder(g, x, y + 6, w, 24, 5,
            active ? Theme.current().accent() : hit(mx, my, x, y + 6, w, 24) ? UICustom.borderHover : UICustom.border,
            0xFF10141C);
        String display = editing == s ? buffer + ((System.currentTimeMillis() / 500) % 2 == 0 ? "|" : "") : value;
        Gui.text(g, Minecraft.getInstance().font, clip(display, w - 14), x + 7, y + 13,
            active ? Theme.current().text() : UICustom.textDim);
    }
    private void colorPicker(GuiGraphicsExtractor g, int y) {
        int x = px + PAD, top = y + 40, w = width - PAD * 2;
        for (int i = 0; i < 64; i++) {
            int x0 = x + i * w / 64, x1 = x + (i + 1) * w / 64;
            int col = java.awt.Color.HSBtoRGB(hue, i / 63f, 1);
            g.fillGradient(x0, top, x1, top + 82, col, 0xFF000000);
            g.fill(x0, top + 92, x1, top + 102, java.awt.Color.HSBtoRGB(i / 63f, 1, 1));
            int a = (int) (i / 63f * 255);
            g.fill(x0, top + 112, x1, top + 122, 0xFF343944);
            g.fill(x0, top + 112, x1, top + 122, (expandedColor.get() & 0xFFFFFF) | a << 24);
        }
        Gui.roundBorder(g, x + (int) (saturation * (w - 1)) - 3, top + (int) ((1 - brightness) * 81) - 3,
            7, 7, 3, 0xFFFFFFFF, 0x00000000);
        g.fill(x + (int) (hue * (w - 1)), top + 90, x + (int) (hue * (w - 1)) + 2, top + 104, 0xFFFFFFFF);
        g.fill(x + (int) (alpha * (w - 1)), top + 110, x + (int) (alpha * (w - 1)) + 2, top + 124, 0xFFFFFFFF);
    }
    int mouseClicked(double mx, double my, int btn) {
        layout();
        if (!contains(mx, my)) { editing = null; capturing = null; return 2; }
        if (editing != null && !commit()) return 1;
        capturing = null; error = "";
        if (hit(mx, my, px + width - 56, py + 10, 44, 22)) return 2;
        if (hit(mx, my, px + width - 106, py + 10, 44, 22)) {
            dev.noxclient.NoxClient.INSTANCE.config.shareModule(module); return 1;
        }
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
        for (int i = 0; i < module.settings.size(); i++) {
            Setting<?> s = module.settings.get(i);
            if (my >= y && my < y + rowH(s)) {
                focus = i;
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
                else if (s instanceof Setting.Num n && my >= y + 28) { draggingSlider = n; updateSlider(mx); }
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
    private boolean commit() {
        if (editing == null) return true;
        try {
            if (editing instanceof Setting.Num n) {
                double value = Double.parseDouble(buffer);
                if (!Double.isFinite(value)) throw new IllegalArgumentException();
                n.set(Math.max(n.min, Math.min(n.max, value)));
            } else if (editing instanceof Setting.Col c) {
                String hex = buffer.startsWith("#") ? buffer.substring(1) : buffer;
                if (!hex.matches("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) throw new IllegalArgumentException();
                c.set((int) Long.parseLong(hex, 16) | (hex.length() == 6 ? 0xFF000000 : 0));
                expandedColor = null;
            } else if (editing instanceof Setting.Str s) s.set(buffer);
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
            if (key != InputConstants.KEY_ESCAPE) capturing.set(key == InputConstants.KEY_BACKSPACE ? -1 : key);
            capturing = null; return true;
        }
        if (key == InputConstants.KEY_TAB && !module.settings.isEmpty()) {
            focus = (focus + 1) % module.settings.size();
            Setting<?> s = module.settings.get(focus);
            int y = rowY(s);
            if (y < bodyTop()) scroll -= bodyTop() - y;
            else if (y + rowH(s) > bodyTop() + bodyHeight()) scroll += y + rowH(s) - bodyTop() - bodyHeight();
            layout(); return true;
        }
        if (key == InputConstants.KEY_RETURN && focus >= 0) {
            Setting<?> s = module.settings.get(focus);
            if (s instanceof Setting.Bool b) b.set(!b.on());
            else if (s instanceof Setting.Bind b) capturing = b;
            else if (s instanceof Setting.Mode m) expandedMode = expandedMode == m ? null : m;
            else beginEdit(s);
            return true;
        }
        return false;
    }
    boolean charTyped(char c) {
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
