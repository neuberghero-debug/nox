package dev.noxclient.gui;

import dev.noxclient.NoxClient;
import dev.noxclient.gui.theme.Theme;
import dev.noxclient.module.Module;
import dev.noxclient.module.render.Interface;
import dev.noxclient.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * HUD editor — drag every HUD element to a free position with edge/center
 * snapping. Positions land in HudLayout (normalized, persisted to nox/hud.json)
 * and NoxOverlay picks them up live while the world keeps rendering behind.
 * Right-click an element to reset just that one; the bottom bar carries
 * per-module settings, full reset and done.
 */
public class HudEditorScreen extends Screen {
    /** id → label, owning module name (may be null → always HudWidgets). */
    private record El(String id, String label, String module, int dw, int dh) {}
    private static final El[] ELS = {
        new El("info",         "Info column",   "HudWidgets",   90, 44),
        new El("arraylist",    "Module list",   "HudWidgets",   90, 52),
        new El("compass",      "Compass",       "HudWidgets",  220, 16),
        new El("coords",       "Coords card",   "HudWidgets",  150, 22),
        new El("gear",         "Armor",         "HudWidgets",   92, 18),
        new El("totems",       "Totems",        "HudWidgets",   40, 16),
        new El("targethud",    "TargetHUD",     "TargetHUD",   148, 46),
        new El("sessionstats", "Session stats", "SessionStats",110, 50),
        new El("spotify",      "Spotify",       "SpotifyHud",  160, 22),
        new El("lyrics",       "Lyrics",        "LyricsSky",   200, 12),
        new El("twitch",       "Twitch chat",   "TwitchChat",  200, 60),
        new El("scoreboard",   "Scoreboard",    "Scoreboard",  120, 80),
        new El("auction",      "Auction",       "Auction",     190, 44),
    };

    private final Screen parent;
    private String dragging, nudgeTarget;
    private float dragDX, dragDY;         // grab offset inside the element (px)
    private int snapVX = -1, snapHY = -1; // guide line positions while snapped
    private SettingsPopup popup;
    private int barY;                     // bottom toolbar y
    private int settingsX, resetX, doneX, guiX; // toolbar hit rects (x starts)

    public HudEditorScreen(Screen parent) {
        super(Component.literal("HUD Editor"));
        this.parent = parent;
    }

    /** Element rect in screen px: live-reported rect wins, else stored pos,
     * else a nominal box at its default anchor spot. */
    private int[] rect(El el, int w, int h) {
        int[] live = HudLayout.rect(el.id);
        if (live != null) return live;
        float[] p = HudLayout.get(el.id);
        if (p != null) return new int[]{(int) (p[0] * w), (int) (p[1] * h), el.dw, el.dh};
        return switch (el.id) {
            case "info" -> new int[]{6, 4, el.dw, el.dh};
            case "arraylist" -> new int[]{w - 6 - el.dw, 4, el.dw, el.dh};
            case "compass" -> new int[]{w / 2 - el.dw / 2, 6, el.dw, el.dh};
            case "gear" -> new int[]{6, h - 46, el.dw, el.dh};
            case "totems" -> new int[]{w - 6 - el.dw, h - 46, el.dw, el.dh};
            case "targethud" -> new int[]{w / 2 - el.dw / 2, h / 2 - 60, el.dw, el.dh};
            case "sessionstats" -> new int[]{6, 60, el.dw, el.dh};
            case "spotify" -> new int[]{w / 2 - el.dw / 2, h - 70, el.dw, el.dh};
            default -> new int[]{w / 2 - el.dw / 2, h - 30, el.dw, el.dh};
        };
    }

    private boolean enabled(El el) {
        Module m = NoxClient.INSTANCE.modules.get(el.module);
        return m != null && m.isEnabled();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        var t = Theme.current();
        g.fill(0, 0, width, height, 0x55000000);

        // snap guides
        if (snapVX >= 0) Gui.fill(g, snapVX, 0, snapVX + 1, height, UICustom.accent & 0x77FFFFFF);
        if (snapHY >= 0) Gui.fill(g, 0, snapHY, width, snapHY + 1, UICustom.accent & 0x77FFFFFF);

        for (El el : ELS) {
            int[] r = rect(el, width, height);
            boolean on = enabled(el), hov = ModuleCard.inside(mx, my, r[0], r[1], r[2], r[3]);
            boolean drag = el.id.equals(dragging);
            int border = drag || hov ? UICustom.accent : 0x55FFFFFF;
            Gui.roundBorder(g, r[0], r[1], r[2], r[3], 6, border,
                on ? 0x5510121A : 0x3310121A);
            Gui.textCenter(g, font, el.label, r[0] + r[2] / 2,
                r[1] + Math.max(2, (r[3] - 9) / 2),
                on ? UICustom.text : UICustom.textDim);
            if (!on) Gui.text(g, font, "off", r[0] + 4, r[1] + 4, UICustom.textMute);
            if (drag) Gui.text(g, font, r[0] + "," + r[1], r[0], r[1] + r[3] + 3,
                UICustom.accent);
        }

        // top hint + GUI layout cycle
        Gui.text(g, font, "HUD EDITOR", 12, 12, UICustom.accent);
        Gui.text(g, font, "drag to move · right-click resets · esc saves", 88, 12, UICustom.textDim);
        Module ifcM = NoxClient.INSTANCE.modules.get("Interface");
        String style = ifcM instanceof Interface ifc ? ifc.clickStyle.get() : UICustom.clickStyle;
        String lbl = "GUI layout: " + style;
        int lw = Gui.width(font, lbl) + 16;
        guiX = width - lw - 12;
        boolean gh = ModuleCard.inside(mx, my, guiX, 6, lw, 16);
        Gui.roundBorder(g, guiX, 6, lw, 16, 8, gh ? UICustom.accent : UICustom.border,
            0x9910121A);
        Gui.textCenter(g, font, lbl, guiX + lw / 2, 10, gh ? UICustom.text : UICustom.textDim);

        // bottom toolbar
        String[] btns = {"⚙ HUD settings", "↺ Reset layout", "✓ Done"};
        int[] xs = new int[3];
        int total = 0;
        int[] bws = new int[3];
        for (int i = 0; i < 3; i++) { bws[i] = Gui.width(font, btns[i]) + 20; total += bws[i] + 8; }
        int bx = width / 2 - (total - 8) / 2;
        barY = height - 30;
        for (int i = 0; i < 3; i++) {
            xs[i] = bx;
            boolean hov = ModuleCard.inside(mx, my, bx, barY, bws[i], 20);
            Gui.roundBorder(g, bx, barY, bws[i], 20, 10,
                hov ? UICustom.accent : UICustom.border, 0xCC10141C);
            Gui.textCenter(g, font, btns[i], bx + bws[i] / 2, barY + 6,
                i == 2 ? UICustom.accent : UICustom.text);
            bx += bws[i] + 8;
        }
        settingsX = xs[0]; resetX = xs[1]; doneX = xs[2];

        if (popup != null) {
            g.fill(0, 0, width, height, 0x33000000);
            popup.render(g, mx, my);
        }
        super.extractRenderState(g, mx, my, dt);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        double mx = e.x(), my = e.y();
        if (popup != null) {
            int r = popup.mouseClicked(mx, my, e.button());
            if (r == 2) popup = null;
            return true;
        }
        // toolbar
        if (my >= barY && my <= barY + 20) {
            int bw0 = Gui.width(font, "⚙ HUD settings") + 20;
            int bw1 = Gui.width(font, "↺ Reset layout") + 20;
            int bw2 = Gui.width(font, "✓ Done") + 20;
            if (ModuleCard.inside(mx, my, settingsX, barY, bw0, 20)) {
                Module hw = NoxClient.INSTANCE.modules.get("HudWidgets");
                if (hw != null) popup = new SettingsPopup(hw, (int) mx, (int) my, width, height);
                return true;
            }
            if (ModuleCard.inside(mx, my, resetX, barY, bw1, 20)) {
                HudLayout.resetAll(); Toasts.push("HUD layout reset", 0xFFFFD870); return true;
            }
            if (ModuleCard.inside(mx, my, doneX, barY, bw2, 20)) { onClose(); return true; }
        }
        // GUI layout cycle (top-right pill)
        int lw = Gui.width(font, "GUI layout: x") + 40;
        if (ModuleCard.inside(mx, my, guiX, 6, width - guiX - 6, 16)) {
            Module ifcM = NoxClient.INSTANCE.modules.get("Interface");
            if (ifcM instanceof Interface ifc) {
                Setting.Mode s = ifc.clickStyle;
                int i = java.util.Arrays.asList(s.modes).indexOf(s.get());
                s.set(s.modes[(i + 1) % s.modes.length]);
                Toasts.push("ClickGUI → " + s.get(), UICustom.accent);
            }
            return true;
        }
        // element hit — topmost last
        for (int i = ELS.length - 1; i >= 0; i--) {
            El el = ELS[i];
            int[] r = rect(el, width, height);
            if (!ModuleCard.inside(mx, my, r[0], r[1], r[2], r[3])) continue;
            if (e.button() == 2 || e.button() == 3) {   // RMB → reset this element
                HudLayout.reset(el.id);
                Toasts.push(el.label + " reset", UICustom.textDim);
                return true;
            }
            dragging = el.id; nudgeTarget = el.id;
            float[] p = HudLayout.get(el.id);
            // drag offset inside the element — keep grab point stable
            dragDX = (float) (mx - r[0]); dragDY = (float) (my - r[1]);
            if (p == null) HudLayout.set(el.id, r[0] / (float) width, r[1] / (float) height);
            return true;
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragging != null) {
            float nx = (float) (e.x() - dragDX), ny = (float) (e.y() - dragDY);
            int[] r = rect(find(dragging), width, height);
            snapVX = snapHY = -1;
            // center-axis snap (element center → screen center)
            float cx = nx + r[2] / 2f, cy = ny + r[3] / 2f;
            if (Math.abs(cx - width / 2f) < 7) { nx = width / 2f - r[2] / 2f; snapVX = width / 2; }
            if (Math.abs(cy - height / 2f) < 7) { ny = height / 2f - r[3] / 2f; snapHY = height / 2; }
            // edge snap (4px margin feel)
            if (Math.abs(nx) < 6) nx = 0;
            if (Math.abs(nx + r[2] - width) < 6) nx = width - r[2];
            if (Math.abs(ny) < 6) ny = 0;
            if (Math.abs(ny + r[3] - height) < 6) ny = height - r[3];
            // element-to-element snap: my left/center/right vs other edges+centers
            float[] probes = {0, r[2] / 2f, r[2]};
            float[] probesY = {0, r[3] / 2f, r[3]};
            outer:
            for (El other : ELS) {
                if (other.id.equals(dragging)) continue;
                int[] or = rect(other, width, height);
                for (float line : new float[]{or[0], or[0] + or[2] / 2f, or[0] + or[2]})
                    for (float p : probes)
                        if (Math.abs(nx + p - line) < 5) { nx = line - p; snapVX = (int) line; }
                for (float line : new float[]{or[1], or[1] + or[3] / 2f, or[1] + or[3]})
                    for (float p : probesY)
                        if (Math.abs(ny + p - line) < 5) { ny = line - p; snapHY = (int) line; }
                if (snapVX >= 0 || snapHY >= 0) break outer;
            }
            HudLayout.set(dragging, nx / width, ny / height);
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (dragging != null) { dragging = null; snapVX = snapHY = -1; HudLayout.save(); }
        return super.mouseReleased(e);
    }

    private El find(String id) {
        for (El el : ELS) if (el.id.equals(id)) return el;
        return ELS[0];
    }

    @Override public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (popup != null) { popup.mouseScrolled(mx, my, v); return true; }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override public boolean keyPressed(KeyEvent e) {
        if (popup != null && popup.keyPressed(e.key())) return true;
        // arrow-key nudge on the last-touched element (shift = 8px steps)
        if (nudgeTarget != null && (e.key() >= 79 && e.key() <= 82)) {
            float[] p = HudLayout.get(nudgeTarget);
            if (p == null) { int[] r = rect(find(nudgeTarget), width, height);
                p = new float[]{r[0] / (float) width, r[1] / (float) height}; }
            int step = e.hasShiftDown() ? 8 : 1;
            float dx = e.key() == 80 ? -step : e.key() == 79 ? step : 0;   // SDL: 79=RIGHT 80=LEFT
            float dy = e.key() == 82 ? -step : e.key() == 81 ? step : 0;   // SDL: 82=UP    81=DOWN
            HudLayout.set(nudgeTarget, p[0] + dx / width, p[1] + dy / height);
            HudLayout.save();
            return true;
        }
        if (e.key() == 41) { onClose(); return true; }   // ESC (SDL)
        return super.keyPressed(e);
    }

    // 26.3: SDL text input must be claimed by a listener or char events die —
    // the SettingsPopup reuses this screen's SDL session (owner = this screen)
    @Override public void added() { super.added(); minecraft.onTextInputFocusChange(this, true); }
    @Override public void removed() { minecraft.onTextInputFocusChange(this, false); super.removed(); }

    @Override public boolean charTyped(CharacterEvent e) {
        if (popup != null && popup.charTyped((char) e.codepoint())) return true;
        return super.charTyped(e);
    }

    @Override public void onClose() {
        HudLayout.save();
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override public boolean isPauseScreen() { return false; }
}
