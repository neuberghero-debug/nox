package dev.noxclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.noxclient.NoxClient;
import dev.noxclient.gui.comp.Charts;
import dev.noxclient.gui.comp.Inputs;
import dev.noxclient.gui.comp.Overlays;
import dev.noxclient.gui.ds.DS;
import dev.noxclient.gui.nvg.FxSprites;
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
import java.util.Locale;
import java.util.Map;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * ClickGUI — full-NanoVG rebuild. Every pixel is vector: real gradients,
 * real arcs, AA text with embedded Inter/JetBrains Mono, box-gradient
 * shadows and glows. No texture stairs anywhere.
 *
 * Layout (spec: mockup-reference):
 *  - window #0F0F12, 14px radius, soft shadow, draggable/resizable
 *  - titlebar: NOX wordmark, compact action strip, close button
 *  - left icon rail: one 50px button per category (glyph + caps label +
 *    enabled-count badge), active = accent gradient + left edge bar,
 *    Design gear pinned at the rail bottom
 *  - search + module rows 56px (iOS toggle, accent strip, letter-chip icon,
 *    mini keybind chip) row click = accordion expand (settings = darker
 *    child card, 12px indent); "All" groups rows under category headers
 *  - right column: LIVE STATS — sparkline + radial gauge + bar chart,
 *    real data every 5s (kills/min, K/D, money/hour)
 *  - footer: active count · pinned modules (MMB pins) · version · grip
 */
public class NoxClickGui extends Screen {
    public static boolean recordingMode;
    public static NoxClickGui current;

    private int winX = NoxWindowScreen.winX, winY = NoxWindowScreen.winY,
        winW = NoxWindowScreen.winW > 0 ? Math.max(560, NoxWindowScreen.winW) : 960,
        winH = NoxWindowScreen.winH > 0 ? Math.max(320, NoxWindowScreen.winH) : 560;

    private static final int TITLE = 44, SEARCH = 36, FOOT = 44,
        STATS_W = 236, GAP = 24, RAIL = 68;

    /** Row height driven by the Density setting (Compact/Normal/Spacious). */
    private int ROW() {
        return switch (UICustom.density) {
            case "Compact" -> 48;
            case "Spacious" -> 66;
            default -> 56;
        };
    }
    /** Animation speed honoring reduced-motion (instant when on). */
    private float animMul() { return UICustom.reducedMotion ? 1e9f : Math.max(.05f, UICustom.animSpeed); }

    private float openT;
    private final long openAt = System.currentTimeMillis();
    /** Restagger clock — reset on open + every tab/design switch so rows cascade. */
    private long listAt = System.currentTimeMillis();
    /* first-open intro veil — once per session, click/ESC skips */
    private static boolean introDone;
    private final long introAt = System.currentTimeMillis();
    private static final int INTRO_MS = 700;

    private int dragDX, dragDY;
    private boolean dragging, resizing;
    private Category tab = null;
    private final Overlays.Scrollable scroll = new Overlays.Scrollable(0);
    private final Inputs.Input search = new Inputs.Input(0, 0, 0, "search modules…");

    private Module expanded;
    private final Map<Module, Long> expAt = new HashMap<>();
    private final Map<Module, Long> togAt = new HashMap<>();
    private final Map<Module, Float> hovAt = new HashMap<>();
    private Setting.Num dragNum;
    private int dragNumX, dragNumW;

    /* per-setting control hitboxes recorded during draw — [x, y, w, h][].
     * Mode pills + Str fields are measured with Nvg.textW, so their clicks
     * must consume these rects (font.width measured a different font). */
    private final java.util.Map<Setting<?>, float[][]> ctrlRects = new java.util.HashMap<>();
    /** String setting currently receiving keyboard input. */
    private Setting.Str focusedStr;

    /* seasonal fx — particles + snow drift state (persistent across frames) */
    private final float[] fxX = new float[120], fxY = new float[120],
        fxV = new float[120], fxS = new float[120], fxPh = new float[120],
        fxR = new float[120], fxA = new float[120];
    private final int[] fxT = new int[120];
    private int fxN;
    private long fxLast;
    private float[] snowDrift;   // per-column drift height on the window top edge
    private String lastSeason = "";

    /* design page — grouped Interface-module settings, live editable */
    private boolean designTab;
    private final Overlays.Scrollable dScroll = new Overlays.Scrollable(0);
    private record DItem(Object o, int y, int h) {}   // String header | Setting
    private final List<DItem> dRows = new ArrayList<>();
    private List<Object> design;

    public static final java.util.List<String> PINNED = new ArrayList<>();

    private boolean palette;
    /** Palette opened from a "+" pin slot — clicks pin/unpin, stays open. */
    private boolean pinPick;
    private final Inputs.Input palInput = new Inputs.Input(0, 0, 0, "Type a module name…");
    private int palSel;

    /* frame-captured input for the deferred NVG draw */
    private int fmx, fmy;
    private float fdt;
    private long lastFrameNanos;

    private record Row(Module m, int y, int h) {}
    private record SettingRow(Module m, Setting<?> s, int x, int y, int w, int h) {}
    /** Category group header inside the All view — "COMBAT 1/3" + hairline. */
    private record Gh(String label, int on, int total, int y) {}
    private final List<Row> rows = new ArrayList<>();
    private final List<Gh> ghs = new ArrayList<>();
    private final List<SettingRow> settingRows = new ArrayList<>();
    private int scrollOff;
    /** Live tooltip text (rail buttons etc.) — drawn last, above content. */
    private String tipText;
    private Module tipModule;
    private long panicArmedAt;
    private boolean acInfo;
    private float[] acPanelRect;
    /** Keyboard-selection index into filtered() — ↑↓ moves, Enter toggles. */
    private int selIdx = -1;
    private Module keyboardSelection;

    public NoxClickGui() {
        super(Component.literal("NOX"));
        current = this;
    }

    @Override public void added() { super.added(); minecraft.onTextInputFocusChange(this, true); }
    @Override public void removed() { minecraft.onTextInputFocusChange(this, false); super.removed(); }

    public void setTabIndex(int i) {
        var vals = Category.values();
        tab = i < 0 || i >= vals.length ? null : vals[i];
        designTab = false;
        resetListPosition();
    }

    private void resetListPosition() {
        scroll.scroll = scroll.target = 0;
        selIdx = -1;
    }

    private void setQuery(String query) {
        search.value = query;
        resetListPosition();
    }

    /* ================= layout ================= */

    private int contentX() { return winX + RAIL + 16; }
    private int contentW() { return winW - RAIL - 32; }
    private boolean hasStats() { return winW >= 860 && listH() >= 300 && UICustom.showStats && !designTab; }
    private int listW() { return hasStats() ? contentW() - STATS_W - GAP : contentW(); }
    private int railY() { return winY + TITLE; }
    private int railH() { return winY + winH - footerH() - railY(); }
    private int searchY() { return railY() + 10; }
    private int listY() { return searchY() + SEARCH + 8; }
    private int footerH() { return UICustom.showPinned ? FOOT : 14; }
    private int listH() { return Math.max(0, winY + winH - footerH() - listY() - 6); }
    private float winR() { return Math.max(4, Math.min(24, UICustom.radius)); }

    private List<Module> filtered() {
        String q = search.value.strip().toLowerCase(Locale.ROOT);
        List<Module> out = new ArrayList<>();
        for (Module m : NoxClient.INSTANCE.modules.all()) {
            if (tab != null && m.category != tab) continue;
            if (!q.isEmpty() && !m.name.toLowerCase(Locale.ROOT).contains(q)
                && !m.description.toLowerCase(Locale.ROOT).contains(q)) continue;
            out.add(m);
        }
        if (tab == null && q.isEmpty())
            out.sort(java.util.Comparator.comparingInt(m -> m.category.ordinal()));
        return out;
    }

    private static boolean inside(double x, double y, float left, float top, float w, float h) {
        return x >= left && x < left + w && y >= top && y < top + h;
    }

    private boolean inList(double x, double y) {
        return inside(x, y, contentX(), listY(), listW(), listH());
    }

    /* ================= animations ================= */

    private static float easeOutCubic(float t) { return 1f - (float) Math.pow(1 - t, 3); }

    /** Smooth hover 0..1 — exponential approach on the frame delta. */
    private float hovFrac(Module m, boolean hov) {
        Float cur = hovAt.get(m);
        float c = cur == null ? 0f : cur;
        float t = hov ? 1f : 0f;
        c += (t - c) * Math.min(1f, fdt * 14f * animMul());
        if (Math.abs(c - t) < .012f) c = t;
        hovAt.put(m, c);
        return c;
    }

    private float expandFrac(Module m) {
        Long at = expAt.get(m);
        if (at == null) return 0;
        long t0 = Math.abs(at);
        float t = Math.min(1f, (System.currentTimeMillis() - t0) / (200f / animMul()));
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
        float t = Math.min(1f, (System.currentTimeMillis() - at) / (150f / animMul()));
        if (t >= 1f) { togAt.remove(m); return on ? 1 : 0; }
        // easeOutBack — the knob overshoots ~9% then settles, spring feel
        float f = 1f + 2.71f * (float) Math.pow(t - 1, 3) + 1.71f * (float) Math.pow(t - 1, 2);
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
        for (Setting<?> ignored : m.settings) h += 30;
        return h;
    }
    private int settingH(Setting<?> s) { return s instanceof Setting.Num ? 30 : 24; }

    /* ================= render ================= */

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        Charts.tickSample();
        if (dev.noxclient.util.DebugConsole.hoverX >= 0) {
            mx = (int) dev.noxclient.util.DebugConsole.hoverX;
            my = (int) dev.noxclient.util.DebugConsole.hoverY;
        }
        fmx = mx; fmy = my;
        long now = System.nanoTime();
        fdt = lastFrameNanos == 0 ? 1f / 60f : Math.min(.05f, (now - lastFrameNanos) / 1e9f);
        lastFrameNanos = now;
        // clamp the window fully on-screen (saved pos may come from a bigger display)
        winW = Math.min(winW, width - 20); winH = Math.min(winH, height - 20);
        if (winX < 0 || winY < 0) {
            winX = (width - winW) / 2;
            winY = (height - winH) / 2;
        }
        winX = Math.max(8, Math.min(winX, width - winW - 8));
        winY = Math.max(8, Math.min(winY, height - winH - 8));
        float t = Math.min(1f, (System.currentTimeMillis() - openAt) / (200f / animMul()));
        openT = easeOutCubic(t);

        // layout pass (scroll tick, row positions) — draw params the NVG op reads
        scroll.viewH = listH();
        scrollOff = scroll.tick(fdt);
        keyboardSelection = null;
        rows.clear(); settingRows.clear(); dRows.clear(); ctrlRects.clear(); ghs.clear();
        tipText = null; tipModule = null;
        if (designTab) {
            dScroll.viewH = listH();
            int dOff = dScroll.tick(fdt);
            int dy = listY() - dOff, dch = 0;
            for (Object o : designItems()) {
                int h = o instanceof String ? 24
                    : o instanceof PresetRow ? 34
                    : settingH((Setting<?>) o) + 6;
                if (dy + h >= listY() && dy <= listY() + listH())
                    dRows.add(new DItem(o, dy, h));
                dy += h; dch += h;
            }
            dScroll.contentH = dch;
        } else {
            List<Module> fm = filtered();
            if (selIdx >= 0 && selIdx < fm.size()) keyboardSelection = fm.get(selIdx);
            // "All" with no query groups rows under category headers (mockup look)
            boolean grouped = tab == null && search.value.isBlank();
            int y = listY() - scrollOff, ch = 0;
            if (grouped) {
                for (Category cat : Category.values()) {
                    List<Module> grp = new ArrayList<>();
                    int on = 0;
                    for (Module m : fm) if (m.category == cat) { grp.add(m); if (m.isEnabled()) on++; }
                    if (grp.isEmpty()) continue;
                    if (y + 22 >= listY() && y <= listY() + listH())
                        ghs.add(new Gh(cat.label, on, grp.size(), y));
                    y += 22; ch += 22;
                    for (Module m : grp) {
                        int sh = (m == expanded || expAt.containsKey(m))
                            ? (int) (expandFrac(m) * (settingsFullH(m) + 8)) : 0;
                        int rh = ROW() + sh;
                        rows.add(new Row(m, y, rh));
                        y += rh + 4; ch += rh + 4;
                    }
                }
            } else {
                for (Module m : fm) {
                    int sh = (m == expanded || expAt.containsKey(m))
                        ? (int) (expandFrac(m) * (settingsFullH(m) + 8)) : 0;
                    int rh = ROW() + sh;
                    rows.add(new Row(m, y, rh));
                    y += rh + 4; ch += rh + 4;
                }
            }
            scroll.contentH = ch;
        }

        // backdrop dim — deferred batch stays UNDER the NVG window
        if (!recordingMode) Gui.backdrop(g, width, height, openT);

        NvgOverlay.op(this::drawAll);
        if (!introDone) NvgOverlay.op(this::drawIntro);
        if (palette) NvgOverlay.op(this::drawPalette);
        else super.extractRenderState(g, mx, my, dt);
    }

    /* ================= NVG draw (runs in the post-pass) ================= */

    private void drawAll(long c, MemoryStack st) {
        int mx = fmx, my = fmy;
        float prevFontScale = Nvg.fontScale;
        Nvg.fontScale = UICustom.fontScale * UICustom.textScale();
        float wr = winR();
        nvgSave(c);
        nvgGlobalAlpha(c, openT);

        // window shell — accent halo + deep shadow + obsidian gradient body
        float bodyA = switch (UICustom.winStyle) {
            case "Sky" -> UICustom.winOpacity * 0.55f;
            case "Glass" -> UICustom.winOpacity * 0.82f;
            default -> UICustom.winOpacity;
        };
        if (UICustom.glow > 0.05f) {
            Nvg.shadow(c, st, winX + 6, winY + 10, winW - 12, winH, wr, 30 * UICustom.glow,
                Nvg.fade(DS.accent(), 0.10f * Math.min(1f, UICustom.glow)));
            Nvg.shadow(c, st, winX, winY + 4, winW, winH, wr, 22 * UICustom.glow,
                Nvg.fade(0x70000000, Math.min(1f, UICustom.glow)));
        }
        Nvg.vgrad(c, st, winX, winY, winW, winH, wr,
            Nvg.fade(DS.lighten(DS.bg(), .045f), bodyA), Nvg.fade(DS.bg(), bodyA));
        Nvg.borderGrad(c, st, winX, winY, winW, winH, wr,
            0x24FFFFFF, Nvg.fade(DS.accent(), 0.28f), 0x00FFFFFF);
        Nvg.insetHi(c, st, winX + 2, winY, winW - 4, wr - 2, 0x14FFFFFF);

        titlebar(c, st, mx, my);
        acPanelRect = null;
        rail(c, st, mx, my);
        searchBar(c, st, mx, my);
        if (designTab) designPage(c, st);
        else moduleList(c, st, mx, my);
        if (hasStats()) statsPanel(c, st);
        pinRects.clear();
        if (UICustom.showPinned) pinnedBar(c, st, mx, my);

        // resize grip — three diagonal ticks in the bottom-right corner
        boolean rhov = resizing || inside(mx, my, winX + winW - 24, winY + winH - 24, 24, 24);
        int grip = rhov ? Nvg.fade(DS.accent(), .85f) : 0x40FFFFFF;
        for (int k = 0; k < 3; k++) {
            float gx = winX + winW - 7 - k * 5, gy = winY + winH - 7;
            Nvg.line(c, st, gx, gy, gx + (k + 1) * 5 - 4, gy - (k + 1) * 5 + 4, 1.4f, grip);
        }

        seasonalFx(c, st);   // flakes drift over content; snow piles on the top edge
        if (acInfo) {
            for (float[] b : titleBtns) if (b[4] == 2) { acPanel(c, st, b); break; }
        } else {
            drawTooltip(c, st, mx, my);
        }

        nvgRestore(c);
        Nvg.fontScale = prevFontScale;
    }

    /** Hover tooltip — dark card anchored right-below the cursor, clamped
     *  to the window. text measured in the same font it is drawn with. */
    private void drawTooltip(long c, MemoryStack st, int mx, int my) {
        if (tipModule != null) { moduleCard(c, st, mx, my, tipModule); return; }
        if (tipText == null || tipText.isEmpty()) return;
        float tw = Nvg.textW(c, "inter", 10f, tipText) + 16;
        float tx = Math.min(mx + 12, winX + winW - tw - 6);
        float ty = Math.min(my + 14, winY + winH - 26);
        Nvg.shadow(c, st, tx, ty + 2, tw, 20, 7, 8, 0x50000000);
        Nvg.round(c, st, tx, ty, tw, 20, 7, Nvg.fade(DS.lighten(DS.surface(), .05f), .97f));
        Nvg.roundB(c, st, tx, ty, tw, 20, 7, 0x00FFFFFF, DS.line());
        Nvg.text(c, st, "inter", 10f, tx + 8, ty + 10, tipText, DS.text(),
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
    }

    /** Rich hover card for a module row — name + status line + description +
     *  category/key/settings meta + the click hints, all live. */
    private void moduleCard(long c, MemoryStack st, int mx, int my, Module m) {
        float tw = 236, th = 92;
        float tx = Math.min(mx + 14, winX + winW - tw - 8);
        float ty = Math.min(my + 16, winY + winH - th - 8);
        Nvg.shadow(c, st, tx, ty + 2, tw, th, 9, 10, 0x55000000);
        Nvg.round(c, st, tx, ty, tw, th, 9, Nvg.fade(DS.lighten(DS.surface(), .05f), .985f));
        Nvg.roundB(c, st, tx, ty, tw, th, 9, 0x00FFFFFF,
            Nvg.fade(DS.accent(), .5f));
        // icon chip + name + status dot
        Nvg.round(c, st, tx + 8, ty + 8, 22, 22, 6,
            Nvg.fade(DS.lighten(DS.surface(), .06f), .9f));
        drawIcon(c, st, m, tx + 19, ty + 19, 13,
            m.isEnabled() ? DS.cardAccent() : DS.secondary(), .8f);
        Nvg.text(c, st, "inter_b", 11.5f, tx + 36, ty + 14, m.name
            + (m.premium ? " ★" : ""), DS.text(),
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        String stat = m.isEnabled() ? (m.status() == null ? "active" : m.status()) : "idle";
        Nvg.circle(c, st, tx + 39, ty + 28, 2.4f, m.isEnabled() ? DS.success() : DS.muted());
        Nvg.text(c, st, "inter", 9.5f, tx + 46, ty + 28, stat, DS.muted(),
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        Nvg.text(c, st, "inter", 10, tx + 12, ty + 44, truncN(c, "inter", 10, m.description, tw - 24), DS.secondary(),
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        String meta = m.category.label.toLowerCase(java.util.Locale.US)
            + "  ·  " + (m.keybind() > 0 ? "key " + m.keybind() : "unbound")
            + "  ·  " + m.settings.size() + " settings";
        Nvg.text(c, st, "mono", 9, tx + 12, ty + 60, meta, DS.muted(),
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        Nvg.text(c, st, "inter", 9, tx + 12, ty + 76, "LMB toggle · RMB settings · MMB pin",
            Nvg.fade(DS.muted(), .8f), org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
    }

    /** Per-season particles — real MC particle sprites. Winter snow falls
     * screen-wide and piles onto the window's top edge (Snow piling toggle),
     * autumn tinted leaves tumble, spring cherry petals drift, summer green
     * leaves fall while fireflies wander. Density = FX density setting. */
    private void seasonalFx(long c, MemoryStack st) {
        if (!UICustom.seasonFx || UICustom.reducedMotion) { fxN = 0; return; }
        FxSprites.ensure();
        String sn = "Auto".equals(UICustom.season) ? seasonNow() : UICustom.season;
        long now = System.nanoTime();
        float dt = fxLast == 0 ? .016f : Math.min(.05f, (now - fxLast) / 1e9f);
        fxLast = now;
        if (!sn.equals(lastSeason)) { fxN = 0; snowDrift = null; lastSeason = sn; }
        int cols = Math.max(1, winW / 10);
        if (snowDrift == null || snowDrift.length != cols) snowDrift = new float[cols];
        float den = Math.max(.2f, UICustom.fxDensity);
        int target = (int) (switch (sn) { case "Winter" -> 96; case "Autumn" -> 46;
            case "Spring" -> 52; default -> 40; } * den);
        target = Math.min(120, target);
        int flies = "Summer".equals(sn) ? Math.min(10, (int) (7 * den)) : 0;
        while (fxN < target) spawnFx(fxN++, sn, flies);
        if (fxN > target) fxN = target;
        float swayA = "Autumn".equals(sn) ? 34 : "Summer".equals(sn) ? 10 : 14;
        for (int i = 0; i < fxN; i++) {
            boolean fly = i < flies;
            fxPh[i] += dt * (fly ? 1.1f : 2.2f);
            fxX[i] += (float) Math.sin(fxPh[i]) * (fly ? 26 : swayA) * dt;
            fxY[i] += fxV[i] * dt;
            if (fly) fxY[i] += (float) Math.cos(fxPh[i] * .7f) * 9f * dt;
            // winter flakes landing on the window's top edge feed the drift —
            // they shrink out over their last ~4px instead of popping away
            if ("Winter".equals(sn) && UICustom.snowPile && fxY[i] >= winY - 1
                && fxX[i] >= winX - 4 && fxX[i] <= winX + winW + 4) {
                int ci = Math.max(0, Math.min(cols - 1,
                    (int) ((fxX[i] - winX) / (winW / (float) cols))));
                snowDrift[ci] = Math.min(7f, snowDrift[ci] + .05f);
                fxY[i] = -20 - (float) Math.random() * 60;
                fxX[i] = (float) Math.random() * width;
                continue;
            }
            // pre-landing fade makes the "stick" read as soft, not a vanish
            float landFade = "Winter".equals(sn) && UICustom.snowPile
                && fxX[i] >= winX && fxX[i] <= winX + winW
                && fxY[i] > winY - 5 && fxY[i] < winY
                ? Math.max(.15f, (winY - fxY[i]) / 5f) : 1f;
            if (fxY[i] > height + 16 || (fxV[i] < 0 && fxY[i] < -40)
                || fxX[i] < -30 || fxX[i] > width + 30) {
                respawnFx(i, sn, fly);
                continue;
            }
            int[] set = switch (sn) {
                case "Winter" -> FxSprites.snow();
                case "Autumn" -> FxSprites.leafAutumn();
                case "Spring" -> FxSprites.petal();
                default -> fly ? FxSprites.glow() : FxSprites.leafGreen();
            };
            int h = set.length == 0 ? 0 : FxSprites.handle(set[fxT[i] % set.length]);
            if (h == 0) {   // sprite not uploaded yet — soft dot fallback
                Nvg.circle(c, st, fxX[i], fxY[i], fxS[i] * .35f,
                    "Winter".equals(sn) ? 0x99FFFFFF : Nvg.fade(DS.cardAccent(), .3f));
                continue;
            }
            if (fly) {
                float gl = .45f + .35f * (float) Math.sin(fxPh[i] * 3.1f);
                Nvg.imgRot(c, st, h, fxX[i], fxY[i], fxS[i] * 3.4f, fxS[i] * 3.4f,
                    0, gl * .35f * fxA[i]);
                Nvg.circle(c, st, fxX[i], fxY[i], fxS[i] * .55f,
                    Nvg.fade(0xFFFFF0A8, (.55f + gl * .45f) * fxA[i]));
            } else {
                float tumble = "Winter".equals(sn) ? .25f : 1f;
                // fake 3D tumble: squash on the flip axis for leaves/petals
                float sq = "Winter".equals(sn) ? 1f
                    : .45f + .55f * Math.abs((float) Math.cos(fxPh[i] * .9f));
                Nvg.imgRot(c, st, h, fxX[i], fxY[i], fxS[i], fxS[i] * sq,
                    fxPh[i] * fxR[i] * tumble, fxA[i] * landFade);
            }
        }
        if ("Winter".equals(sn) && UICustom.snowPile) {
            boolean any = false;
            for (int i = 0; i < cols; i++) {
                snowDrift[i] = Math.max(0, snowDrift[i] - dt * .07f);   // slow melt
                if (snowDrift[i] > .25f) any = true;
            }
            if (any) {
                // smooth crest silhouette — neighbor-averaged heights, one path
                float colW = winW / (float) cols;
                org.lwjgl.nanovg.NanoVG.nvgBeginPath(c);
                org.lwjgl.nanovg.NanoVG.nvgMoveTo(c, winX - 2, winY + 5);
                for (int i = 0; i < cols; i++) {
                    float d = snowDrift[i];
                    if (i > 0) d = (snowDrift[i - 1] + d * 2) / 3f;
                    if (i < cols - 1) d = (d * 2 + snowDrift[i + 1]) / 3f;
                    org.lwjgl.nanovg.NanoVG.nvgLineTo(c,
                        winX + (i + .5f) * colW, winY + 1.5f - Math.min(7f, d));
                }
                org.lwjgl.nanovg.NanoVG.nvgLineTo(c, winX + winW + 2, winY + 5);
                org.lwjgl.nanovg.NanoVG.nvgLineTo(c, winX + winW + 2, winY + 7);
                org.lwjgl.nanovg.NanoVG.nvgLineTo(c, winX - 2, winY + 7);
                org.lwjgl.nanovg.NanoVG.nvgClosePath(c);
                org.lwjgl.nanovg.NVGPaint dp = org.lwjgl.nanovg.NanoVG.nvgLinearGradient(
                    c, 0, winY - 6, 0, winY + 8,
                    Nvg.col(st, 0xE8FFFFFF), Nvg.col(st, 0x22FFFFFF),
                    org.lwjgl.nanovg.NVGPaint.malloc(st));
                org.lwjgl.nanovg.NanoVG.nvgFillPaint(c, dp);
                org.lwjgl.nanovg.NanoVG.nvgFill(c);
            }
        }
    }
    private void spawnFx(int i, String sn, int flies) {
        boolean fly = i < flies;
        fxX[i] = (float) Math.random() * width;
        fxY[i] = fly ? height * (.25f + (float) Math.random() * .7f)
            : -10 - (float) Math.random() * height * .6f;
        fxV[i] = fly ? -(3 + (float) Math.random() * 9)
            : switch (sn) {
                case "Winter" -> 16 + (float) Math.random() * 22;
                case "Autumn" -> 30 + (float) Math.random() * 34;
                case "Spring" -> 14 + (float) Math.random() * 18;
                default -> 22 + (float) Math.random() * 26;
            };
        fxS[i] = fly ? 1.6f + (float) Math.random() * 1.4f
            : switch (sn) {
                case "Winter" -> 3.2f + (float) Math.random() * 4.6f;
                case "Autumn" -> 8f + (float) Math.random() * 6f;
                case "Spring" -> 6f + (float) Math.random() * 5f;
                default -> 7f + (float) Math.random() * 5f;
            };
        fxPh[i] = (float) (Math.random() * Math.PI * 2);
        fxR[i] = .6f + (float) Math.random() * 1.8f;
        fxA[i] = "Winter".equals(sn) ? .55f + (float) Math.random() * .4f
            : .6f + (float) Math.random() * .35f;
        fxT[i] = (int) (Math.random() * 12);
    }
    private void respawnFx(int i, String sn, boolean fly) {
        fxX[i] = (float) Math.random() * width;
        fxY[i] = fly ? height + 10 + (float) Math.random() * 20
            : -12 - (float) Math.random() * 50;
        if (fxV[i] == 0) fxV[i] = 20;
    }
    private String seasonNow() {
        return switch (java.time.LocalDate.now().getMonthValue()) {
            case 12, 1, 2 -> "Winter";
            case 3, 4, 5 -> "Spring";
            case 6, 7, 8 -> "Summer";
            default -> "Autumn";
        };
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
            {"Atmosphere", "Season", "Seasonal FX", "Snow piling", "FX density",
                "Screen backdrop", "Backdrop level"},
        };
        for (String[] g : groups) {
            design.add(g[0]);
            for (int i = 1; i < g.length; i++) {
                Setting<?> s = byName.remove(g[i]);
                if (s != null) design.add(s);
            }
        }
        if (!byName.isEmpty()) { design.add("More"); design.addAll(byName.values()); }
        // Presets group — profile files with hotkey binding (LMB load, MMB bind)
        design.add("Presets");
        for (String p : NoxClient.INSTANCE.config.presets()) design.add(new PresetRow(p));
        design.add(new PresetRow(null));   // "+ save current as preset"
        return design;
    }

    /** Design-page preset row — name=null renders the "save current" action. */
    private record PresetRow(String name) {}
    private String presetBindFor;   // preset name waiting for a keypress

    private void designPage(long c, MemoryStack st) {
        int lw = listW();
        Nvg.scissor(c, contentX(), listY(), lw, listH());
        for (DItem it : dRows) {
            if (it.o instanceof String hdr) {
                Nvg.textL(c, st, "inter_b", 9, contentX() + 4, it.y + it.h / 2f,
                    hdr.toUpperCase(java.util.Locale.US), DS.cardAccent(), 1.6f,
                    org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
            } else if (it.o instanceof PresetRow pr) {
                Nvg.vgrad(c, st, contentX(), it.y, lw, it.h - 4, 10,
                    Nvg.fade(DS.lighten(DS.surfaceAlt(), .04f), .7f), Nvg.fade(DS.surface(), .7f));
                Nvg.insetHi(c, st, contentX() + 2, it.y, lw - 4, 8, 0x0CFFFFFF);
                float cy = it.y + it.h / 2f;
                if (pr.name() == null) {
                    Nvg.text(c, st, "inter_sb", 11, contentX() + 14, cy, "+ save current as preset",
                        DS.cardAccent(), org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
                } else {
                    boolean arming = pr.name().equals(presetBindFor);
                    Nvg.circle(c, st, contentX() + 16, cy, 3f, DS.cardAccent());
                    Nvg.text(c, st, "inter_sb", 11, contentX() + 28, cy, pr.name(), DS.text(),
                        org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
                    String bound = null;
                    for (var e : NoxClient.INSTANCE.config.presetKeys.entrySet())
                        if (e.getValue().equals(pr.name())) bound = keyName(e.getKey());
                    String right = arming ? "press a key… (esc clears)"
                        : bound != null ? "[" + bound + "]"
                        : "LMB load · MMB bind key";
                    Nvg.text(c, st, "mono", 9, contentX() + lw - 14, cy, right,
                        arming ? DS.cardAccent() : DS.muted(),
                        org.lwjgl.nanovg.NanoVG.NVG_ALIGN_RIGHT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
                }
            } else {
                Setting<?> s = (Setting<?>) it.o;
                Nvg.vgrad(c, st, contentX(), it.y, lw, it.h - 4, 10,
                    Nvg.fade(DS.lighten(DS.surfaceAlt(), .04f), .7f), Nvg.fade(DS.surface(), .7f));
                Nvg.insetHi(c, st, contentX() + 2, it.y, lw - 4, 8, 0x0CFFFFFF);
                drawSetting(c, st, s, contentX() + 12, it.y + 2, lw - 24);
            }
        }
        Nvg.unScissor(c);
        dScroll.renderBarNvg(c, st, contentX() + lw - 2, listY());
    }

    /* ---------- titlebar ---------- */
    private void titlebar(long c, MemoryStack st, int mx, int my) {
        float r = winR() - 1;
        nvgBeginPath(c);
        nvgRoundedRectVarying(c, winX + 1, winY + 1, winW - 2, TITLE - 1, r, r, 0, 0);
        nvgFillColor(c, Nvg.col(st, DS.header()));
        nvgFill(c);
        Nvg.rect(c, st, winX + 1, winY + TITLE - 1, winW - 2, 1, DS.line());

        boolean compact = winW < 480;
        float cy = winY + TITLE / 2f;
        float lx = winX + 19;
        Nvg.circle(c, st, lx, cy, 6.4f, 0xFF05060A);
        Nvg.ring(c, st, lx, cy, 6.4f, 1.1f, DS.accent());
        Nvg.arc(c, st, lx, cy, 7.6f, 1.1f, -1.1f, 1.5f, DS.accent2());
        Nvg.textL(c, st, "inter_b", Math.min(13f, 16f / Nvg.fontScale),
            winX + 34, compact ? cy : cy - 6, "NOX", DS.text(), 1.8f, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        if (!compact) {
            String version = "Studio / v" + dev.noxclient.update.UpdateManager.currentVersion()
                + (dev.noxclient.premium.LicenseManager.premium() ? " Pro" : "");
            Nvg.text(c, st, "mono", 8, winX + 34, cy + 8, version, DS.secondary(),
                NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        }
        titleBtns.clear();
        float rx = winX + winW - 12;
        rx = titleButton(c, st, mx, my, rx, "", DS.secondary(), 4, "Close (Esc)", compact);
        boolean armed = System.currentTimeMillis() - panicArmedAt < 2500;
        rx = titleButton(c, st, mx, my, rx, armed ? "SURE?" : "PANIC", DS.danger(), 0,
            armed ? "Click again to confirm" : "Disable active modules", compact);
        String ac = AntiCheats.display();
        int status = switch (ac) {
            case "None", "Vanilla" -> DS.success();
            case "Grim", "Polar" -> DS.danger();
            default -> DS.warning();
        };
        rx = titleButton(c, st, mx, my, rx, compact ? "AC" : "AC: " + ac, status, 2,
            "Detected anti-cheat: " + ac, compact);
        rx = titleButton(c, st, mx, my, rx, "SHOP", DS.secondary(), 1, "Open shop", compact);
        titleButton(c, st, mx, my, rx, compact ? "WIN" : "WINDOW", DS.secondary(), 3,
            "Switch to the window GUI", compact);
    }

    private float titleButton(long c, MemoryStack st, int mx, int my, float right, String label,
                              int color, int action, String tip, boolean compact) {
        float size = Math.min(compact ? 9f : 10f, (compact ? 10f : 13f) / Nvg.fontScale);
        float w = Math.max(24, Nvg.textW(c, "inter_b", size, label) + (compact ? 12 : 18));
        float x = right - w, y = winY + 10;
        boolean hover = inside(mx, my, x, y, w, 24);
        Nvg.roundB(c, st, x, y, w, 24, 7, Nvg.fade(color, hover ? .22f : .09f),
            hover || action == 0 ? Nvg.fade(color, .48f) : DS.line());
        float cx = x + w / 2, cy = y + 12;
        if (action == 4) {
            Nvg.line(c, st, cx - 3, cy - 3, cx + 3, cy + 3, 1.4f, color);
            Nvg.line(c, st, cx - 3, cy + 3, cx + 3, cy - 3, 1.4f, color);
        } else {
            Nvg.text(c, st, "inter_b", size, cx, cy, label, color, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        }
        if (hover) tipText = tip;
        titleBtns.add(new float[]{x, y, w, 24, action});
        return x - (compact ? 4 : 6);
    }

    /* ---------- ac info dropdown ---------- */
    /** Detail card under the AC pill — what was detected and how. */
    private void acPanel(long c, MemoryStack st, float[] button) {
        float pw = Math.min(230, winW - 16), ph = 118;
        float px = Math.max(winX + 8, Math.min(button[0] + button[2] - pw, winX + winW - pw - 8));
        float py = winY + TITLE + 4;
        acPanelRect = new float[]{px, py, pw, ph};
        Nvg.shadow(c, st, px, py + 3, pw, ph, 10, 10, 0x60000000);
        Nvg.round(c, st, px, py, pw, ph, 10, Nvg.fade(DS.lighten(DS.surface(), .03f), .98f));
        Nvg.roundB(c, st, px, py, pw, ph, 10, 0x00FFFFFF, DS.line());
        Nvg.text(c, st, "inter_b", 11, px + 12, py + 12, "Anti-cheat detection", DS.text(),
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        Nvg.rect(c, st, px + 12, py + 24, pw - 24, 1, DS.line());
        String[][] rows = {
            {"Detected", AntiCheats.display()},
            {"Source", AntiCheats.source()},
            {"Brand", AntiCheats.brandOr("—")},
            {"Profile", AntiCheats.advice()},
        };
        float ry = py + 36;
        for (String[] r : rows) {
            Nvg.text(c, st, "inter", 10, px + 12, ry, r[0], DS.muted(),
                org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
            Nvg.text(c, st, "mono", 10, px + 82, ry, truncN(c, "mono", 10, r[1], pw - 94), DS.secondary(),
                org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
            ry += 16;
        }
        Nvg.text(c, st, "inter", 9.5f, px + 12, ry + 4,
            "High-risk modules pace themselves on grim.", DS.muted(),
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
    }

    /* ---------- left icon rail ---------- */
    /** Rail hitboxes captured during the draw pass — [x, y, w, h, action].
     *  action: -1 = All, 0..n-1 = Category.values()[n], -2 = Design. */
    private final List<float[]> railRects = new ArrayList<>();
    /** Titlebar buttons — [x, y, w, h, action] captured at draw. */
    private final List<float[]> titleBtns = new ArrayList<>();

    /** Category → rail glyph (shared icon map with module rows). */
    private String railGlyph(Category cat) {
        if (cat == null) return null;   // All = custom 2×2 grid
        return switch (cat) {
            case COMBAT -> "sword";
            case MOVEMENT -> "wings";
            case RENDER -> "eye";
            case DONUTSMP -> "donut";
            case MISC -> "star";
        };
    }

    /** 2×2 rounded grid — the "All" rail icon. */
    private void gridIcon(long c, MemoryStack st, float cx, float cy, float s, int argb) {
        float g = s * .18f, q = (s - g) / 2f;
        for (int i = 0; i < 4; i++)
            Nvg.round(c, st, cx - s / 2 + (i % 2) * (q + g), cy - s / 2 + (i / 2) * (q + g),
                q, q, q * .35f, argb);
    }

    private void rail(long c, MemoryStack st, int mx, int my) {
        railRects.clear();
        int ry = railY(), rh = railH();
        // right edge hairline separating the rail from the list column
        Nvg.rect(c, st, winX + RAIL, ry + 4, 1, rh - 8, Nvg.fade(DS.line(), .8f));

        int n = Category.values().length, gap = 3, slots = n + 3;
        int bw = 50, bh = Math.min(46, Math.max(1, (rh - 12 - (slots - 1) * gap) / slots));
        boolean compact = bh < 30;
        float labelSize = Math.min(7.2f, 8.5f / Nvg.fontScale);
        float bx = winX + (RAIL - bw) / 2f;
        float by = ry + 6;
        for (int k = 0; k <= n; k++) {
            Category cat = k == 0 ? null : Category.values()[k - 1];
            String lbl = cat == null ? "ALL" : cat.label.toUpperCase(java.util.Locale.US);
            int total = 0, on = 0;
            for (Module m : NoxClient.INSTANCE.modules.all()) {
                if (cat != null && m.category != cat) continue;
                total++; if (m.isEnabled()) on++;
            }
            boolean act = !designTab && tab == cat;
            boolean hov = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
            if (act) {
                // flat accent tile — one state marker, no glow stack
                Nvg.round(c, st, bx, by, bw, bh, 11, DS.accent());
                Nvg.rect(c, st, bx - (RAIL - bw) / 2f + 1, by + bh * .26f, 2.5f, bh * .48f,
                    DS.accent2());
            } else if (hov) {
                Nvg.round(c, st, bx, by, bw, bh, 11, DS.lighten(DS.surface(), .09f));
            }
            int icol = act ? 0xFFFFFFFF : hov ? DS.text() : DS.secondary();
            String gl = railGlyph(cat);
            if (!compact) {
                if (gl == null) gridIcon(c, st, bx + bw / 2f, by + bh * .36f, 15, icol);
                else Nvg.glyph(c, st, gl, bx + bw / 2f, by + bh * .36f, 17, icol);
            }
            Nvg.text(c, st, "inter_b", labelSize, bx + bw / 2f, by + bh * (compact ? .5f : .74f), lbl,
                act ? 0xF2FFFFFF : hov ? DS.text() : DS.secondary(), NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
            if (!compact && on > 0) {
                String cnt = String.valueOf(on);
                float cw = Nvg.textW(c, "mono", 7f, cnt) + 6;
                Nvg.round(c, st, bx + bw - cw - 3, by + 3, cw, 10, 5,
                    act ? 0x40FFFFFF : Nvg.fade(DS.accent(), .22f));
                Nvg.text(c, st, "mono", 7f, bx + bw - 3 - cw / 2, by + 8, cnt,
                    act ? 0xFFFFFFFF : DS.accent2(),
                    org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
            }
            if (hov && tipText == null)
                tipText = (cat == null ? "All" : cat.label) + " — " + on + "/" + total + " on";
            railRects.add(new float[]{bx, by, bw, bh, k - 1});
            by += bh + gap;
        }
        // Design gear pinned at the rail bottom
        float dy = Math.max(by, ry + rh - 6 - 2 * bh - gap);
        boolean dact = designTab;
        boolean dhov = mx >= bx && mx < bx + bw && my >= dy && my < dy + bh;
        if (dact) {
            Nvg.shadow(c, st, bx + 2, dy + 2, bw - 4, bh - 2, 11, 9, Nvg.fade(DS.accent(), 0.32f));
            Nvg.hgrad(c, st, bx, dy, bw, bh, 11, DS.accent(), DS.accent2());
            Nvg.insetHi(c, st, bx + 2, dy, bw - 4, 9, 0x33FFFFFF);
        } else if (dhov) {
            Nvg.well(c, st, bx, dy, bw, bh, 11, DS.lighten(DS.surface(), .09f));
        }
        if (!compact) gear(c, st, bx + bw / 2f, dy + bh * .36f, 6.2f,
            dact ? 0xFFFFFFFF : dhov ? DS.text() : DS.secondary());
        Nvg.text(c, st, "inter_b", labelSize, bx + bw / 2f, dy + bh * (compact ? .5f : .74f), "STYLE",
            dact ? 0xF2FFFFFF : dhov ? DS.text() : DS.secondary(), NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        if (dhov && tipText == null) tipText = "Design — accent, backdrop, density";
        railRects.add(new float[]{bx, dy, bw, bh, -2});

        // HUD editor — frame glyph rail button under STYLE
        float hy = dy + bh + gap;
        boolean hHov = mx >= bx && mx < bx + bw && my >= hy && my < hy + bh;
        if (hHov) Nvg.well(c, st, bx, hy, bw, bh, 11, DS.lighten(DS.surface(), .09f));
        float fcx = bx + bw / 2f, fcy = hy + bh * .36f;
        int hcol = hHov ? DS.text() : DS.secondary();
        for (int i = 0; !compact && i < 4; i++) {   // four corner brackets = layout frame
            float sx = i % 2 == 0 ? -1 : 1, sy = i < 2 ? -1 : 1;
            float ox = fcx + sx * 6.4f, oy = fcy + sy * 5f;
            Nvg.line(c, st, ox, oy, ox - sx * 4.2f, oy, 1.4f, hcol);
            Nvg.line(c, st, ox, oy, ox, oy - sy * 3.4f, 1.4f, hcol);
        }
        Nvg.text(c, st, "inter_b", labelSize, fcx, hy + bh * (compact ? .5f : .74f), "HUD",
            hHov ? DS.text() : DS.secondary(), NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        if (hHov && tipText == null) tipText = "HUD editor — drag elements, snap";
        railRects.add(new float[]{bx, hy, bw, bh, -3});
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
        if (designTab) {
            Nvg.text(c, st, "inter_sb", 15, contentX() + 4, searchY() + SEARCH / 2f,
                "Style & layout", DS.text(), NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            return;
        }
        search.x = contentX(); search.y = searchY(); search.w = contentW(); search.h = SEARCH;
        boolean hov = mx >= search.x && mx < search.x + search.w && my >= search.y && my < search.y + search.h;
        Nvg.well(c, st, search.x, search.y, search.w, search.h, 10,
            Nvg.fade(DS.surface(), .8f));
        if (search.focused) {
            Nvg.borderGrad(c, st, search.x, search.y, search.w, search.h, 10,
                DS.accent(), Nvg.fade(DS.accent(), .8f), DS.accent2());
            if (UICustom.glow > 0.05f)
                Nvg.shadow(c, st, search.x, search.y, search.w, search.h, 10, 8 * UICustom.glow,
                    Nvg.fade(DS.accent(), 0.2f * Math.min(1f, UICustom.glow)));
        } else if (hov) {
            Nvg.roundB(c, st, search.x, search.y, search.w, search.h, 10, 0x00FFFFFF,
                UICustom.borderHover);
        }
        // magnifier
        float ix = search.x + 16, iy = search.y + search.h / 2f;
        Nvg.ring(c, st, ix, iy - 1, 4.5f, 1.6f, search.focused ? DS.accent() : DS.muted());
        Nvg.line(c, st, ix + 3.4f, iy + 2.6f, ix + 7, iy + 6.4f, 1.6f,
            search.focused ? DS.accent() : DS.muted());
        boolean empty = search.value.isEmpty();
        String results = rows.size() + (rows.size() == 1 ? " result" : " results");
        float textX = search.x + 32;
        float textW = empty ? search.w - 40 : search.w - 72 - Nvg.textW(c, "mono", 9.5f, results);
        if (textW > 0) {
            float advance = Nvg.textW(c, "inter", 12, search.value);
            float offset = search.focused ? Math.max(0, advance - textW + 3) : 0;
            Nvg.clipPush(c, textX, search.y, textW, search.h);
            Nvg.text(c, st, "inter", 12, textX - offset, iy, empty ? "Search modules..." : search.value,
                empty ? DS.secondary() : DS.text(), NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            if (search.focused && System.currentTimeMillis() % 1000 < 550)
                Nvg.rect(c, st, textX + advance - offset, search.y + 10, 1.4f, search.h - 20, DS.accent());
            Nvg.clipPop(c);
        }
        if (!search.value.isEmpty()) {
            Nvg.text(c, st, "mono", 9.5f, search.x + search.w - 36, search.y + search.h / 2f, results,
                DS.secondary(), org.lwjgl.nanovg.NanoVG.NVG_ALIGN_RIGHT
                    | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
            float cxx = search.x + search.w - 20;
            Nvg.circle(c, st, cxx, search.y + search.h / 2f, 8, Nvg.fade(DS.muted(), 0.35f));
            Nvg.line(c, st, cxx - 3, search.y + search.h / 2f - 3, cxx + 3, search.y + search.h / 2f + 3, 1.5f, DS.text());
            Nvg.line(c, st, cxx + 3, search.y + search.h / 2f - 3, cxx - 3, search.y + search.h / 2f + 3, 1.5f, DS.text());
        }
    }

    /* ---------- module list ---------- */
    private void moduleList(long c, MemoryStack st, int mx, int my) {
        int lw = listW();
        Nvg.scissor(c, contentX(), listY(), lw, listH());
        if (rows.isEmpty() && !search.value.isBlank()) {
            float cx = contentX() + lw / 2f, cy = listY() + listH() / 2f;
            Nvg.text(c, st, "inter_sb", 14, cx, cy - 9, "No modules found", DS.text(),
                NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
            Nvg.text(c, st, "inter", 10, cx, cy + 9, "Try another search or category", DS.secondary(),
                NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        }
        // category group headers — "COMBAT 1/3" + fading hairline (All view)
        for (Gh h : ghs) {
            String lbl = h.label.toUpperCase(java.util.Locale.US);
            float lblw = Nvg.textLW(c, "inter_b", 9f, lbl, 1.6f);
            String cnt = h.on + "/" + h.total;
            Nvg.textL(c, st, "inter_b", 9f, contentX() + 4, h.y + 10, lbl, DS.muted(), 1.6f,
                org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
            Nvg.text(c, st, "mono", 8.5f, contentX() + 4 + lblw + 8, h.y + 10, cnt,
                DS.cardAccent(), org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT
                    | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
            float lx = contentX() + 4 + lblw + 8 + Nvg.textW(c, "mono", 8.5f, cnt) + 12;
            Nvg.line(c, st, lx, h.y + 10, contentX() + lw - 4, h.y + 10, 1f,
                Nvg.fade(DS.line(), .9f));
        }
        float elapsed = (System.currentTimeMillis() - listAt) * animMul();
        int shown = 0;
        for (Row r : rows) {
            if (r.y + r.h <= listY() || r.y >= listY() + listH()) continue;
            float delay = Math.min(shown++, 5) * 18f;
            float reveal = UICustom.reducedMotion ? 1f : .55f + .45f
                * easeOutCubic(Math.max(0f, Math.min(1f, (elapsed - delay) / 140f)));
            nvgSave(c);
            nvgGlobalAlpha(c, openT * reveal);
            drawRow(c, st, r, contentX(), r.y, lw, mx, my);
            nvgRestore(c);
        }
        Nvg.unScissor(c);
        scroll.renderBarNvg(c, st, contentX() + lw - 2, listY());
    }

    private void drawRow(long c, MemoryStack st, Row row, int x, int y, int w, int mx, int my) {
        Module m = row.m;
        boolean on = m.isEnabled();
        int rh = ROW();
        boolean hov = inList(mx, my) && inside(mx, my, x, y, w, rh);
        if (hov && expanded == null) tipModule = m;
        String cs = UICustom.cardStyle;

        // card body — Clean = flat strip + hairline, single accent edge when on
        //           Flat = solid · Glass = top-lit gradient · Neon = glass + accent edge
        float hf = hovFrac(m, hov);
        if ("Clean".equals(cs)) {
            // quiet strip — contrast comes from fill, not a border ring
            Nvg.round(c, st, x, y, w, rh, 10,
                Nvg.lerp(Nvg.fade(DS.surfaceAlt(), .32f),
                    Nvg.fade(DS.lighten(DS.surfaceAlt(), .10f), .95f), hf));
            // hairline under each row instead of a boxed card
            Nvg.rect(c, st, x + 12, y + rh - 1, w - 24, 1, 0x0AFFFFFF);
            if (on)
                Nvg.round(c, st, x + 1, y + rh * .16f, 3, rh * .68f, 1.5f, DS.cardAccent());
        } else if ("Flat".equals(cs)) {
            Nvg.round(c, st, x, y, w, rh, 10,
                hov ? DS.lighten(DS.surfaceAlt(), .07f) : Nvg.fade(DS.surfaceAlt(), .65f));
        } else {
            float lift = hov ? .05f : 0f;
            Nvg.vgrad(c, st, x, y, w, rh, 10,
                Nvg.fade(DS.lighten(DS.surfaceAlt(), .05f + lift), .85f),
                Nvg.fade(DS.surface(), .85f));
            Nvg.insetHi(c, st, x + 2, y, w - 4, 8, hov ? 0x1CFFFFFF : 0x0EFFFFFF);
            if ("Neon".equals(cs) && on)
                Nvg.borderGrad(c, st, x, y, w, rh, 10,
                    Nvg.fade(DS.accent(), .75f), 0x00FFFFFF, Nvg.fade(DS.accent(), .18f));
            else
                Nvg.roundB(c, st, x, y, w, rh, 10, 0x00FFFFFF,
                    hov ? UICustom.borderHover : DS.line());
        }
        // enabled state — accent edge bar + faint interior tint (Clean has its
        // own quieter edge inside the card branch above)
        if (on && !"Clean".equals(cs)) {
            Nvg.round(c, st, x, y, w, rh, 10, Nvg.fade(DS.cardAccent(), "Neon".equals(cs) ? 0.07f : 0.10f));
            Nvg.hgrad(c, st, x + 1.5f, y + rh * .22f, 3.5f, rh * .56f, 2,
                DS.cardAccent(), DS.accent2());
        }
        // keyboard selection outline (↑↓ navigate)
        if (keyboardSelection == m)
            Nvg.roundB(c, st, x + 1, y + 1, w - 2, rh - 2, 9, 0x00FFFFFF,
                Nvg.fade(DS.accent(), .55f));

        // icon chip — glyph on tinted tile (lit accent chip when on, well when off)
        float chip = 30, chx = x + 12, chy = y + rh / 2f - chip / 2;
        if ("Clean".equals(cs)) {
            // borderless tile; the accent lives in the glyph, not the chip fill
            Nvg.round(c, st, chx, chy, chip, chip, 8,
                Nvg.fade(DS.lighten(DS.surface(), .055f), .85f));
            drawIcon(c, st, m, chx + chip / 2, chy + chip / 2, 17,
                on ? DS.cardAccent() : Nvg.lerp(DS.secondary(), DS.text(), hf), hf);
        } else if (on) {
            Nvg.hgrad(c, st, chx, chy, chip, chip, 8,
                Nvg.fade(DS.cardAccent(), .9f), DS.accent2());
            Nvg.insetHi(c, st, chx + 1, chy, chip - 2, 7, 0x3AFFFFFF);
            drawIcon(c, st, m, chx + chip / 2, chy + chip / 2, 17, 0xFFFFFFFF, hf);
        } else {
            // readable tile: lifted fill + hairline border so glyphs sit in chips,
            // not floating loose against the card
            Nvg.round(c, st, chx, chy, chip, chip, 8, Nvg.fade(DS.lighten(DS.surface(), .045f), .9f));
            Nvg.roundB(c, st, chx, chy, chip, chip, 8, 0x00FFFFFF,
                hov ? UICustom.borderHover : DS.line());
            drawIcon(c, st, m, chx + chip / 2, chy + chip / 2, 17,
                hov ? DS.secondary() : DS.muted(), hf);
        }
        // toggle flash — brief accent wash across the row while the flip runs
        Long tg = togAt.get(m);
        if (tg != null && !UICustom.reducedMotion) {
            float tf = Math.min(1f, (System.currentTimeMillis() - tg) / 220f);
            if (tf < 1f)
                Nvg.round(c, st, x, y, w, rh, 10, Nvg.fade(DS.cardAccent(), .07f * (1f - tf)));
        }

        // name + description vertically centered as a block around row middle;
        // a pin dot shifts the block right when the module is footer-pinned
        boolean pinned = PINNED.contains(m.name);
        float nx = x + 52;
        if (pinned) {
            Nvg.circle(c, st, nx, y + rh / 2f, 2.4f,
                on ? DS.cardAccent() : Nvg.fade(DS.accent2(), .55f));
            nx += 10;
        }
        float textLimit = x + w - (m.keybind() > 0 ? 136 : 78);
        String name = truncN(c, "inter_sb", 13.5f, m.name, textLimit - nx - (m.premium ? 16 : 0));
        Nvg.text(c, st, "inter_sb", 13.5f, nx, y + rh / 2f - 9.5f, name,
            DS.text(),
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        Nvg.text(c, st, "inter", 10.5f, nx, y + rh / 2f + 9.5f,
            truncN(c, m.description, textLimit - nx), DS.secondary(),
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        if (m.premium) {
            float sx = nx + 6 + Nvg.textW(c, "inter_sb", 13.5f, name);
            Nvg.glyph(c, st, "star", sx, y + rh / 2f - 9.5f, 10, DS.warning());
        }

        // iOS toggle — gradient track + lit knob
        int tx = x + w - 48, ty = y + rh / 2 - 10;
        Nvg.toggle2(c, st, tx, ty, 36, 20, toggleFrac(m), DS.surface(),
            DS.cardAccent(), DS.accent2(), UICustom.glow);

        // mini keybind chip — mono, well-inset, left of the toggle (mockup)
        int kb = m.keybind();
        float chevX = x + w - 60;
        if (kb > 0) {
            String kn = InputConstants.Type.KEYBOARD.getOrCreate(kb)
                .getDisplayName().getString().toUpperCase(java.util.Locale.US);
            if (kn.length() > 6) kn = kn.substring(0, 6);
            float kw = Nvg.textW(c, "mono", 8.5f, kn) + 12;
            float kx = chevX - kw - 6;
            Nvg.well(c, st, kx, y + rh / 2f - 8, kw, 16, 6, Nvg.fade(DS.surface(), .8f));
            Nvg.text(c, st, "mono", 8.5f, kx + kw / 2, y + rh / 2f, kn,
                DS.secondary(), org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER
                    | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        }

        // chevron — always visible affordance (rotates up while expanded)
        if (!m.settings.isEmpty()) {
            float cyy = y + rh / 2f;
            boolean xp = expandFrac(m) > .5f;
            int cc = xp ? DS.secondary() : Nvg.lerp(DS.muted(), DS.secondary(), hf);
            if (xp) {
                Nvg.line(c, st, chevX - 4, cyy + 2, chevX, cyy - 3, 1.6f, cc);
                Nvg.line(c, st, chevX, cyy - 3, chevX + 4, cyy + 2, 1.6f, cc);
            } else {
                Nvg.line(c, st, chevX - 4, cyy - 2, chevX, cyy + 3, 1.6f, cc);
                Nvg.line(c, st, chevX, cyy + 3, chevX + 4, cyy - 2, 1.6f, cc);
            }
        }

        // settings child — inset darker card with top inner shade
        float ef = expandFrac(m);
        if (ef > 0) {
            int full = settingsFullH(m);
            int sh = (int) (ef * full);
            int ey = y + rh + 4;
            Nvg.clipPush(c, x, ey - 2, w, sh + 4);
            Nvg.well(c, st, x + 4, ey, w - 8, full, 10, Nvg.fade(DS.surface(), .58f));
            Nvg.line(c, st, x + 16, ey + 4, x + 16, ey + full - 4, 1f,
                Nvg.fade(DS.accent(), .28f));
            int sy = ey + 8;
            for (Setting<?> s : m.settings) {
                drawSetting(c, st, s, x + 24, sy, w - 48);
                if (m == expanded && sy < ey + sh)
                    settingRows.add(new SettingRow(m, s, x + 24, sy, w - 48, Math.min(settingH(s), ey + sh - sy)));
                sy += settingH(s) + 6;
            }
            Nvg.clipPop(c);
        }
    }

    /** Glyph with micro-motion: scale-pop on toggle (sin pulse, one shot) and
     *  a slight tilt while the row is hovered. Identity when reducedMotion. */
    private void drawIcon(long c, MemoryStack st, Module m, float cx, float cy,
                          float s, int argb, float hf) {
        if (UICustom.reducedMotion) { Nvg.glyph(c, st, iconOf(m), cx, cy, s, argb); return; }
        float pop = 0;
        Long at = togAt.get(m);
        if (at != null) {
            float tt = Math.min(1f, (System.currentTimeMillis() - at) / 170f);
            pop = (float) Math.sin(tt * Math.PI) * .26f;
        }
        float tilt = hf * .10f;
        if (pop > .005f || tilt > .005f) {
            org.lwjgl.nanovg.NanoVG.nvgSave(c);
            org.lwjgl.nanovg.NanoVG.nvgTranslate(c, cx, cy);
            org.lwjgl.nanovg.NanoVG.nvgRotate(c, tilt);
            org.lwjgl.nanovg.NanoVG.nvgScale(c, 1f + pop, 1f + pop);
            org.lwjgl.nanovg.NanoVG.nvgTranslate(c, -cx, -cy);
            Nvg.glyph(c, st, iconOf(m), cx, cy, s, argb);
            org.lwjgl.nanovg.NanoVG.nvgRestore(c);
        } else {
            Nvg.glyph(c, st, iconOf(m), cx, cy, s, argb);
        }
    }

    /** Module name → glyph icon (keyword map, category fallback). */
    private String iconOf(Module m) {
        String n = m.name.toLowerCase(java.util.Locale.ROOT);
        if (n.contains("bounty")) return "target";
        if (n.contains("scout") || n.contains("activity")) return "radar";
        if (n.contains("loot") || n.contains("chest") || n.contains("steal") || n.contains("stash")) return "chest";
        if (n.contains("entity")) return "person";
        if (n.contains("pearl")) return "pearl";
        if (n.contains("tnt") || n.contains("explo")) return "tnt";
        if (n.contains("coin") || n.contains("stonks") || n.contains("flip") || n.contains("bountyhunt")) return "coin";
        if (n.contains("bell") || n.contains("notify") || n.contains("remind") || n.contains("vote") || n.contains("daily")) return "bell";
        if (n.contains("pot") && !n.contains("spot") || n.contains("brew")) return "potion";
        if (n.contains("ghost") || n.contains("autolog")) return "ghost";
        if (n.contains("aura") || n.contains("aim") || n.contains("trigger")) return "crosshair";
        if (n.contains("kill") || n.contains("sword") || n.contains("wtap") || n.contains("crit")) return "sword";
        if (n.contains("speed") || n.contains("sprint") || n.contains("timer") || n.contains("zoom")) return "bolt";
        if (n.contains("velo") || n.contains("knock")) return "wings";
        if (n.contains("esp") || n.contains("finder") || n.contains("tracer") || n.contains("search") || n.contains("xray")) return "eye";
        if (n.contains("fly") || n.contains("elytra") || n.contains("jetpack") || n.contains("glide")) return "wings";
        if (n.contains("crystal") || n.contains("bed") || n.contains("anchor")) return "diamond";
        if (n.contains("totem") || n.contains("armor") || n.contains("protect")) return "shield";
        if (n.contains("chat") || n.contains("msg") || n.contains("spam")) return "chat";
        if (n.contains("spotify") || n.contains("music") || n.contains("lyric") || n.contains("song")) return "note";
        if (n.contains("staff") || n.contains("detect") || n.contains("radar") || n.contains("alert")) return "radar";
        if (n.contains("cape") || n.contains("skin") || n.contains("cosmet")) return "person";
        if (n.contains("money") || n.contains("price") || n.contains("shop") || n.contains("auction") || n.contains("bazaar")) return "coin";
        if (n.contains("stash") || n.contains("storage") || n.contains("base") || n.contains("shulker")) return "cube";
        if (n.contains("waypoint") || n.contains("map") || n.contains("coord") || n.contains("ore")) return "pin";
        if (n.contains("panic") || n.contains("unstuck")) return "flag";
        if (n.contains("stream") || n.contains("record") || n.contains("replay") || n.contains("screenshot") || n.contains("camera")) return "camera";
        if (n.contains("gift") || n.contains("drop") || n.contains("loot") || n.contains("farm")) return "gift";
        if (n.contains("sound") || n.contains("voice")) return "wave";
        if (n.contains("time") || n.contains("clock") || n.contains("schedule")) return "clock";
        if (n.contains("fire") || n.contains("flame") || n.contains("lava")) return "flame";
        if (n.contains("heart") || n.contains("health") || n.contains("heal")) return "heart";
        if (n.contains("rocket") || n.contains("launch") || n.contains("boost")) return "rocket";
        if (n.contains("friend") || n.contains("party") || n.contains("guild")) return "person";
        if (n.contains("stat") || n.contains("graph") || n.contains("track")) return "graph";
        if (n.contains("seed") || n.contains("world") || n.contains("biome")) return "globe";
        if (n.contains("key") || n.contains("alt") || n.contains("account")) return "key";
        if (n.contains("snow") || n.contains("winter") || n.contains("weather")) return "snow";
        if (n.contains("book") || n.contains("enchant") || n.contains("lore")) return "book";
        if (n.contains("magnet") || n.contains("vacuum") || n.contains("vac")) return "magnet";
        if (n.contains("kill") || n.contains("death")) return "skull";
        if (n.contains("emote") || n.contains("dance")) return "person";
        if (n.contains("pvp") || n.contains("coach")) return "sword";
        if (n.contains("sell") || n.contains("buy") || n.contains("trade")) return "diamond";
        if (n.contains("eat") || n.contains("food") || n.contains("hunger")) return "heart";
        if (n.contains("auto") || n.contains("macro")) return "bolt";
        return switch (m.category.label.toLowerCase(java.util.Locale.ROOT)) {
            case "combat" -> "crosshair";
            case "movement" -> "bolt";
            case "render" -> "eye";
            case "player" -> "person";
            case "donutsmp" -> "donut";
            default -> "star";
        };
    }

    private void drawSetting(long c, MemoryStack st, Setting<?> s, int x, int y, int w) {
        float mid = y + settingH(s) / 2f;
        if (s instanceof Setting.Num n) {
            // label + mono value on one line, recessed slider under it
            Nvg.text(c, st, "inter", 11.5f, x, y + 5, s.name, DS.secondary(),
                org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
            double fr = (n.get() - n.min) / (n.max - n.min);
            int tw = w - 64; float ty = y + 18;
            Nvg.well(c, st, x, ty - 1, tw, 7, 3.5f, Nvg.fade(DS.surface(), .9f));
            if (fr > 0.001f)
                Nvg.hgrad(c, st, x, ty - 1, tw * (float) fr, 7, 3.5f, DS.cardAccent(), DS.accent2());
            Nvg.shadow(c, st, x + tw * (float) fr - 4.5f, ty - 4, 9, 9, 4.5f, 3, 0x40000000);
            Nvg.knob(c, st, x + tw * (float) fr, ty + 2.5f, 4.5f);
            String vs = n.get() == n.get().longValue() ? "" + n.get().longValue()
                : String.format(java.util.Locale.US, "%.2f", n.get());
            Nvg.text(c, st, "mono", 11, x + w, y + 5, vs, DS.cardAccent(),
                org.lwjgl.nanovg.NanoVG.NVG_ALIGN_RIGHT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
            return;
        }
        if (s instanceof Setting.Mode mo) {
            drawMode(c, st, mo, x, mid, w);
            return;
        }
        Nvg.text(c, st, "inter", 11.5f, x, mid, s.name, DS.secondary(),
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        if (s instanceof Setting.Bool b) {
            Nvg.check(c, st, x + Nvg.textW(c, "inter", 11.5f, s.name) + 10, mid - 8, 16,
                b.on(), DS.cardAccent(), DS.surface(), DS.border());
        } else if (s instanceof Setting.Col cc) {
            Nvg.round(c, st, x + w - 48, mid - 8, 42, 16, 5, cc.get());
            Nvg.insetHi(c, st, x + w - 47, mid - 8, 40, 4, 0x33FFFFFF);
            Nvg.roundB(c, st, x + w - 48, mid - 8, 42, 16, 5, 0x00FFFFFF, DS.border());
        } else if (s instanceof Setting.Bind b) {
            String txt = b.get() == -2 ? "press…" : b.get() < 0 ? "none" : "key " + b.get();
            float pw = Nvg.textW(c, "mono", 10.5f, txt) + 14;
            if (b.get() == -2)
                Nvg.hgrad(c, st, x + w - pw, mid - 9, pw, 18, 9, DS.cardAccent(), DS.accent2());
            else
                Nvg.roundB(c, st, x + w - pw, mid - 9, pw, 18, 9,
                    Nvg.fade(DS.lighten(DS.surface(), .08f), .9f), DS.border());
            Nvg.text(c, st, "mono", 10.5f, x + w - pw / 2, mid, txt,
                b.get() == -2 ? 0xFFFFFFFF : DS.secondary(),
                org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        } else if (s instanceof Setting.Str sstr) {
            boolean foc = sstr == focusedStr;
            String v = sstr.get().isEmpty() ? "…" : sstr.get();
            float tw2 = Math.min(150, Math.max(64, Nvg.textW(c, "inter", 10.5f, v) + 16));
            float fx = x + w - tw2, fy = mid - 9;
            ctrlRects.put(s, new float[][]{{fx, fy, tw2, 18}});
            Nvg.roundB(c, st, fx, fy, tw2, 18, 6,
                foc ? Nvg.fade(DS.lighten(DS.surface(), .08f), .95f) : Nvg.fade(DS.lighten(DS.surface(), .08f), .9f),
                foc ? Nvg.fade(DS.cardAccent(), .6f) : DS.border());
            if (foc)
                Nvg.borderGrad(c, st, fx, fy, tw2, 18, 6,
                    DS.cardAccent(), Nvg.fade(DS.cardAccent(), .6f), DS.accent2());
            Nvg.text(c, st, "inter", 10.5f, fx + 7, mid, truncN(c, v, tw2 - 14),
                foc ? DS.text() : DS.secondary(),
                org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
            if (foc && System.currentTimeMillis() % 1000 < 550)
                Nvg.rect(c, st, fx + 8 + Nvg.textW(c, "inter", 10.5f, sstr.get()),
                    mid - 7, 1.2f, 14, DS.cardAccent());
        }
    }

    private void drawMode(long c, MemoryStack st, Setting.Mode mode, int x, float mid, int w) {
        float limit = Math.max(0, w - 16 - Nvg.textW(c, "inter", 11.5f, mode.name));
        float total = 0;
        for (String option : mode.modes) total += Nvg.textW(c, "inter", 10.5f, option) + 18;
        if (total > limit && mode.modes.length > 1) {
            float pw = Math.min(w * .62f, Math.max(42, Nvg.textW(c, "inter", 10.5f, mode.get()) + 33));
            float left = x + w - pw;
            Nvg.text(c, st, "inter", 11.5f, x, mid,
                truncN(c, "inter", 11.5f, mode.name, w - pw - 8), DS.secondary(), NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            Nvg.roundB(c, st, left, mid - 9, pw, 18, 9, Nvg.fade(DS.accent(), .16f), DS.line());
            Nvg.text(c, st, "inter", 10.5f, left + 8, mid,
                truncN(c, mode.get(), pw - 25), DS.text(), NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            float cx = x + w - 9;
            Nvg.line(c, st, cx - 2, mid - 3, cx + 1, mid, 1.25f, DS.secondary());
            Nvg.line(c, st, cx + 1, mid, cx - 2, mid + 3, 1.25f, DS.secondary());
            ctrlRects.put(mode, new float[][]{{left, mid - 9, pw, 18}});
            return;
        }

        Nvg.text(c, st, "inter", 11.5f, x, mid,
            truncN(c, "inter", 11.5f, mode.name, w - Math.min(total, w * .62f) - 8),
            DS.secondary(), NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        float right = x + w - 4;
        float[][] rects = new float[mode.modes.length][];
        for (int i = mode.modes.length - 1; i >= 0; i--) {
            String option = mode.modes[i];
            float pw = Math.min(w * .62f, Nvg.textW(c, "inter", 10.5f, option) + 14);
            float left = right - pw;
            rects[i] = new float[]{left, mid - 9, pw, 18};
            boolean active = mode.get().equals(option);
            if (active) Nvg.hgrad(c, st, left, mid - 9, pw, 18, 9, DS.cardAccent(), DS.accent2());
            else Nvg.roundB(c, st, left, mid - 9, pw, 18, 9, DS.surfaceAlt(), DS.line());
            Nvg.text(c, st, "inter", 10.5f, left + pw / 2, mid, truncN(c, option, pw - 10),
                active ? 0xFFFFFFFF : DS.secondary(), NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
            right = left - 4;
        }
        ctrlRects.put(mode, rects);
    }

    /** First-open intro — dark veil + spinning black-hole mark + "loading N
     *  modules" strip that fades out over INTRO_MS. Click/ESC skips. Once per
     *  session; reducedMotion skips it entirely. */
    private void drawIntro(long c, MemoryStack st) {
        if (UICustom.reducedMotion) { introDone = true; return; }
        long el = System.currentTimeMillis() - introAt;
        float p = Math.min(1f, el / (float) INTRO_MS);
        if (p >= 1f) { introDone = true; return; }
        float a = 1f - easeOutCubic(p);
        // veil — slightly darker than the GUI backdrop, whole screen
        Nvg.rect(c, st, 0, 0, width, height, Nvg.fade(0xFF07080C, .92f * a));
        float cx = width / 2f, cy = height / 2f - 24;
        // accretion ring — same tilted-ellipse mark as the titlebar, spinning
        float rot = el / 420f;
        org.lwjgl.nanovg.NanoVG.nvgSave(c);
        org.lwjgl.nanovg.NanoVG.nvgTranslate(c, cx, cy);
        org.lwjgl.nanovg.NanoVG.nvgScale(c, 1f, .42f);
        Nvg.ring(c, st, 0, 0, 30, 2.4f, Nvg.fade(DS.accent2(), .8f * a));
        org.lwjgl.nanovg.NanoVG.nvgBeginPath(c);
        org.lwjgl.nanovg.NanoVG.nvgArc(c, 0, 0, 30, rot, rot + 2.4f,
            org.lwjgl.nanovg.NanoVG.NVG_CW);
        org.lwjgl.nanovg.NanoVG.nvgStrokeColor(c, Nvg.col(st, Nvg.fade(DS.accent(), .95f * a)));
        org.lwjgl.nanovg.NanoVG.nvgStrokeWidth(c, 3.6f);
        org.lwjgl.nanovg.NanoVG.nvgLineCap(c, org.lwjgl.nanovg.NanoVG.NVG_ROUND);
        org.lwjgl.nanovg.NanoVG.nvgStroke(c);
        org.lwjgl.nanovg.NanoVG.nvgRestore(c);
        Nvg.circle(c, st, cx, cy, 16, Nvg.fade(0xFF05060A, a));
        Nvg.arc(c, st, cx, cy - 1.4f, 11, 2.4f, rot * .8f, rot * .8f + 3.4f,
            Nvg.fade(DS.accent(), .9f * a));
        Nvg.textL(c, st, "inter_b", 22, cx, cy + 34, "N O X",
            Nvg.fade(DS.text(), a), 3.4f,
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_TOP);
        int nmods = NoxClient.INSTANCE.modules.all().size();
        Nvg.text(c, st, "mono", 10.5f, cx, cy + 66, "loading " + nmods + " modules",
            Nvg.fade(DS.muted(), a),
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_TOP);
        // progress strip
        float bw = 120, bx = cx - bw / 2, by = cy + 88;
        Nvg.well(c, st, bx, by, bw, 4, 2, Nvg.fade(DS.surface(), .9f * a));
        Nvg.hgrad(c, st, bx, by, bw * p, 4, 2,
            Nvg.fade(DS.accent(), a), Nvg.fade(DS.accent2(), a));
    }

    /* ---------- live stats ---------- */
    private void statsPanel(long c, MemoryStack st) {
        int sx = contentX() + listW() + GAP, sw = STATS_W;
        int sy = listY(), sh = listH();
        Nvg.vgrad(c, st, sx, sy, sw, sh, 10,
            Nvg.fade(DS.lighten(DS.surfaceAlt(), .04f), .8f), Nvg.fade(DS.surface(), .8f));
        Nvg.roundB(c, st, sx, sy, sw, sh, 10, 0x00FFFFFF, DS.line());
        Nvg.insetHi(c, st, sx + 2, sy, sw - 4, 8, 0x10FFFFFF);
        float px = sx + 14, py = sy + 13;
        Nvg.textL(c, st, "inter_b", 9, px, py, "LIVE STATS", DS.cardAccent(), 1.8f,
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_TOP);
        Nvg.line(c, st, px, py + 13, px + Nvg.textLW(c, "inter_b", 9, "LIVE STATS", 1.8f), py + 13,
            1f, Nvg.fade(DS.cardAccent(), .5f));
        py += 24;

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
    /** Chip hitboxes captured during draw — [x, w, pinnedIndex]. */
    private final List<float[]> pinRects = new ArrayList<>();
    private void pinnedBar(long c, MemoryStack st, int mx, int my) {
        int fy = winY + winH - FOOT;
        Nvg.rect(c, st, winX + 1, fy, winW - 2, 1, DS.line());
        Nvg.insetHi(c, st, winX + 2, fy, winW - 4, 6, 0x0BFFFFFF);
        float py = fy + 9;
        int active = 0;
        for (Module m : NoxClient.INSTANCE.modules.all()) if (m.isEnabled()) active++;
        Nvg.textL(c, st, "inter_b", 8.5f, contentX(), py + 13, "ACTIVE", DS.muted(), 1.2f,
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        float ax = contentX() + Nvg.textLW(c, "inter_b", 8.5f, "ACTIVE", 1.2f) + 6;
        Nvg.text(c, st, "mono", 9, ax, py + 13, String.valueOf(active), DS.cardAccent(),
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        float x = ax + Nvg.textW(c, "mono", 9, String.valueOf(active)) + 18;
        float maxPinW = (winX + winW - 32 - x - 4 * 6) / 5;
        if (maxPinW < 20) return;
        for (int i = 0; i < 5; i++) {
            Module m = i < PINNED.size() ? NoxClient.INSTANCE.modules.get(PINNED.get(i)) : null;
            float pw = Math.min(maxPinW, m != null ? Nvg.textW(c, "inter_sb", 11, m.name) + 34 : 26);
            pinRects.add(new float[]{x, pw, i});
            boolean hov = mx >= x && mx < x + pw && my >= py && my < py + 26;
            boolean on = m != null && m.isEnabled();
            Nvg.well(c, st, x, py, pw, 26, 13,
                hov ? DS.lighten(DS.surface(), .08f) : Nvg.fade(DS.surface(), .55f));
            if (on)
                Nvg.borderGrad(c, st, x, py, pw, 26, 13,
                    Nvg.fade(DS.cardAccent(), .5f), 0x00FFFFFF, Nvg.fade(DS.cardAccent(), .15f));
            if (m != null) {
                Nvg.circle(c, st, x + 8, py + 13, 2.6f, on ? DS.cardAccent() : DS.muted());
                Nvg.text(c, st, "inter_sb", 11, x + 14, py + 13,
                    truncN(c, "inter_sb", 11, m.name, pw - 20),
                    on ? DS.text() : DS.secondary(),
                    org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
                if (hov) tipText = m.name + " / right click to unpin";
            } else {
                float cxx = x + pw / 2, cyy = py + 13;
                Nvg.line(c, st, cxx - 4, cyy, cxx + 4, cyy, 1.6f, DS.muted());
                Nvg.line(c, st, cxx, cyy - 4, cxx, cyy + 4, 1.6f, DS.muted());
                if (hov && tipModule == null && tipText == null)
                    tipText = "Pin slot — click, then pick a module";
            }
            x += pw + 6;
        }
    }

    /* ---------- palette ---------- */
    private void drawPalette(long c, MemoryStack st) {
        Nvg.rect(c, st, 0, 0, width, height, 0x88000000);
        int pw = 340, px = (width - pw) / 2, py = height / 5;
        Nvg.shadow(c, st, px, py + 4, pw, 300, 12, 22, 0x70000000);
        Nvg.shadow(c, st, px + 6, py + 8, pw - 12, 296, 12, 26, Nvg.fade(DS.accent(), 0.14f));
        Nvg.vgrad(c, st, px, py, pw, 46, 12, DS.lighten(DS.surfaceAlt(), .05f), DS.surface());
        Nvg.borderGrad(c, st, px, py, pw, 46, 12, DS.cardAccent(), Nvg.fade(DS.cardAccent(), .6f), DS.accent2());
        Nvg.insetHi(c, st, px + 2, py, pw - 4, 10, 0x20FFFFFF);
        Nvg.ring(c, st, px + 16, py + 22, 4.5f, 1.6f, DS.accent());
        Nvg.line(c, st, px + 19.4f, py + 25.6f, px + 23, py + 29.4f, 1.6f, DS.accent());
        Nvg.text(c, st, "inter", 12, px + 30, py + 23,
            palInput.value.isEmpty() ? "Type a module name…" : palInput.value,
            palInput.value.isEmpty() ? DS.muted() : DS.text(),
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        if (System.currentTimeMillis() % 1000 < 550)
            Nvg.rect(c, st, px + 30 + Nvg.textW(c, "inter", 12, palInput.value), py + 12, 1.4f, 22, DS.accent());
        if (pinPick)
            Nvg.text(c, st, "inter_b", 9, px + pw - 10, py + 23, "click = pin · ESC done",
                DS.accent2(),
                org.lwjgl.nanovg.NanoVG.NVG_ALIGN_RIGHT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);

        List<Module> hits = palHits();
        float ly = py + 54;
        for (int i = 0; i < Math.min(8, hits.size()); i++) {
            Module m = hits.get(i);
            boolean sel = i == palSel;
            boolean pinned = PINNED.contains(m.name);
            Nvg.round(c, st, px, ly, pw, 30, 8,
                sel ? Nvg.fade(DS.accent(), 0.35f) : Nvg.fade(DS.surface(), 0.9f));
            if (pinPick)
                Nvg.circle(c, st, px + 12, ly + 15, 3f,
                    pinned ? DS.cardAccent() : 0x33FFFFFF);
            Nvg.text(c, st, "inter_sb", 11, px + (pinPick ? 24 : 12), ly + 15, m.name,
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
        return truncN(c, "inter", 10.5f, s, w);
    }

    private String truncN(long c, String font, float size, String s, float w) {
        if (w <= 0 || s.isEmpty()) return "";
        if (Nvg.textW(c, font, size, s) <= w) return s;
        String dot = "\u2026";
        if (Nvg.textW(c, font, size, dot) > w) return "";
        int end = s.length();
        do { end = s.offsetByCodePoints(end, -1); }
        while (end > 0 && Nvg.textW(c, font, size, s.substring(0, end) + dot) > w);
        return s.substring(0, end) + dot;
    }

    /* ================= input ================= */

    /** 26.3 delivers RAW SDL buttons (1=LMB, 2=MMB, 3=RMB) — normalize to the
     * GLFW-style 0/1/2 convention the click logic below was written in. */
    private static int normButton(int sdl) {
        return switch (sdl) { case 1 -> 0; case 3 -> 1; case 2 -> 2; default -> sdl; };
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        double mx = e.x(), my = e.y(); int btn = normButton(e.button());
        // any click that isn't into a Str field releases text focus — the Str
        // branch below re-arms it when the click lands inside its field.
        // Without this, typing kept writing into a field you had long left
        focusedStr = null;
        if (!introDone) { introDone = true; return true; }
        if (palette) {
            List<Module> hits = palHits();
            int pw = 340, px = (width - pw) / 2, py = height / 5 + 54;
            for (int i = 0; i < Math.min(8, hits.size()); i++)
                if (mx >= px && mx < px + pw && my >= py + i * 32 && my < py + i * 32 + 30) {
                    if (pinPick) {   // multi-choice pin picker — stays open
                        Module m = hits.get(i);
                        if (PINNED.contains(m.name)) PINNED.remove(m.name);
                        else if (PINNED.size() < 5) PINNED.add(m.name);
                        return true;
                    }
                    toggleModule(hits.get(i));
                    palette = false; return true;
                }
            palette = false; pinPick = false; return true;
        }
        if (btn == 0) for (float[] b : titleBtns) {
            if (!inside(mx, my, b[0], b[1], b[2], b[3])) continue;
            switch ((int) b[4]) {
                case 0 -> {
                    if (System.currentTimeMillis() - panicArmedAt < 2500) {
                        Module p = NoxClient.INSTANCE.modules.get("Panic");
                        if (p != null) p.setEnabled(true);
                        panicArmedAt = 0;
                    } else panicArmedAt = System.currentTimeMillis();
                }
                case 1 -> minecraft.setScreenAndShow(new ShopScreen(this));
                case 2 -> acInfo = !acInfo;
                case 3 -> {
                    UICustom.selectClickStyle("Cards");
                    minecraft.setScreenAndShow(new NoxWindowScreen());
                }
                case 4 -> onClose();
                default -> { }
            }
            return true;
        }
        if (acInfo) {
            float[] a = acPanelRect;
            if (a != null && inside(mx, my, a[0], a[1], a[2], a[3])) return true;
            acInfo = false;
        }
        if (btn == 0 && inside(mx, my, winX, winY, winW, TITLE)) {
            if (dbl) {
                winX = (width - winW) / 2; winY = (height - winH) / 2;
                return true;
            }
            dragging = true; dragDX = (int) mx - winX; dragDY = (int) my - winY; return true;
        }
        if (btn == 0 && inside(mx, my, winX + winW - 24, winY + winH - 24, 24, 24)) {
            resizing = true; return true;
        }
        // left rail — hitboxes recorded by the draw pass [x,y,w,h,action]
        if (btn == 0) for (float[] r : railRects) {
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                search.focused = false;
                if (r[4] == -2f) { designTab = true; dScroll.scroll = dScroll.target = 0;
                    listAt = System.currentTimeMillis(); return true; }
                if (r[4] == -3f) {
                    minecraft.setScreenAndShow(new HudEditorScreen(this)); return true; }
                int k = (int) r[4] + 1;
                tab = k == 0 ? null : Category.values()[k - 1];
                designTab = false;
                NoxWindowScreen.activeTab = Math.max(0, k - 1);
                resetListPosition();
                listAt = System.currentTimeMillis(); return true;
            }
        }
        if (!designTab && btn == 0 && !search.value.isEmpty()
            && inside(mx, my, search.x + search.w - 30, search.y, 30, search.h)) {
            setQuery(""); search.focused = true; return true;
        }
        search.focused = !designTab && btn == 0 && inside(mx, my, search.x, search.y, search.w, search.h);
        if (search.focused) return true;
        if (designTab) {
            if (mx >= contentX() && mx < contentX() + listW()
                && my >= listY() && my < listY() + listH()) {
                designHit(mx, my, btn); return true;
            }
            return true;
        }
        boolean inList = inList(mx, my);
        if (inList && btn == 0 && settingHit(mx, my)) return true;
        if (inList) for (Row r : rows) {
            if (mx >= contentX() && mx < contentX() + listW() && my >= r.y && my < r.y + ROW()) {
                if (btn == 2) {   // MMB → pin
                    if (!PINNED.contains(r.m.name) && PINNED.size() < 5) PINNED.add(r.m.name);
                    else PINNED.remove(r.m.name); return true;
                }
                if (btn == 1) {   // RMB → settings accordion (mockup semantics)
                    if (!r.m.settings.isEmpty()) setExpanded(r.m);
                    return true;
                }
                // chevron affordance — explicit expand target left of the toggle
                if (btn == 0 && !r.m.settings.isEmpty()
                    && mx >= contentX() + listW() - 76 && mx < contentX() + listW() - 48) {
                    setExpanded(r.m); return true;
                }
                if (btn == 0) {   // LMB → toggle the module
                    toggleModule(r.m); return true;
                }
            }
        }
        int fy = winY + winH - FOOT + 9;
        if (UICustom.showPinned) for (float[] pr : pinRects) {
            if (mx >= pr[0] && mx < pr[0] + pr[1] && my >= fy && my < fy + 26) {
                int k = (int) pr[2];
                Module m = k < PINNED.size() ? NoxClient.INSTANCE.modules.get(PINNED.get(k)) : null;
                if (m != null) {
                    if (btn == 1) PINNED.remove(k);   // RMB → unpin
                    else toggleModule(m);              // LMB → toggle
                } else if (btn == 0) { palette = true; pinPick = true;
                    palInput.value = ""; palSel = 0; }   // empty slot → multi picker
                return true;
            }
        }
        return super.mouseClicked(e, dbl);
    }

    private boolean settingHit(double mx, double my) {
        for (SettingRow sr : settingRows) {
            if (sr.m == expanded && inside(mx, my, sr.x, sr.y, sr.w, sr.h))
                return hitOne(sr.s, sr.x, sr.w, mx, my, sr.y, sr.h);
        }
        return false;
    }

    private boolean designHit(double mx, double my, int btn) {
        int x = contentX() + 12, w = listW() - 24;
        for (DItem it : dRows) {
            if (it.o instanceof PresetRow pr) {
                if (my < it.y || my >= it.y + it.h) continue;
                presetClick(pr, btn);
                return true;
            }
            if (!(it.o instanceof Setting<?> s)) continue;
            int sh = it.h - 4, ry = it.y + 2;
            if (my < ry || my >= ry + sh) continue;
            return btn == 0 && hitOne(s, x, w, mx, my, ry, sh);
        }
        return false;
    }

    /** LMB loads the preset · MMB arms the keybind capture · "+save" writes
     *  the current module state to a new preset file. */
    private void presetClick(PresetRow pr, int btn) {
        if (pr.name() == null) {
            String n = "preset" + (NoxClient.INSTANCE.config.presets().size() + 1);
            NoxClient.INSTANCE.config.savePreset(n);
            design = null;   // rebuild item list so the new file shows up
            return;
        }
        if (btn == 2) { presetBindFor = pr.name(); return; }   // MMB = arm keybind
        NoxClient.INSTANCE.config.loadPreset(pr.name());
    }

    /** SDL keycode → short display name for preset chips. */
    private static String keyName(int k) {
        return switch (k) {
            case 41 -> "ESC"; case 40 -> "RET"; case 42 -> "BKSP"; case 43 -> "TAB";
            case 44 -> "SPC"; case 79 -> "RIGHT"; case 80 -> "LEFT";
            case 81 -> "DOWN"; case 82 -> "UP"; case 225 -> "LSHIFT"; case 229 -> "RSHIFT";
            case 224 -> "LCTRL"; case 228 -> "RCTRL"; case 226 -> "LALT"; case 230 -> "RALT";
            default -> k >= 58 && k <= 69 ? "F" + (k - 57)
                : Character.toString((char) k).toUpperCase(java.util.Locale.ROOT);
        };
    }

    /** Widget click dispatch shared by accordion settings + design page. */
    private boolean hitOne(Setting<?> s, int x, int w, double mx, double my, int ry, int sh) {
        if (s instanceof Setting.Bool b) { b.set(!b.on()); return true; }
        if (s instanceof Setting.Num n) {
            dragNum = n; dragNumX = x; dragNumW = w;
            dragNumTo(n, x, w, mx); return true;
        }
        if (s instanceof Setting.Mode mo) {
            float[][] rects = ctrlRects.get(s);
            if (rects != null)
                for (int k = 0; k < rects.length; k++)
                    if (mx >= rects[k][0] && mx < rects[k][0] + rects[k][2]
                        && my >= rects[k][1] && my < rects[k][1] + rects[k][3]) {
                        int selected = k;
                        if (rects.length == 1 && mo.modes.length > 1)
                            selected = (java.util.Arrays.asList(mo.modes).indexOf(mo.get()) + 1) % mo.modes.length;
                        mo.set(mo.modes[selected]); return true;
                    }
            return true;
        }
        if (s instanceof Setting.Col cc) {
            int[] pal = {0xFFFF6EC7, 0xFF7A2BE2, 0xFF5AC8FF, 0xFF4ADE80, 0xFFFBBF24, 0xFFF87171,
                0xFF0F0F12, 0xFF1A1A1F, 0xFF232329, 0xFFE8E8EC};
            cc.set(pal[(indexOf(pal, cc.get()) + 1) % pal.length]); return true;
        }
        if (s instanceof Setting.Bind b) { b.set(-2); return true; }
        if (s instanceof Setting.Str ss) {
            float[][] rects = ctrlRects.get(s);
            boolean inField = rects != null && mx >= rects[0][0] && mx < rects[0][0] + rects[0][2]
                && my >= rects[0][1] && my < rects[0][1] + rects[0][3];
            focusedStr = inField ? ss : null;
            return true;
        }
        focusedStr = null;
        return true;
    }

    private void dragNumTo(Setting.Num n, int x, int w, double mx) {
        double fr = (mx - x) / Math.max(1, w - 64);
        n.set(n.min + Math.max(0, Math.min(1, fr)) * (n.max - n.min));
    }

    private int indexOf(int[] a, int v) { for (int i = 0; i < a.length; i++) if (a[i] == v) return i; return a.length - 1; }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (normButton(e.button()) == 0) {
            dragging = resizing = false;
            dragNum = null;
        }
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragging) {
            winX = (int) e.x() - dragDX; winY = (int) e.y() - dragDY; return true;
        }
        if (resizing) {
            int maxW = Math.max(1, width - winX - 8), maxH = Math.max(1, height - winY - 8);
            winW = Math.min(maxW, Math.max(560, (int) e.x() - winX + 8));
            winH = Math.min(maxH, Math.max(320, (int) e.y() - winY + 8));
            return true;
        }
        if (dragNum != null) {
            dragNumTo(dragNum, dragNumX, dragNumW, e.x()); return true;
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
        if (!introDone) { introDone = true; return true; }
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
        if (focusedStr != null) {
            if (key == InputConstants.KEY_BACKSPACE && !focusedStr.get().isEmpty())
                focusedStr.set(focusedStr.get().substring(0, focusedStr.get().length() - 1));
            if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RETURN)
                focusedStr = null;
            return true;
        }
        if (search.focused) {
            if (key == InputConstants.KEY_BACKSPACE) {
                if (!search.value.isEmpty())
                    setQuery(search.value.substring(0, search.value.offsetByCodePoints(search.value.length(), -1)));
                return true;
            }
            if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RETURN) {
                search.focused = false;
                return true;
            }
            if (key != InputConstants.KEY_UP && key != InputConstants.KEY_DOWN) return true;
            search.focused = false;
        }
        if (expanded != null)
            for (Setting<?> s : expanded.settings)
                if (s instanceof Setting.Bind b && b.get() == -2) {
                    b.set(key == InputConstants.KEY_ESCAPE ? -1 : key); return true;
                }
        if (presetBindFor != null) {
            var keys = NoxClient.INSTANCE.config.presetKeys;
            keys.values().removeIf(presetBindFor::equals);
            if (key != InputConstants.KEY_ESCAPE) keys.put(key, presetBindFor);
            presetBindFor = null;
            NoxClient.INSTANCE.config.save();
            return true;
        }
        if (designTab)
            for (Object o : designItems())
                if (o instanceof Setting.Bind b && b.get() == -2) {
                    b.set(key == InputConstants.KEY_ESCAPE ? -1 : key); return true;
                }
        // keyboard navigation — ↑↓ select, Enter toggles, ←/→ collapse/expand
        if (!designTab && !search.focused) {
            List<Module> fm = filtered();
            if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_UP) {
                if (!fm.isEmpty()) {
                    boolean down = key == InputConstants.KEY_DOWN;
                    selIdx = selIdx < 0 ? (down ? 0 : fm.size() - 1)
                        : Math.floorMod(selIdx + (down ? 1 : -1), fm.size());
                    Module sm = fm.get(selIdx);
                    for (Row r : rows)
                        if (r.m == sm) {
                            float top = r.y + scrollOff - listY();
                            if (top < scroll.scroll) scroll.target = top;
                            else if (top + ROW() > scroll.scroll + listH())
                                scroll.target = top + ROW() - listH();
                            break;
                        }
                }
                return true;
            }
            if (selIdx >= 0 && selIdx < fm.size()) {
                Module sm = fm.get(selIdx);
                if (key == InputConstants.KEY_RETURN) { toggleModule(sm); return true; }
                if (key == InputConstants.KEY_RIGHT && !sm.settings.isEmpty()) {
                    setExpanded(sm); return true;
                }
                if (key == InputConstants.KEY_LEFT && expanded == sm) {
                    setExpanded(sm); return true;
                }
            }
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
        if (focusedStr != null) {
            if (e.isAllowedChatCharacter()) focusedStr.set(focusedStr.get() + e.codepointAsString());
            return true;
        }
        if (!designTab && e.isAllowedChatCharacter()) {
            search.focused = true;
            if (search.value.length() < search.maxLen) setQuery(search.value + e.codepointAsString());
            return true;
        }
        return super.charTyped(e);
    }

    @Override
    public void onClose() {
        NoxWindowScreen.winX = winX; NoxWindowScreen.winY = winY;
        NoxWindowScreen.winW = winW; NoxWindowScreen.winH = winH;
        NoxClient.guiClosedAt = System.currentTimeMillis();
        NoxClient.INSTANCE.config.save();
        super.onClose();
    }
    @Override public boolean isPauseScreen() { return false; }
}
