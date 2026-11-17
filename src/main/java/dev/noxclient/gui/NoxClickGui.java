package dev.noxclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.noxclient.NoxClient;
import dev.noxclient.gui.comp.Charts;
import dev.noxclient.gui.comp.Inputs;
import dev.noxclient.gui.comp.Overlays;
import dev.noxclient.gui.ds.DS;
import dev.noxclient.gui.nvg.Nvg;
import dev.noxclient.gui.nvg.NvgOverlay;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.AntiCheats;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.system.MemoryStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ClickGUI — full-NanoVG rebuild. Every pixel is vector: real gradients,
 * real arcs, AA text with embedded Inter/JetBrains Mono, box-gradient
 * shadows and glows. No texture stairs anywhere.
 *
 * Layout (spec: mockup-reference):
 *  - window #0F0F12, 14px radius, soft shadow, draggable/resizable
 *  - titlebar: mac dots (red closes) · centered "NOX / Premium • Dark Mode"
 *    · right: yellow "AC: x" badge (real detection via AntiCheats) +
 *      version + red PANIC button
 *  - pill tabs: active = pink gradient #FF6EC7→#FF8AD5
 *  - search + module rows 56px (iOS toggle, accent strip, letter-chip icon)
 *    row click = accordion expand (settings = darker child card, 12px indent)
 *  - right column: LIVE STATS — sparkline + radial gauge + bar chart,
 *    real data every 5s (kills/min, K/D, money/hour)
 *  - footer: pinned modules (MMB pins) · Ctrl+K palette · recording mode
 */
public class NoxClickGui extends Screen {
    public static boolean recordingMode;
    public static NoxClickGui current;

    private int winX = NoxWindowScreen.winX, winY = NoxWindowScreen.winY,
        winW = Math.max(720, NoxWindowScreen.winW),
        winH = Math.max(460, NoxWindowScreen.winH);

    private static final int TITLE = 52, TABS = 38, SEARCH = 36, FOOT = 44,
        ROW = 56, STATS_W = 236, GAP = 24;

    private float openT;
    private final long openAt = System.currentTimeMillis();

    private int dragDX, dragDY;
    private boolean dragging, resizing;
    private Category tab = null;
    private final Overlays.Scrollable scroll = new Overlays.Scrollable(0);
    private final Inputs.Input search = new Inputs.Input(0, 0, 0, "search modules…");

    private Module expanded;
    private final Map<Module, Long> expAt = new HashMap<>();
    private final Map<Module, Long> togAt = new HashMap<>();
    private Setting.Num dragNum;

    /* design page — grouped Interface-module settings, live editable */
    private boolean designTab;
    private final Overlays.Scrollable dScroll = new Overlays.Scrollable(0);
    private record DItem(Object o, int y, int h) {}   // String header | Setting
    private final List<DItem> dRows = new ArrayList<>();
    private List<Object> design;

    public static final java.util.List<String> PINNED = new ArrayList<>();

    private boolean palette;
    private final Inputs.Input palInput = new Inputs.Input(0, 0, 0, "Type a module name…");
    private int palSel;

    /* frame-captured input for the deferred NVG draw */
    private int fmx, fmy;
    private float fdt;

    private record Row(Module m, int y, int h) {}
    private record SettingRow(Setting<?> s, int y) {}
    private final List<Row> rows = new ArrayList<>();
    private final List<SettingRow> settingRows = new ArrayList<>();
    private int scrollOff, listContentH;

    public NoxClickGui() {
        super(Component.literal("NOX"));
        current = this;
        if (winX < 0) { winX = 40; winY = 40; }
    }

    public void setTabIndex(int i) {
        var vals = Category.values();
        tab = i < 0 || i >= vals.length ? null : vals[i];
        scroll.target = 0;
    }

    /* ================= layout ================= */

    private int contentX() { return winX + 16; }
    private int contentW() { return winW - 32; }
    private boolean hasStats() { return winW >= 720 && UICustom.showStats; }
    private int listW() { return hasStats() ? contentW() - STATS_W - GAP : contentW(); }
    private int tabsY() { return winY + TITLE; }
    private int searchY() { return tabsY() + TABS + 4; }
    private int listY() { return searchY() + SEARCH + 8; }
    private int footerH() { return UICustom.showPinned ? FOOT : 14; }
    private int listH() { return winY + winH - footerH() - listY(); }
    private float winR() { return Math.max(4, Math.min(24, UICustom.radius)); }

    private List<Module> filtered() {
        String q = search.value.trim().toLowerCase();
        List<Module> out = new ArrayList<>();
        for (Module m : NoxClient.INSTANCE.modules.all()) {
            if (tab != null && m.category != tab) continue;
            if (!q.isEmpty() && !m.name.toLowerCase().contains(q)
                && !m.description.toLowerCase().contains(q)) continue;
            out.add(m);
        }
        return out;
    }

    /* ================= animations ================= */

    private static float easeOutCubic(float t) { return 1f - (float) Math.pow(1 - t, 3); }

    private float expandFrac(Module m) {
        Long at = expAt.get(m);
        if (at == null) return 0;
        long t0 = Math.abs(at);
        float t = Math.min(1f, (System.currentTimeMillis() - t0) / (200f / UICustom.animSpeed));
        float f = easeOutCubic(t);
        if (at < 0) {
            if (t >= 1f) { expAt.remove(m); return 0; }
            return 1f - f;
        }
        return f;
    }

    private float toggleFrac(Module m) {
        Long at = togAt.get(m);
        boolean on = m.isEnabled();
        if (at == null) return on ? 1 : 0;
        float t = Math.min(1f, (System.currentTimeMillis() - at) / (150f / UICustom.animSpeed));
        if (t >= 1f) { togAt.remove(m); return on ? 1 : 0; }
        float f = easeOutCubic(t);
        return on ? f : 1f - f;
    }

    private void toggleModule(Module m) {
        togAt.put(m, System.currentTimeMillis());
        m.setEnabled(!m.isEnabled());
    }

    private void setExpanded(Module m) {
        long now = System.currentTimeMillis();
        if (expanded != null && expanded != m) expAt.put(expanded, -now);
        if (expanded == m) { expAt.put(m, -now); expanded = null; }
        else { expanded = m; expAt.put(m, now); }
    }

    private int settingsFullH(Module m) {
        int h = 10;
        for (Setting<?> s : m.settings)
            if (!s.name.equals("Activation") && !s.name.equals("Keybind")) h += settingH(s) + 6;
        return h;
    }
    private int settingH(Setting<?> s) { return s instanceof Setting.Num ? 30 : 24; }

    /* ================= render ================= */

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        Charts.tickSample();
        fmx = mx; fmy = my; fdt = dt;
        // clamp the window fully on-screen (saved pos may come from a bigger display)
        winW = Math.min(winW, width - 20); winH = Math.min(winH, height - 20);
        winX = Math.max(8, Math.min(winX, width - winW - 8));
        winY = Math.max(8, Math.min(winY, height - winH - 8));
        float t = Math.min(1f, (System.currentTimeMillis() - openAt) / (200f / UICustom.animSpeed));
        openT = easeOutCubic(t);

        // layout pass (scroll tick, row positions) — draw params the NVG op reads
        scroll.viewH = listH();
        scrollOff = scroll.tick(dt);
        rows.clear(); settingRows.clear(); dRows.clear();
        if (designTab) {
            dScroll.viewH = listH();
            int dOff = dScroll.tick(dt);
            int dy = listY() - dOff, dch = 0;
            for (Object o : designItems()) {
                int h = o instanceof String ? 24 : settingH((Setting<?>) o) + 6;
                if (dy + h >= listY() && dy <= listY() + listH())
                    dRows.add(new DItem(o, dy, h));
                dy += h; dch += h;
            }
            dScroll.contentH = dch;
        } else {
            int y = listY() - scrollOff, ch = 0;
            for (Module m : filtered()) {
                int sh = (m == expanded || expAt.containsKey(m))
                    ? (int) (expandFrac(m) * (settingsFullH(m) + 8)) : 0;
                int rh = ROW + sh;
                if (y + rh >= listY() && y <= listY() + listH())
                    rows.add(new Row(m, y, rh));
                y += rh + 4; ch += rh + 4;
            }
            scroll.contentH = listContentH = ch;
        }

        // backdrop dim — deferred batch stays UNDER the NVG window
        if (!recordingMode) Gui.backdrop(g, width, height, openT);

        NvgOverlay.op(this::drawAll);
        if (palette) NvgOverlay.op(this::drawPalette);
        else super.extractRenderState(g, mx, my, dt);
    }

    /* ================= NVG draw (runs in the post-pass) ================= */

    private void drawAll(long c, MemoryStack st) {
        int mx = fmx, my = fmy;
        Nvg.fontScale = UICustom.fontScale;
        float wr = winR();
        float cx = winX + winW / 2f, cy = winY + winH / 2f;
        float sc = 0.96f + 0.04f * openT;
        org.lwjgl.nanovg.NanoVG.nvgTranslate(c, cx, cy);
        org.lwjgl.nanovg.NanoVG.nvgScale(c, sc, sc);
        org.lwjgl.nanovg.NanoVG.nvgTranslate(c, -cx, -cy);

        // window shell — shadow + body + hairline border + top sheen
        if (UICustom.glow > 0.05f)
            Nvg.shadow(c, st, winX, winY + 2, winW, winH, wr, 26 * UICustom.glow,
                Nvg.fade(0x66000000, Math.min(1f, UICustom.glow)));
        Nvg.round(c, st, winX, winY, winW, winH, wr, Nvg.fade(DS.bg(), UICustom.winOpacity * openT));
        Nvg.roundB(c, st, winX, winY, winW, winH, wr, 0x00FFFFFF, DS.line());
        Nvg.vgrad(c, st, winX, winY, winW, 44, wr, 0x10FFFFFF, 0x00FFFFFF);

        titlebar(c, st);
        tabs(c, st, mx, my);
        searchBar(c, st, mx, my);
        if (designTab) designPage(c, st);
        else moduleList(c, st, mx, my);
        if (hasStats()) statsPanel(c, st);
        if (UICustom.showPinned) pinnedBar(c, st, mx, my);

        org.lwjgl.nanovg.NanoVG.nvgResetTransform(c);
    }

    /* ---------- design page — every GUI setting, grouped + live ---------- */
    private List<Object> designItems() {
        if (design != null) return design;
        design = new ArrayList<>();
        Module im = NoxClient.INSTANCE.modules.get("Interface");
        if (im == null) return design;
        Map<String, Setting<?>> byName = new HashMap<>();
        for (Setting<?> s : im.settings) byName.put(s.name, s);
        String[][] groups = {
            {"Style", "Theme", "ClickGUI", "Card style", "Window style", "Density"},
            {"Colors", "Window accent", "Accent gradient", "Card accent", "GUI background",
                "Window", "Card top", "Card bottom", "Header", "Backdrop", "Text"},
            {"Window", "Corner radius", "Window opacity", "Glow intensity", "Borders",
                "Live stats", "Pinned bar"},
            {"Motion & Text", "Animation speed", "Font scale", "HUD scale",
                "Large text", "Reduced motion", "Color blind"},
            {"Atmosphere", "Season", "Seasonal FX", "Screen backdrop", "Backdrop level"},
        };
        for (String[] g : groups) {
            design.add(g[0]);
            for (int i = 1; i < g.length; i++) {
                Setting<?> s = byName.remove(g[i]);
                if (s != null) design.add(s);
            }
        }
        if (!byName.isEmpty()) { design.add("More"); design.addAll(byName.values()); }
        return design;
    }

    private void designPage(long c, MemoryStack st) {
        int lw = listW();
        Nvg.scissor(c, contentX() - 4, listY() - 2, lw + 8, listH() + 4);
        for (DItem it : dRows) {
            if (it.o instanceof String hdr) {
                Nvg.text(c, st, "inter_b", 9.5f, contentX() + 4, it.y + it.h / 2f,
                    hdr.toUpperCase(java.util.Locale.US), DS.muted(),
                    org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
            } else {
                Setting<?> s = (Setting<?>) it.o;
                Nvg.round(c, st, contentX(), it.y, lw, it.h - 4, 10,
                    Nvg.fade(DS.surfaceAlt(), 0.5f));
                drawSetting(c, st, s, contentX() + 12, it.y + 2, lw - 24);
            }
        }
        Nvg.unScissor(c);
        dScroll.renderBarNvg(c, st, contentX() + lw - 2, listY());
    }

    /* ---------- titlebar ---------- */
    private void titlebar(long c, MemoryStack st) {
        // mac dots — red closes
        Nvg.circle(c, st, winX + 20, winY + 16, 5.5f, 0xFFFF5F57);
        Nvg.circle(c, st, winX + 38, winY + 16, 5.5f, 0xFFFEBC2E);
        Nvg.circle(c, st, winX + 56, winY + 16, 5.5f, 0xFF28C840);

        // centered brand
        Nvg.text(c, st, "inter_b", 16, winX + winW / 2f, winY + 9, "NOX",
            DS.text(), org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_TOP);
        Nvg.text(c, st, "inter", 10, winX + winW / 2f, winY + 27, "Premium • Dark Mode",
            DS.muted(), org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_TOP);

        // right: [AC: x]  version  [PANIC]
        float rx = winX + winW - 14;
        float pw = Nvg.textW(c, "inter_b", 10.5f, "PANIC") + 18;
        Nvg.round(c, st, rx - pw, winY + 10, pw, 20, 10, DS.danger());
        Nvg.text(c, st, "inter_b", 10.5f, rx - pw / 2, winY + 15, "PANIC", 0xFFFFFFFF,
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        rx -= pw + 10;
        String ver = "v" + dev.noxclient.update.UpdateManager.currentVersion()
            + (dev.noxclient.premium.LicenseManager.premium() ? " Pro" : "");
        float vw = Nvg.textW(c, "inter", 10, ver);
        rx -= vw;
        Nvg.text(c, st, "inter", 10, rx, winY + 15, ver, DS.muted(),
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        rx -= 10;
        String ac = "AC: " + AntiCheats.display();
        float aw = Nvg.textW(c, "inter_b", 10.5f, ac) + 14;
        Nvg.round(c, st, rx - aw, winY + 10, aw, 20, 10, 0xFFFBBF24);
        Nvg.text(c, st, "inter_b", 10.5f, rx - aw / 2, winY + 15, ac, 0xFF1A1A1F,
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);

        Nvg.rect(c, st, winX + 1, winY + TITLE - 1, winW - 2, 1, DS.line());
    }

    /* ---------- tabs ---------- */
    /** Pill hitboxes captured during the draw pass — [x, w, catIndex(-1=All,-2=Design)].
     *  Drawn with Nvg.textW, so clicks must use THESE rects, not font.width. */
    private final List<float[]> tabRects = new ArrayList<>();

    private void tabs(long c, MemoryStack st, int mx, int my) {
        int x = contentX();
        tabRects.clear();
        String[] names = new String[Category.values().length + 1];
        names[0] = "All"; int i = 1;
        for (Category cat : Category.values()) names[i++] = cat.label;
        for (int k = 0; k < names.length; k++) {
            int w = (int) (Nvg.textW(c, "inter_sb", 12, names[k]) + 32);
            boolean act = !designTab
                && ((k == 0 && tab == null) || (k > 0 && tab == Category.values()[k - 1]));
            boolean hov = mx >= x && mx < x + w && my >= tabsY() && my < tabsY() + TABS - 10;
            if (act)
                Nvg.hgrad(c, st, x, tabsY(), w, TABS - 10, (TABS - 10) / 2f, DS.accent(), DS.accent2());
            else
                Nvg.round(c, st, x, tabsY(), w, TABS - 10, (TABS - 10) / 2f,
                    hov ? DS.lighten(DS.surfaceAlt(), .12f) : DS.surfaceAlt());
            Nvg.text(c, st, "inter_sb", 12, x + w / 2f, tabsY() + (TABS - 10) / 2f, names[k],
                act ? 0xFFFFFFFF : DS.secondary(),
                org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
            tabRects.add(new float[]{x, w, k - 1});
            x += w + 6;
        }
        // Design pill — gear + label, opens the grouped GUI-settings page
        float dw = designPillW(c);
        tabRects.add(new float[]{x, dw, -2});
        boolean dhov = mx >= x && mx < x + dw && my >= tabsY() && my < tabsY() + TABS - 10;
        if (designTab)
            Nvg.hgrad(c, st, x, tabsY(), dw, TABS - 10, (TABS - 10) / 2f, DS.accent(), DS.accent2());
        else
            Nvg.round(c, st, x, tabsY(), dw, TABS - 10, (TABS - 10) / 2f,
                dhov ? DS.lighten(DS.surfaceAlt(), .12f) : DS.surfaceAlt());
        gear(c, st, x + 16, tabsY() + (TABS - 10) / 2f, 4.5f,
            designTab ? 0xFFFFFFFF : DS.secondary());
        Nvg.text(c, st, "inter_sb", 12, x + 26, tabsY() + (TABS - 10) / 2f, "Design",
            designTab ? 0xFFFFFFFF : DS.secondary(),
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
    }

    private float designPillW(long c) {
        return Nvg.textW(c, "inter_sb", 12, "Design") + 42;
    }

    /** Vector cog — ring + 6 spokes. */
    private void gear(long c, MemoryStack st, float cx, float cy, float r, int argb) {
        Nvg.ring(c, st, cx, cy, r * 0.55f, 1.4f, argb);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            Nvg.line(c, st,
                cx + (float) Math.cos(a) * r * 0.75f, cy + (float) Math.sin(a) * r * 0.75f,
                cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, 1.4f, argb);
        }
    }

    /* ---------- search ---------- */
    private void searchBar(long c, MemoryStack st, int mx, int my) {
        search.x = contentX(); search.y = searchY(); search.w = contentW(); search.h = SEARCH;
        boolean hov = mx >= search.x && mx < search.x + search.w && my >= search.y && my < search.y + search.h;
        Nvg.round(c, st, search.x, search.y, search.w, search.h, 10, DS.surfaceAlt());
        int border = search.focused ? DS.accent() : hov ? DS.lighten(DS.border(), .3f) : DS.line();
        Nvg.roundB(c, st, search.x, search.y, search.w, search.h, 10, 0x00FFFFFF, border);
        if (search.focused && UICustom.glow > 0.05f)   // soft focus glow
            Nvg.shadow(c, st, search.x, search.y, search.w, search.h, 10, 8 * UICustom.glow,
                Nvg.fade(DS.accent(), 0.2f * Math.min(1f, UICustom.glow)));
        // magnifier
        float ix = search.x + 16, iy = search.y + search.h / 2f;
        Nvg.ring(c, st, ix, iy - 1, 4.5f, 1.6f, search.focused ? DS.accent() : DS.muted());
        Nvg.line(c, st, ix + 3.4f, iy + 2.6f, ix + 7, iy + 6.4f, 1.6f,
            search.focused ? DS.accent() : DS.muted());
        String shown = search.value.isEmpty() && !search.focused ? "search modules…" : search.value;
        Nvg.text(c, st, "inter", 12, search.x + 32, search.y + search.h / 2f, shown,
            search.value.isEmpty() && !search.focused ? DS.muted() : DS.text(),
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        if (!search.value.isEmpty()) {
            float cxx = search.x + search.w - 20;
            Nvg.circle(c, st, cxx, search.y + search.h / 2f, 8, Nvg.fade(DS.muted(), 0.35f));
            Nvg.line(c, st, cxx - 3, search.y + search.h / 2f - 3, cxx + 3, search.y + search.h / 2f + 3, 1.5f, DS.text());
            Nvg.line(c, st, cxx + 3, search.y + search.h / 2f - 3, cxx - 3, search.y + search.h / 2f + 3, 1.5f, DS.text());
        }
        if (search.focused && System.currentTimeMillis() % 1000 < 550) {
            float caretX = search.x + 32 + Nvg.textW(c, "inter", 12, search.value);
            Nvg.rect(c, st, caretX, search.y + 10, 1.4f, search.h - 20, DS.accent());
        }
    }

    /* ---------- module list ---------- */
    private void moduleList(long c, MemoryStack st, int mx, int my) {
        int lw = listW();
        Nvg.scissor(c, contentX() - 4, listY() - 2, lw + 8, listH() + 4);
        for (Row r : rows) drawRow(c, st, r, contentX(), r.y, lw, mx, my);
        Nvg.unScissor(c);
        scroll.renderBarNvg(c, st, contentX() + lw - 2, listY());
    }

    private void drawRow(long c, MemoryStack st, Row row, int x, int y, int w, int mx, int my) {
        Module m = row.m;
        boolean on = m.isEnabled();
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + ROW;
        Nvg.round(c, st, x, y, w, ROW, 10,
            on ? Nvg.fade(DS.accent(), 0.10f) : hov ? DS.surfaceAlt() : Nvg.fade(DS.surfaceAlt(), 0.5f));
        if (on)
            Nvg.round(c, st, x + 1, y + 10, 4, ROW - 20, 2, DS.accent());

        // letter-chip icon (vector — no raster icons behind NVG)
        float chip = 30, chx = x + 12, chy = y + ROW / 2f - chip / 2;
        Nvg.round(c, st, chx, chy, chip, chip, 8, on ? Nvg.fade(DS.accent(), 0.18f) : DS.surface());
        Nvg.text(c, st, "inter_b", 13, chx + chip / 2, chy + chip / 2 - 0.5f,
            String.valueOf(Character.toUpperCase(m.name.charAt(0))),
            on ? DS.accent() : DS.muted(),
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);

        // name + description vertically centered as a block around row middle
        Nvg.text(c, st, "inter_sb", 13.5f, x + 52, y + ROW / 2f - 9.5f, m.name,
            on ? DS.text() : DS.secondary(),
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        Nvg.text(c, st, "inter", 10.5f, x + 52, y + ROW / 2f + 9.5f,
            truncN(c, m.description, w - 150), DS.muted(),
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        if (m.premium) {
            float sx = x + 56 + Nvg.textW(c, "inter_sb", 13.5f, m.name);
            star(c, st, sx, y + ROW / 2f - 9.5f, 5, DS.warning());
        }

        // iOS toggle 36×20 — animated knob
        int tx = x + w - 48, ty = y + ROW / 2 - 10;
        Nvg.toggle(c, st, tx, ty, 36, 20, toggleFrac(m), DS.surface(), DS.accent());

        if (hov && !m.settings.isEmpty()) {
            float cxx = x + w - 14, cyy = y + ROW / 2f;
            Nvg.line(c, st, cxx - 4, cyy - 2, cxx, cyy + 3, 1.6f, DS.muted());
            Nvg.line(c, st, cxx, cyy + 3, cxx + 4, cyy - 2, 1.6f, DS.muted());
        }

        // settings child — 12px indent, darker card, easeOutCubic height
        float ef = expandFrac(m);
        if (ef > 0) {
            int full = settingsFullH(m);
            int sh = (int) (ef * full);
            int ey = y + ROW + 4;
            Nvg.scissor(c, x, ey - 2, w, sh + 4);
            Nvg.round(c, st, x + 12, ey, w - 12, full, 10, DS.surface());
            int sy = ey + 8;
            for (Setting<?> s : m.settings) {
                if (s.name.equals("Activation") || s.name.equals("Keybind")) continue;
                drawSetting(c, st, s, x + 24, sy, w - 48);
                settingRows.add(new SettingRow(s, sy));
                sy += settingH(s) + 6;
            }
            Nvg.unScissor(c);
        }
    }

    private void drawSetting(long c, MemoryStack st, Setting<?> s, int x, int y, int w) {
        float mid = y + settingH(s) / 2f;
        if (s instanceof Setting.Num n) {
            // label top-left, slider + value bottom — no overlap, value centered
            Nvg.text(c, st, "inter", 11.5f, x, y + 4, s.name, DS.secondary());
            double fr = (n.get() - n.min) / (n.max - n.min);
            int tw = w - 64; float ty = y + 20;
            Nvg.round(c, st, x, ty, tw, 4, 2, DS.surfaceAlt());
            Nvg.round(c, st, x, ty, tw * (float) fr, 4, 2, DS.accent());
            Nvg.circle(c, st, x + tw * (float) fr, ty + 2, 6, 0xFFFFFFFF);
            String vs = n.get() == n.get().longValue() ? "" + n.get().longValue()
                : String.format(java.util.Locale.US, "%.2f", n.get());
            Nvg.text(c, st, "mono", 11, x + w, y + 10, vs, DS.accent(),
                org.lwjgl.nanovg.NanoVG.NVG_ALIGN_RIGHT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
            return;
        }
        Nvg.text(c, st, "inter", 11.5f, x, mid, s.name, DS.secondary(),
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        if (s instanceof Setting.Bool b) {
            Nvg.check(c, st, x + Nvg.textW(c, "inter", 11.5f, s.name) + 10, mid - 8, 16,
                b.on(), DS.accent(), DS.surfaceAlt(), DS.border());
        } else if (s instanceof Setting.Mode mo) {
            float mx2 = x + w - 4;
            for (int i = mo.modes.length - 1; i >= 0; i--) {
                float pw = Nvg.textW(c, "inter", 10.5f, mo.modes[i]) + 14;
                mx2 -= pw;
                boolean act = mo.get().equals(mo.modes[i]);
                Nvg.round(c, st, mx2, mid - 9, pw, 18, 9, act ? DS.accent() : DS.surfaceAlt());
                Nvg.text(c, st, "inter", 10.5f, mx2 + pw / 2, mid, mo.modes[i],
                    act ? 0xFFFFFFFF : DS.secondary(),
                    org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
                mx2 -= 4;
            }
        } else if (s instanceof Setting.Col cc) {
            Nvg.roundB(c, st, x + w - 48, mid - 8, 42, 16, 5, cc.get(), DS.border());
        } else if (s instanceof Setting.Bind b) {
            String txt = b.get() == -2 ? "press…" : b.get() < 0 ? "none" : "key " + b.get();
            float pw = Nvg.textW(c, "inter", 10.5f, txt) + 14;
            Nvg.round(c, st, x + w - pw, mid - 9, pw, 18, 9, b.get() == -2 ? DS.accent() : DS.surfaceAlt());
            Nvg.text(c, st, "inter", 10.5f, x + w - pw / 2, mid, txt,
                b.get() == -2 ? 0xFFFFFFFF : DS.secondary(),
                org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        } else if (s instanceof Setting.Str sstr) {
            String v = sstr.get().isEmpty() ? "…" : sstr.get();
            float tw2 = Math.min(140, Nvg.textW(c, "inter", 10.5f, v) + 14);
            Nvg.round(c, st, x + w - tw2, mid - 9, tw2, 18, 6, DS.surfaceAlt());
            Nvg.text(c, st, "inter", 10.5f, x + w - tw2 + 7, mid, truncN(c, v, tw2 - 12),
                DS.secondary(),
                org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        }
    }

    /* ---------- live stats ---------- */
    private void statsPanel(long c, MemoryStack st) {
        int sx = contentX() + listW() + GAP, sw = STATS_W;
        int sy = listY(), sh = listH();
        Nvg.roundB(c, st, sx, sy, sw, sh, 10, Nvg.fade(DS.surfaceAlt(), 0.5f), DS.line());
        float px = sx + 14, py = sy + 12;
        Nvg.text(c, st, "inter_sb", 10, px, py, "LIVE STATS", DS.secondary());
        py += 18;

        // Kills / Min — sparkline + grid + mono axis labels
        Nvg.text(c, st, "inter", 10.5f, px, py, "Kills / Min:", DS.secondary()); py += 12;
        sparklineN(c, st, px, py, sw - 28, 56, Charts.kpmSeries());
        py += 68;

        // K/D — radial gauge (270° arc), fill = kd / 4.0
        Nvg.text(c, st, "inter", 10.5f, px, py, "K/D Ratio:", DS.secondary()); py += 16;
        double kd = Charts.kd();
        float pct = (float) Math.min(1, kd / 4.0);
        float gcx = sx + sw / 2f, gcy = py + 38, gr = 34;
        Nvg.arc(c, st, gcx, gcy, gr, 7, 135, 405, DS.surface());        // track
        if (pct > 0.002f)
            Nvg.arc(c, st, gcx, gcy, gr, 7, 135, 135 + pct * 270, 0xFF4ADE80);
        String kv = String.format(java.util.Locale.US, "%.2f", kd);
        Nvg.text(c, st, "mono", 15, gcx, gcy - 4, kv, DS.text(),
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        String pc = (int) (pct * 100) + "%";
        Nvg.text(c, st, "mono", 10, gcx, gcy + 14, pc, 0xFF4ADE80,
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        py += 92;

        // Money / Hour — bars + baseline grid + mono labels
        Nvg.text(c, st, "inter", 10.5f, px, py, "Money / Hour:", DS.secondary()); py += 12;
        float[] mph = Charts.mphSeries();
        barsN(c, st, px, py, sw - 28, 48, mph);
        py += 54;
        int n = mph.length;
        for (int i = n - 3; i < n; i++) {
            if (i < 0) continue;
            String v = Charts.kfmt(mph[i]);
            float lx = px + (sw - 28f) * (i + 0.5f) / n;
            Nvg.text(c, st, "mono", 9.5f, lx, py + 2, v, DS.muted(),
                org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_TOP);
        }
    }

    /** Vector sparkline: area fade + 1.6px stroke + live tip dot + y labels. */
    private void sparklineN(long c, MemoryStack st, float x, float y, float w, float h, float[] v) {
        float max = 1;
        for (float a : v) if (a > max) max = a;
        for (int i = 0; i <= 2; i++) {
            float gy = y + h - 1 - i * (h - 1) / 2;
            Nvg.rect(c, st, x, gy, w - 24, 1, Nvg.fade(DS.border(), 0.5f));
            String lbl = i == 0 ? "0" : String.valueOf((int) (max * i / 2));
            Nvg.text(c, st, "mono", 9, x + w - 22, gy + 0.5f, lbl, DS.muted(),
                org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        }
        if (v.length < 2) return;
        float[] pts = new float[v.length * 2];
        float sx = (w - 26f) / (v.length - 1);
        for (int i = 0; i < v.length; i++) {
            pts[i * 2] = x + i * sx;
            pts[i * 2 + 1] = y + h - 2 - v[i] / max * (h - 8);
        }
        Nvg.area(c, st, pts, y + h - 1, y, DS.accent() & 0xFFFFFF, 0.22f);
        Nvg.poly(c, st, pts, 1.6f, DS.accent());
        Nvg.circle(c, st, pts[pts.length - 2], pts[pts.length - 1], 2.4f, DS.accent());
    }

    private void barsN(long c, MemoryStack st, float x, float y, float w, float h, float[] v) {
        float max = 1;
        for (float a : v) if (a > max) max = a;
        for (int i = 0; i <= 2; i++) {
            float gy = y + h - 1 - i * (h - 12) / 2;
            Nvg.rect(c, st, x, gy, w, 1, Nvg.fade(DS.border(), i == 0 ? 1f : 0.5f));
        }
        int n = v.length;
        float bw = Math.max(4, (w - (n - 1) * 4) / n);
        for (int i = 0; i < n; i++) {
            float bh = Math.max(2, v[i] / max * (h - 14));
            Nvg.round(c, st, x + i * (bw + 4), y + h - 1 - bh, bw, bh, 2,
                i == n - 1 ? DS.accent() : Nvg.fade(DS.accent(), 0.45f));
        }
    }

    /* ---------- pinned bar ---------- */
    private void pinnedBar(long c, MemoryStack st, int mx, int my) {
        int fy = winY + winH - FOOT;
        Nvg.rect(c, st, winX + 1, fy, winW - 2, 1, DS.line());
        float x = contentX(), py = fy + 9;
        for (int i = 0; i < 5; i++) {
            Module m = i < PINNED.size() ? NoxClient.INSTANCE.modules.get(PINNED.get(i)) : null;
            float pw = m != null ? Nvg.textW(c, "inter_sb", 11, m.name) + 34 : 30;
            boolean hov = mx >= x && mx < x + pw && my >= py && my < py + 26;
            boolean on = m != null && m.isEnabled();
            Nvg.roundB(c, st, x, py, pw, 26, 13,
                hov ? DS.surfaceAlt() : DS.surface(),
                on ? Nvg.fade(DS.accent(), 0.8f) : DS.border());
            if (m != null) {
                Nvg.text(c, st, "inter_sb", 11, x + pw / 2, py + 13, m.name,
                    on ? DS.accent() : DS.secondary(),
                    org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
            } else {
                float cxx = x + pw / 2, cyy = py + 13;
                Nvg.line(c, st, cxx - 4, cyy, cxx + 4, cyy, 1.6f, DS.muted());
                Nvg.line(c, st, cxx, cyy - 4, cxx, cyy + 4, 1.6f, DS.muted());
            }
            x += pw + 8;
        }
    }

    /* ---------- palette ---------- */
    private void drawPalette(long c, MemoryStack st) {
        Nvg.rect(c, st, 0, 0, width, height, 0x88000000);
        int pw = 340, px = (width - pw) / 2, py = height / 5;
        Nvg.shadow(c, st, px, py, pw, 300, 12, 20, 0x66000000);
        Nvg.roundB(c, st, px, py, pw, 46, 12, DS.surfaceAlt(), DS.accent());
        Nvg.ring(c, st, px + 16, py + 22, 4.5f, 1.6f, DS.accent());
        Nvg.line(c, st, px + 19.4f, py + 25.6f, px + 23, py + 29.4f, 1.6f, DS.accent());
        Nvg.text(c, st, "inter", 12, px + 30, py + 23,
            palInput.value.isEmpty() ? "Type a module name…" : palInput.value,
            palInput.value.isEmpty() ? DS.muted() : DS.text(),
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        if (System.currentTimeMillis() % 1000 < 550)
            Nvg.rect(c, st, px + 30 + Nvg.textW(c, "inter", 12, palInput.value), py + 12, 1.4f, 22, DS.accent());

        List<Module> hits = palHits();
        float ly = py + 54;
        for (int i = 0; i < Math.min(8, hits.size()); i++) {
            Module m = hits.get(i);
            boolean sel = i == palSel;
            Nvg.round(c, st, px, ly, pw, 30, 8,
                sel ? Nvg.fade(DS.accent(), 0.35f) : Nvg.fade(DS.surface(), 0.9f));
            Nvg.text(c, st, "inter_sb", 11, px + 12, ly + 15, m.name,
                sel ? 0xFFFFFFFF : DS.text(),
                org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
            Nvg.text(c, st, "inter", 10, px + pw - 12, ly + 15, m.category.label, DS.muted(),
                org.lwjgl.nanovg.NanoVG.NVG_ALIGN_RIGHT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
            ly += 32;
        }
    }

    private List<Module> palHits() {
        String q = palInput.value.trim().toLowerCase();
        List<Module> out = new ArrayList<>();
        for (Module m : NoxClient.INSTANCE.modules.all())
            if (q.isEmpty() || m.name.toLowerCase().contains(q)) out.add(m);
        return out;
    }

    /* ---------- vector helpers ---------- */
    private void star(long c, MemoryStack st, float cx, float cy, float r, int argb) {
        for (int i = 0; i < 5; i++) {
            double a = i * Math.PI * 2 / 5 - Math.PI / 2;
            Nvg.line(c, st, cx, cy, cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, 1.4f, argb);
        }
    }

    private String truncN(long c, String s, float w) {
        if (Nvg.textW(c, "inter", 10.5f, s) <= w) return s;
        while (s.length() > 1 && Nvg.textW(c, "inter", 10.5f, s + "…") > w)
            s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    /* ================= input ================= */

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        double mx = e.x(), my = e.y(); int btn = e.button();
        if (palette) {
            List<Module> hits = palHits();
            int pw = 340, px = (width - pw) / 2, py = height / 5 + 54;
            for (int i = 0; i < Math.min(8, hits.size()); i++)
                if (mx >= px && mx < px + pw && my >= py + i * 32 && my < py + i * 32 + 30) {
                    toggleModule(hits.get(i));
                    palette = false; return true;
                }
            palette = false; return true;
        }
        if (Math.hypot(mx - (winX + 20), my - (winY + 16)) <= 8) { onClose(); return true; }
        int prx = winX + winW - 14 - 60;
        if (mx >= prx - 8 && mx < winX + winW - 8 && my >= winY + 8 && my < winY + 32) {
            Module p = NoxClient.INSTANCE.modules.get("Panic");
            if (p != null) p.setEnabled(!p.isEnabled());
            return true;
        }
        if (my >= winY && my < winY + TITLE && mx >= winX && mx < winX + winW) {
            dragging = true; dragDX = (int) mx - winX; dragDY = (int) my - winY; return true;
        }
        if (mx >= winX + winW - 14 && my >= winY + winH - 14) { resizing = true; return true; }
        // tab pills — hitboxes recorded by the draw pass (Nvg.textW-accurate)
        for (float[] r : tabRects) {
            if (mx >= r[0] && mx < r[0] + r[1] && my >= tabsY() && my < tabsY() + TABS - 10) {
                if (r[2] == -2f) { designTab = true; dScroll.target = 0; return true; }
                int k = (int) r[2] + 1;
                tab = k == 0 ? null : Category.values()[k - 1];
                designTab = false;
                NoxWindowScreen.activeTab = k; scroll.target = 0; return true;
            }
        }
        if (mx >= search.x + search.w - 30 && mx < search.x + search.w && my >= search.y
            && my < search.y + search.h && !search.value.isEmpty()) {
            search.value = ""; return true;
        }
        search.focused = mx >= search.x && mx < search.x + search.w && my >= search.y
            && my < search.y + search.h;
        if (search.focused) return true;
        if (designTab) {
            if (mx >= contentX() && mx < contentX() + listW()
                && my >= listY() && my < listY() + listH()) {
                designHit(mx, my); return true;
            }
            return true;
        }
        for (Row r : rows)
            if (r.m == expanded || expAt.containsKey(r.m))
                if (mx >= contentX() + 12 && mx < contentX() + listW() && settingHit(r.m, mx, my))
                    return true;
        for (Row r : rows) {
            if (mx >= contentX() && mx < contentX() + listW() && my >= r.y && my < r.y + ROW) {
                if (btn == 2) {
                    if (!PINNED.contains(r.m.name) && PINNED.size() < 5) PINNED.add(r.m.name);
                    else PINNED.remove(r.m.name); return true;
                }
                int tx = contentX() + listW() - 48;
                if (mx >= tx - 4 && mx <= tx + 40 && my >= r.y + ROW / 2 - 12
                    && my <= r.y + ROW / 2 + 12 && btn == 0) {
                    toggleModule(r.m); return true;
                }
                if (btn == 0 || btn == 1) {
                    if (!r.m.settings.isEmpty()) setExpanded(r.m);
                    return true;
                }
            }
        }
        int fy = winY + winH - FOOT + 9, qx = contentX();
        for (int k = 0; k < 5; k++) {
            Module m = k < PINNED.size() ? NoxClient.INSTANCE.modules.get(PINNED.get(k)) : null;
            int pw = m != null ? minecraft.font.width(m.name) + 34 : 30;
            if (mx >= qx && mx < qx + pw && my >= fy && my < fy + 26) {
                if (m != null) toggleModule(m);
                return true;
            }
            qx += pw + 8;
        }
        return super.mouseClicked(e, dbl);
    }

    private boolean settingHit(Module m, double mx, double my) {
        int x = contentX() + 24, w = listW() - 48;
        for (SettingRow sr : settingRows) {
            int sh = settingH(sr.s);
            if (my < sr.y || my >= sr.y + sh) continue;
            return hitOne(sr.s, x, w, mx, my, sr.y, sh);
        }
        return false;
    }

    private boolean designHit(double mx, double my) {
        int x = contentX() + 12, w = listW() - 24;
        for (DItem it : dRows) {
            if (!(it.o instanceof Setting<?> s)) continue;
            int sh = it.h - 4, ry = it.y + 2;
            if (my < ry || my >= ry + sh) continue;
            return hitOne(s, x, w, mx, my, ry, sh);
        }
        return false;
    }

    /** Widget click dispatch shared by accordion settings + design page. */
    private boolean hitOne(Setting<?> s, int x, int w, double mx, double my, int ry, int sh) {
        if (s instanceof Setting.Bool b) { b.set(!b.on()); return true; }
        if (s instanceof Setting.Num n) {
            dragNum = n; dragNumTo(n, x, w, mx); return true;
        }
        if (s instanceof Setting.Mode mo) {
            float mid = ry + sh / 2f;
            int mx2 = x + w - 4;
            for (int k = mo.modes.length - 1; k >= 0; k--) {
                int pw = minecraft.font.width(mo.modes[k]) + 14;
                mx2 -= pw;
                if (mx >= mx2 && mx < mx2 + pw && my >= mid - 9 && my < mid + 9) {
                    mo.set(mo.modes[k]); return true;
                }
                mx2 -= 4;
            }
            return true;
        }
        if (s instanceof Setting.Col cc) {
            int[] pal = {0xFFFF6EC7, 0xFF7A2BE2, 0xFF5AC8FF, 0xFF4ADE80, 0xFFFBBF24, 0xFFF87171,
                0xFF0F0F12, 0xFF1A1A1F, 0xFF232329, 0xFFE8E8EC};
            cc.set(pal[(indexOf(pal, cc.get()) + 1) % pal.length]); return true;
        }
        if (s instanceof Setting.Bind b) { b.set(-2); return true; }
        return true;
    }

    private void dragNumTo(Setting.Num n, int x, int w, double mx) {
        double fr = (mx - x) / (w - 64);
        n.set(n.min + Math.max(0, Math.min(1, fr)) * (n.max - n.min));
    }

    private int indexOf(int[] a, int v) { for (int i = 0; i < a.length; i++) if (a[i] == v) return i; return a.length - 1; }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        dragging = resizing = false;
        dragNum = null;
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragging) {
            winX = (int) e.x() - dragDX; winY = (int) e.y() - dragDY; return true;
        }
        if (resizing) {
            winW = Math.max(700, (int) e.x() - winX + 8);
            winH = Math.max(440, (int) e.y() - winY + 8); return true;
        }
        if (dragNum != null) {
            dragNumTo(dragNum, designTab ? contentX() + 12 : contentX() + 24,
                designTab ? listW() - 24 : listW() - 48, e.x()); return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (mx >= contentX() - 4 && mx < contentX() + listW() + 4 && my >= listY()
            && my < listY() + listH()) {
            if (designTab) dScroll.onScroll(v * 1.6);
            else scroll.onScroll(v * 1.6);
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int key = e.key();
        if (palette) {
            if (key == InputConstants.KEY_ESCAPE) { palette = false; return true; }
            if (key == InputConstants.KEY_BACKSPACE && !palInput.value.isEmpty())
                palInput.value = palInput.value.substring(0, palInput.value.length() - 1);
            if (key == InputConstants.KEY_DOWN) palSel = Math.min(palHits().size() - 1, palSel + 1);
            if (key == InputConstants.KEY_UP) palSel = Math.max(0, palSel - 1);
            if (key == InputConstants.KEY_RETURN) {
                List<Module> hits = palHits();
                if (!hits.isEmpty()) toggleModule(hits.get(Math.min(palSel, hits.size() - 1)));
                palette = false; return true;
            }
            return true;
        }
        if (key == InputConstants.KEY_K && e.hasControlDown()) {
            palette = true; palInput.value = ""; palSel = 0; return true;
        }
        if (search.focused) {
            if (key == InputConstants.KEY_BACKSPACE && !search.value.isEmpty())
                search.value = search.value.substring(0, search.value.length() - 1);
            if (key == InputConstants.KEY_ESCAPE) { search.focused = false; return true; }
            if (key == InputConstants.KEY_RETURN) { search.focused = false; return true; }
            return true;
        }
        if (expanded != null)
            for (Setting<?> s : expanded.settings)
                if (s instanceof Setting.Bind b && b.get() == -2) {
                    b.set(key == InputConstants.KEY_ESCAPE ? -1 : key); return true;
                }
        if (designTab)
            for (Object o : designItems())
                if (o instanceof Setting.Bind b && b.get() == -2) {
                    b.set(key == InputConstants.KEY_ESCAPE ? -1 : key); return true;
                }
        if (key == InputConstants.KEY_RSHIFT || key == InputConstants.KEY_ESCAPE) {
            onClose(); return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (palette) {
            if (e.isAllowedChatCharacter()) { palInput.value += e.codepointAsString(); palSel = 0; }
            return true;
        }
        if (search.focused) {
            if (e.isAllowedChatCharacter()) search.value += e.codepointAsString();
            return true;
        }
        if (e.isAllowedChatCharacter()) { search.focused = true; search.value += e.codepointAsString(); return true; }
        return super.charTyped(e);
    }

    @Override
    public void onClose() {
        NoxWindowScreen.winX = winX; NoxWindowScreen.winY = winY;
        NoxWindowScreen.winW = winW; NoxWindowScreen.winH = winH;
        NoxClient.INSTANCE.config.save();
        super.onClose();
    }
    @Override public boolean isPauseScreen() { return false; }
}
