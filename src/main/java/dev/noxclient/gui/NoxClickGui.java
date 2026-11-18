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
        STATS_W = 236, GAP = 24;

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

    private int dragDX, dragDY;
    private boolean dragging, resizing;
    private Category tab = null;
    private final Overlays.Scrollable scroll = new Overlays.Scrollable(0);
    private final Inputs.Input search = new Inputs.Input(0, 0, 0, "search modules…");

    private Module expanded;
    private final Map<Module, Long> expAt = new HashMap<>();
    private final Map<Module, Long> togAt = new HashMap<>();
    private Setting.Num dragNum;

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
        for (Setting<?> ignored : m.settings) h += 30;
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
        float t = Math.min(1f, (System.currentTimeMillis() - openAt) / (200f / animMul()));
        openT = easeOutCubic(t);

        // layout pass (scroll tick, row positions) — draw params the NVG op reads
        scroll.viewH = listH();
        scrollOff = scroll.tick(dt);
        rows.clear(); settingRows.clear(); dRows.clear(); ctrlRects.clear();
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
                int rh = ROW() + sh;
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
        Nvg.fontScale = UICustom.fontScale * UICustom.textScale();
        float wr = winR();
        float cx = winX + winW / 2f, cy = winY + winH / 2f;
        float sc = 0.96f + 0.04f * openT;
        org.lwjgl.nanovg.NanoVG.nvgTranslate(c, cx, cy);
        org.lwjgl.nanovg.NanoVG.nvgScale(c, sc, sc);
        org.lwjgl.nanovg.NanoVG.nvgTranslate(c, -cx, -cy);

        // window shell — accent halo + deep shadow + obsidian gradient body
        float bodyA = switch (UICustom.winStyle) {
            case "Sky" -> UICustom.winOpacity * 0.55f;
            case "Glass" -> UICustom.winOpacity * 0.82f;
            default -> UICustom.winOpacity;
        } * openT;
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

        titlebar(c, st);
        tabs(c, st, mx, my);
        searchBar(c, st, mx, my);
        if (designTab) designPage(c, st);
        else moduleList(c, st, mx, my);
        if (hasStats()) statsPanel(c, st);
        if (UICustom.showPinned) pinnedBar(c, st, mx, my);

        // resize grip — three diagonal ticks in the bottom-right corner
        boolean rhov = resizing || (mx >= winX + winW - 24 && my >= winY + winH - 24);
        int grip = rhov ? Nvg.fade(DS.accent(), .85f) : 0x40FFFFFF;
        for (int k = 0; k < 3; k++) {
            float gx = winX + winW - 7 - k * 5, gy = winY + winH - 7;
            Nvg.line(c, st, gx, gy, gx + (k + 1) * 5 - 4, gy - (k + 1) * 5 + 4, 1.4f, grip);
        }

        seasonalFx(c, st);   // flakes drift over content; snow piles on the top edge

        org.lwjgl.nanovg.NanoVG.nvgResetTransform(c);
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
        return design;
    }

    private void designPage(long c, MemoryStack st) {
        int lw = listW();
        Nvg.scissor(c, contentX() - 4, listY() - 2, lw + 8, listH() + 4);
        for (DItem it : dRows) {
            if (it.o instanceof String hdr) {
                Nvg.textL(c, st, "inter_b", 9, contentX() + 4, it.y + it.h / 2f,
                    hdr.toUpperCase(java.util.Locale.US), DS.cardAccent(), 1.6f,
                    org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
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
    private void titlebar(long c, MemoryStack st) {
        // chrome strip — darker vertical gradient + bottom hairline
        Nvg.scissor(c, winX, winY, winW, TITLE);
        Nvg.vgrad(c, st, winX, winY, winW, TITLE, 0,
            Nvg.fade(DS.lighten(DS.header(), .05f), openT), Nvg.fade(DS.header(), openT));
        // sheen sweep — a diagonal light band crossing every ~6s (reduced-motion off)
        if (!UICustom.reducedMotion) {
            float t = (System.currentTimeMillis() % 6000L) / 6000f;
            float sx = winX - winW * .2f + t * winW * 1.4f;
            org.lwjgl.nanovg.NVGPaint p = org.lwjgl.nanovg.NanoVG.nvgLinearGradient(c,
                sx - 60, winY, sx + 60, winY + TITLE,
                Nvg.col(st, 0x00FFFFFF), Nvg.col(st, 0x09FFFFFF), org.lwjgl.nanovg.NVGPaint.malloc(st));
            org.lwjgl.nanovg.NanoVG.nvgBeginPath(c);
            org.lwjgl.nanovg.NanoVG.nvgRect(c, winX, winY, winW, TITLE);
            org.lwjgl.nanovg.NanoVG.nvgFillPaint(c, p);
            org.lwjgl.nanovg.NanoVG.nvgFill(c);
        }
        Nvg.unScissor(c);
        Nvg.insetHi(c, st, winX + 2, winY, winW - 4, winR() - 2, 0x10FFFFFF);

        // mac dots — red closes
        Nvg.circle(c, st, winX + 20, winY + 16, 5.5f, 0xFFFF5F57);
        Nvg.circle(c, st, winX + 38, winY + 16, 5.5f, 0xFFFEBC2E);
        Nvg.circle(c, st, winX + 56, winY + 16, 5.5f, 0xFF28C840);

        // centered brand — letterspaced caps over a mono subline with version
        Nvg.textL(c, st, "inter_b", 15, winX + winW / 2f, winY + 8, "N O X",
            DS.text(), 2.2f, org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_TOP);
        String ver = "v" + dev.noxclient.update.UpdateManager.currentVersion()
            + (dev.noxclient.premium.LicenseManager.premium() ? " Pro" : "")
            + " • Dark Mode";
        Nvg.text(c, st, "inter", 10, winX + winW / 2f, winY + 28, ver,
            DS.muted(), org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_TOP);

        // right cluster: [SHOP] [AC: x] [PANIC] — version moved to the subline
        // so the right side never collides with the centered brand
        titleBtns.clear();
        float rx = winX + winW - 14;
        float pw = Nvg.textW(c, "inter_b", 10.5f, "PANIC") + 18;
        Nvg.round(c, st, rx - pw, winY + 10, pw, 20, 10, DS.danger());
        Nvg.insetHi(c, st, rx - pw + 2, winY + 10, pw - 4, 8, 0x2EFFFFFF);
        Nvg.text(c, st, "inter_b", 10.5f, rx - pw / 2, winY + 15, "PANIC", 0xFFFFFFFF,
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        titleBtns.add(new float[]{rx - pw, pw, 0});
        rx -= pw + 10;
        String acd = AntiCheats.display();
        String ac = "AC: " + acd;
        // semantic color: none/vanilla = green, known AC = yellow warn, grim = red
        int acBg = switch (acd) {
            case "None", "Vanilla" -> 0xFF34D399;
            case "Grim", "Polar" -> 0xFFEF4444;
            default -> 0xFFFBBF24;
        };
        float aw = Nvg.textW(c, "inter_b", 10.5f, ac) + 14;
        Nvg.round(c, st, rx - aw, winY + 10, aw, 20, 10, acBg);
        Nvg.insetHi(c, st, rx - aw + 2, winY + 10, aw - 4, 8, 0x40FFFFFF);
        Nvg.text(c, st, "inter_b", 10.5f, rx - aw / 2, winY + 15, ac, 0xFF1A1A1F,
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        rx -= aw + 8;
        // shop entry — accent outline pill (quieter than PANIC, same row)
        float shw = Nvg.textW(c, "inter_b", 10.5f, "SHOP") + 18;
        Nvg.roundB(c, st, rx - shw, winY + 10, shw, 20, 10,
            Nvg.fade(DS.accent(), .25f), Nvg.fade(DS.accent(), .8f));
        Nvg.text(c, st, "inter_b", 10.5f, rx - shw / 2, winY + 15, "SHOP",
            DS.accent(), org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER
                | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        titleBtns.add(new float[]{rx - shw, shw, 1});

        Nvg.rect(c, st, winX + 1, winY + TITLE - 1, winW - 2, 1, DS.line());
    }

    /* ---------- tabs ---------- */
    /** Pill hitboxes captured during the draw pass — [x, w, catIndex(-1=All,-2=Design)].
     *  Drawn with Nvg.textW, so clicks must use THESE rects, not font.width. */
    private final List<float[]> tabRects = new ArrayList<>();
    /** Titlebar buttons — [x, w, action(0=panic, 1=shop)] captured at draw. */
    private final List<float[]> titleBtns = new ArrayList<>();

    private void tabs(long c, MemoryStack st, int mx, int my) {
        tabRects.clear();
        String[] names = new String[Category.values().length + 1];
        names[0] = "All"; int i = 1;
        for (Category cat : Category.values()) names[i++] = cat.label;
        /* Fit: pills shrink padding/font until the row fits the window width —
         * narrow windows used to push the Design pill off the right edge. */
        float avail = contentX() + contentW() - contentX();
        float pad = 32, gap = 6, fs = 12;
        float total;
        while (true) {
            total = 0;
            for (String n : names) total += Nvg.textW(c, "inter_sb", fs, n) + pad + gap;
            total += Nvg.textW(c, "inter_sb", fs, "Design") + pad + 10;
            if (total <= avail || fs <= 9f) break;
            if (pad > 20) { pad = 20; gap = 4; continue; }
            fs -= 1f;
        }
        int x = contentX();
        float tr = (TABS - 10) / 2f;
        for (int k = 0; k < names.length; k++) {
            int w = (int) (Nvg.textW(c, "inter_sb", fs, names[k]) + pad);
            boolean act = !designTab
                && ((k == 0 && tab == null) || (k > 0 && tab == Category.values()[k - 1]));
            boolean hov = mx >= x && mx < x + w && my >= tabsY() && my < tabsY() + TABS - 10;
            if (act) {
                Nvg.shadow(c, st, x, tabsY() + 1, w, TABS - 12, tr, 8,
                    Nvg.fade(DS.accent(), 0.35f));
                Nvg.hgrad(c, st, x, tabsY(), w, TABS - 10, tr, DS.accent(), DS.accent2());
                Nvg.insetHi(c, st, x + 2, tabsY(), w - 4, tr - 2, 0x3DFFFFFF);
            } else {
                Nvg.well(c, st, x, tabsY(), w, TABS - 10, tr,
                    hov ? DS.lighten(DS.surface(), .10f) : Nvg.fade(DS.surface(), .55f));
            }
            Nvg.text(c, st, "inter_sb", fs, x + w / 2f, tabsY() + (TABS - 10) / 2f, names[k],
                act ? 0xFFFFFFFF : hov ? DS.text() : DS.secondary(),
                org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
            tabRects.add(new float[]{x, w, k - 1});
            x += w + gap;
        }
        // Design pill — gear + label, opens the grouped GUI-settings page
        float dw = Nvg.textW(c, "inter_sb", fs, "Design") + pad + 10;
        tabRects.add(new float[]{x, dw, -2});
        boolean dhov = mx >= x && mx < x + dw && my >= tabsY() && my < tabsY() + TABS - 10;
        if (designTab) {
            Nvg.shadow(c, st, x, tabsY() + 1, dw, TABS - 12, tr, 8, Nvg.fade(DS.accent(), 0.35f));
            Nvg.hgrad(c, st, x, tabsY(), dw, TABS - 10, tr, DS.accent(), DS.accent2());
            Nvg.insetHi(c, st, x + 2, tabsY(), dw - 4, tr - 2, 0x3DFFFFFF);
        } else {
            Nvg.well(c, st, x, tabsY(), dw, TABS - 10, tr,
                dhov ? DS.lighten(DS.surface(), .10f) : Nvg.fade(DS.surface(), .55f));
        }
        gear(c, st, x + pad * .44f + 4, tabsY() + (TABS - 10) / 2f, 4.5f,
            designTab ? 0xFFFFFFFF : dhov ? DS.text() : DS.secondary());
        Nvg.text(c, st, "inter_sb", fs, x + pad * .44f + 14, tabsY() + (TABS - 10) / 2f, "Design",
            designTab ? 0xFFFFFFFF : DS.secondary(),
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
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
        int rh = ROW();
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + rh;
        String cs = UICustom.cardStyle;

        // card body — Flat = solid · Glass = top-lit gradient · Neon = glass + accent edge
        if ("Flat".equals(cs)) {
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
        // enabled state — accent edge bar + faint interior tint
        if (on) {
            Nvg.round(c, st, x, y, w, rh, 10, Nvg.fade(DS.cardAccent(), "Neon".equals(cs) ? 0.07f : 0.10f));
            Nvg.hgrad(c, st, x + 1.5f, y + rh * .22f, 3.5f, rh * .56f, 2,
                DS.cardAccent(), DS.accent2());
        }

        // icon chip — glyph on tinted tile (lit accent chip when on, well when off)
        float chip = 30, chx = x + 12, chy = y + rh / 2f - chip / 2;
        if (on) {
            Nvg.hgrad(c, st, chx, chy, chip, chip, 8,
                Nvg.fade(DS.cardAccent(), .9f), DS.accent2());
            Nvg.insetHi(c, st, chx + 1, chy, chip - 2, 7, 0x3AFFFFFF);
            Nvg.glyph(c, st, iconOf(m), chx + chip / 2, chy + chip / 2, 17, 0xFFFFFFFF);
        } else {
            // readable tile: lifted fill + hairline border so glyphs sit in chips,
            // not floating loose against the card
            Nvg.round(c, st, chx, chy, chip, chip, 8, Nvg.fade(DS.lighten(DS.surface(), .045f), .9f));
            Nvg.roundB(c, st, chx, chy, chip, chip, 8, 0x00FFFFFF,
                hov ? UICustom.borderHover : DS.line());
            Nvg.glyph(c, st, iconOf(m), chx + chip / 2, chy + chip / 2, 17,
                hov ? DS.secondary() : DS.muted());
        }

        // name + description vertically centered as a block around row middle
        Nvg.text(c, st, "inter_sb", 13.5f, x + 52, y + rh / 2f - 9.5f, m.name,
            on ? DS.text() : DS.secondary(),
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        Nvg.text(c, st, "inter", 10.5f, x + 52, y + rh / 2f + 9.5f,
            truncN(c, m.description, w - 150), DS.muted(),
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        if (m.premium) {
            float sx = x + 58 + Nvg.textW(c, "inter_sb", 13.5f, m.name);
            Nvg.glyph(c, st, "star", sx, y + rh / 2f - 9.5f, 10, DS.warning());
        }

        // iOS toggle — gradient track + lit knob
        int tx = x + w - 48, ty = y + rh / 2 - 10;
        Nvg.toggle2(c, st, tx, ty, 36, 20, toggleFrac(m), DS.surface(),
            DS.cardAccent(), DS.accent2(), UICustom.glow);

        if (hov && !m.settings.isEmpty()) {
            float cxx = x + w - 14, cyy = y + rh / 2f;
            Nvg.line(c, st, cxx - 4, cyy - 2, cxx, cyy + 3, 1.6f, DS.secondary());
            Nvg.line(c, st, cxx, cyy + 3, cxx + 4, cyy - 2, 1.6f, DS.secondary());
        }

        // settings child — inset darker card with top inner shade
        float ef = expandFrac(m);
        if (ef > 0) {
            int full = settingsFullH(m);
            int sh = (int) (ef * full);
            int ey = y + rh + 4;
            Nvg.clipPush(c, x, ey - 2, w, sh + 4);
            Nvg.well(c, st, x + 12, ey, w - 12, full, 10, Nvg.fade(DS.surface(), .75f));
            Nvg.line(c, st, x + 22, ey + 4, x + 22, ey + full - 4, 1f,
                Nvg.fade(DS.accent(), .35f));
            int sy = ey + 8;
            for (Setting<?> s : m.settings) {
                drawSetting(c, st, s, x + 30, sy, w - 54);
                settingRows.add(new SettingRow(s, sy));
                sy += settingH(s) + 6;
            }
            Nvg.clipPop(c);
        }
    }

    /** Module name → glyph icon (keyword map, category fallback). */
    private String iconOf(Module m) {
        String n = m.name.toLowerCase(java.util.Locale.ROOT);
        if (n.contains("aura") || n.contains("aim") || n.contains("trigger")) return "crosshair";
        if (n.contains("kill") || n.contains("sword") || n.contains("wtap") || n.contains("crit")) return "sword";
        if (n.contains("speed") || n.contains("sprint") || n.contains("timer") || n.contains("zoom")) return "bolt";
        if (n.contains("esp") || n.contains("finder") || n.contains("tracer") || n.contains("search") || n.contains("xray")) return "eye";
        if (n.contains("fly") || n.contains("elytra") || n.contains("jetpack") || n.contains("glide")) return "wings";
        if (n.contains("crystal") || n.contains("bed") || n.contains("anchor")) return "diamond";
        if (n.contains("totem") || n.contains("armor") || n.contains("protect")) return "shield";
        if (n.contains("chat") || n.contains("msg") || n.contains("spam")) return "chat";
        if (n.contains("spotify") || n.contains("music") || n.contains("lyric") || n.contains("song")) return "note";
        if (n.contains("staff") || n.contains("detect") || n.contains("radar") || n.contains("alert")) return "radar";
        if (n.contains("cape") || n.contains("skin") || n.contains("cosmet")) return "person";
        if (n.contains("money") || n.contains("price") || n.contains("shop") || n.contains("auction") || n.contains("bazaar")) return "diamond";
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
            case "donutsmp" -> "diamond";
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
        Nvg.text(c, st, "inter", 11.5f, x, mid, s.name, DS.secondary(),
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        if (s instanceof Setting.Bool b) {
            Nvg.check(c, st, x + Nvg.textW(c, "inter", 11.5f, s.name) + 10, mid - 8, 16,
                b.on(), DS.cardAccent(), DS.surface(), DS.border());
        } else if (s instanceof Setting.Mode mo) {
            float mx2 = x + w - 4;
            float[][] rects = new float[mo.modes.length][];
            for (int i = mo.modes.length - 1; i >= 0; i--) {
                float pw = Nvg.textW(c, "inter", 10.5f, mo.modes[i]) + 14;
                mx2 -= pw;
                rects[i] = new float[]{mx2, mid - 9, pw, 18};
                boolean act = mo.get().equals(mo.modes[i]);
                if (act) {
                    Nvg.hgrad(c, st, mx2, mid - 9, pw, 18, 9, DS.cardAccent(), DS.accent2());
                    Nvg.insetHi(c, st, mx2 + 1, mid - 9, pw - 2, 8, 0x33FFFFFF);
                } else {
                    Nvg.well(c, st, mx2, mid - 9, pw, 18, 9, Nvg.fade(DS.surface(), .7f));
                }
                Nvg.text(c, st, "inter", 10.5f, mx2 + pw / 2, mid, mo.modes[i],
                    act ? 0xFFFFFFFF : DS.secondary(),
                    org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
                mx2 -= 4;
            }
            ctrlRects.put(s, rects);
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
                Nvg.well(c, st, x + w - pw, mid - 9, pw, 18, 9, Nvg.fade(DS.surface(), .7f));
            Nvg.text(c, st, "mono", 10.5f, x + w - pw / 2, mid, txt,
                b.get() == -2 ? 0xFFFFFFFF : DS.secondary(),
                org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        } else if (s instanceof Setting.Str sstr) {
            boolean foc = sstr == focusedStr;
            String v = sstr.get().isEmpty() ? "…" : sstr.get();
            float tw2 = Math.min(150, Math.max(64, Nvg.textW(c, "inter", 10.5f, v) + 16));
            float fx = x + w - tw2, fy = mid - 9;
            ctrlRects.put(s, new float[][]{{fx, fy, tw2, 18}});
            Nvg.well(c, st, fx, fy, tw2, 18, 6,
                foc ? Nvg.fade(DS.surface(), .95f) : Nvg.fade(DS.surface(), .7f));
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
        Nvg.textL(c, st, "inter_b", 8.5f, contentX(), py + 13, "PINS · MMB", DS.muted(), 1.2f,
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
        float x = contentX() + Nvg.textLW(c, "inter_b", 8.5f, "PINS · MMB", 1.2f) + 12;
        pinRects.clear();
        for (int i = 0; i < 5; i++) {
            Module m = i < PINNED.size() ? NoxClient.INSTANCE.modules.get(PINNED.get(i)) : null;
            float pw = m != null ? Nvg.textW(c, "inter_sb", 11, m.name) + 40 : 30;
            pinRects.add(new float[]{x, pw, i});
            boolean hov = mx >= x && mx < x + pw && my >= py && my < py + 26;
            boolean on = m != null && m.isEnabled();
            Nvg.well(c, st, x, py, pw, 26, 13,
                hov ? DS.lighten(DS.surface(), .08f) : Nvg.fade(DS.surface(), .55f));
            if (on)
                Nvg.borderGrad(c, st, x, py, pw, 26, 13,
                    Nvg.fade(DS.cardAccent(), .5f), 0x00FFFFFF, Nvg.fade(DS.cardAccent(), .15f));
            if (m != null) {
                Nvg.circle(c, st, x + 15, py + 13, 2.6f, on ? DS.cardAccent() : DS.muted());
                Nvg.text(c, st, "inter_sb", 11, x + 24, py + 13, m.name,
                    on ? DS.text() : DS.secondary(),
                    org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
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
        if (Nvg.textW(c, "inter", 10.5f, s) <= w) return s;
        while (s.length() > 1 && Nvg.textW(c, "inter", 10.5f, s + "…") > w)
            s = s.substring(0, s.length() - 1);
        return s + "…";
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
        if (Math.hypot(mx - (winX + 20), my - (winY + 16)) <= 8) { onClose(); return true; }
        // titlebar buttons — rects recorded at draw (PANIC / SHOP / AC zone)
        if (my >= winY + 8 && my < winY + 32) {
            for (float[] b : titleBtns) {
                if (mx >= b[0] && mx < b[0] + b[1]) {
                    if (b[2] == 0) {
                        Module p = NoxClient.INSTANCE.modules.get("Panic");
                        if (p != null) p.setEnabled(!p.isEnabled());
                    } else if (b[2] == 1) {
                        minecraft.setScreenAndShow(new dev.noxclient.gui.ShopScreen(this));
                    }
                    return true;
                }
            }
        }
        if (my >= winY && my < winY + TITLE && mx >= winX && mx < winX + winW) {
            dragging = true; dragDX = (int) mx - winX; dragDY = (int) my - winY; return true;
        }
        if (mx >= winX + winW - 24 && my >= winY + winH - 24) { resizing = true; return true; }
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
            if (mx >= contentX() && mx < contentX() + listW() && my >= r.y && my < r.y + ROW()) {
                if (btn == 2) {   // MMB → pin
                    if (!PINNED.contains(r.m.name) && PINNED.size() < 5) PINNED.add(r.m.name);
                    else PINNED.remove(r.m.name); return true;
                }
                int tx = contentX() + listW() - 48;
                if (mx >= tx - 4 && mx <= tx + 40 && my >= r.y + ROW() / 2 - 12
                    && my <= r.y + ROW() / 2 + 12 && btn == 0) {   // LMB → toggle
                    toggleModule(r.m); return true;
                }
                if (btn == 0 || btn == 1) {   // LMB or RMB → expand
                    if (!r.m.settings.isEmpty()) setExpanded(r.m);
                    return true;
                }
            }
        }
        int fy = winY + winH - FOOT + 9;
        for (float[] pr : pinRects) {
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

    private boolean settingHit(Module m, double mx, double my) {
        int x = contentX() + 30, w = listW() - 54;
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
            float[][] rects = ctrlRects.get(s);
            if (rects != null)
                for (int k = 0; k < rects.length; k++)
                    if (mx >= rects[k][0] && mx < rects[k][0] + rects[k][2]
                        && my >= rects[k][1] && my < rects[k][1] + rects[k][3]) {
                        mo.set(mo.modes[k]); return true;
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
            winW = Math.max(560, Math.min(width - 16, (int) e.x() - winX + 8));
            winH = Math.max(360, Math.min(height - 16, (int) e.y() - winY + 8));
            NoxWindowScreen.winW = winW; NoxWindowScreen.winH = winH;   // shared with card GUI
            return true;
        }
        if (dragNum != null) {
            dragNumTo(dragNum, designTab ? contentX() + 12 : contentX() + 30,
                designTab ? listW() - 24 : listW() - 54, e.x()); return true;
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
        if (focusedStr != null) {
            if (key == InputConstants.KEY_BACKSPACE && !focusedStr.get().isEmpty())
                focusedStr.set(focusedStr.get().substring(0, focusedStr.get().length() - 1));
            if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RETURN)
                focusedStr = null;
            return true;
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
        if (focusedStr != null) {
            if (e.isAllowedChatCharacter()) focusedStr.set(focusedStr.get() + e.codepointAsString());
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
