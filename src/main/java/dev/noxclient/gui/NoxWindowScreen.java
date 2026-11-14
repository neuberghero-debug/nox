package dev.noxclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.noxclient.NoxClient;
import dev.noxclient.discord.DiscordLink;
import dev.noxclient.gui.nvg.NvgOverlay;
import dev.noxclient.gui.theme.Theme;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * NOX floating window — card-grid layout (icon tiles + ⚙ per module),
 * settings open in a separate draggable SettingsPopup (not a dropdown).
 *   drag title → move     drag bottom-right → resize
 *   tabs → category       search → filter all categories
 *   card LMB toggle / RMB+gear settings / MMB keybind
 *   T → theme   ESC/RShift → close
 */
public class NoxWindowScreen extends Screen {
    public static volatile boolean open;
    /** Live handle for the debug console (input injection via static handle). */
    public static NoxWindowScreen current;

    public static int winX = -1, winY = -1, winW = -1, winH = -1;
    public static int activeTab;

    private static final int TITLE_H = 30, TAB_H = 24, SEARCH_H = 22, FOOT_H = 30;
    private static final int MIN_W = 480, MIN_H = 240;
    private static final int GAP = 9;
    private static final String[] FOOT = {"Discord", "Report", "Suggest", "Share", "Import", "Shop", "PANIC"};

    private final Map<Category, List<ModuleCard>> cards = new EnumMap<>(Category.class);
    private final List<int[]> footRects = new ArrayList<>();
    private final long openedAt = System.currentTimeMillis();
    private static final long OPEN_MS = 180;
    public Category tab;
    private String search = "";
    private boolean searchFocused, dragging, resizing;
    private int dragOX, dragOY;
    private double scroll, scrollTarget;
    private SettingsPopup popup;

    public NoxWindowScreen() {
        super(Component.literal("NOX"));
        for (Category c : Category.values()) {
            List<ModuleCard> list = new ArrayList<>();
            for (Module m : NoxClient.INSTANCE.modules.by(c)) list.add(new ModuleCard(m));
            cards.put(c, list);
        }
        tab = Category.values()[Math.min(activeTab, Category.values().length - 1)];
        open = true;
        current = this;
    }

    @Override
    protected void init() {
        if (winW < 0) {
            winW = Math.max(MIN_W, Math.min(640, (int) (width * 0.60)));
            winH = Math.max(MIN_H, Math.min(560, (int) (height * 0.72)));
            winX = (width - winW) / 2;
            winY = (height - winH) / 2;
        }
        winW = Math.max(Math.min(MIN_W, width - 8), Math.min(winW, width - 8));
        winH = Math.max(Math.min(MIN_H, height - 8), Math.min(winH, height - 8));
        winX = Math.max(0, Math.min(winX, width - winW));
        winY = Math.max(0, Math.min(winY, height - winH));
    }

    private static final int SIDEBAR_W = 96;
    private boolean sidebar() { return "Sidebar".equals(UICustom.clickStyle); }

    // ---- "Panels" (Meteor-style): one floating column per category, no window
    private static final int PANEL_TOP = 40, ROW_H = 15, PANEL_HEAD = 20, PANEL_GAP = 10;
    private int panelW() {
        int n = Category.values().length;
        return Math.min(136, Math.max(96, (width - 16 - (n - 1) * PANEL_GAP) / n));
    }
    /** columns per row — wraps to 2 rows on narrow screens */
    private int panelsPerRow() {
        return Math.max(1, Math.min(Category.values().length,
            (width - 16 + PANEL_GAP) / (panelW() + PANEL_GAP)));
    }
    private int panelRows() {
        return (Category.values().length + panelsPerRow() - 1) / panelsPerRow();
    }
    private int panelRowH() {
        return Math.max(80, (height - PANEL_TOP - 16) / panelRows());
    }
    private final Map<Category, int[]> panelOff = new EnumMap<>(Category.class);
    private final Map<Category, Double> panelScroll = new EnumMap<>(Category.class);
    private Category panelDrag;
    private int panelDragOX, panelDragOY;
    private Module panelListening;

    // top pill bar (Panels mode): Modules | HUD | Shop | Theme | Discord | search
    private static final String[] PILL_ICON = {"◆", "▤", "★", "◐", "↗", "⌕"};
    private static final String[] PILL_TXT  = {"Modules", "HUD", "Shop", "Theme", "Discord", ""};
    private final List<int[]> pillRects = new ArrayList<>();
    private boolean panelSearch;

    // ---- "Hub" (LABY-style): left category rail + sectioned tile grid
    private static final int LIB_SB_W = 104, LIB_TILE_W = 102, LIB_TILE_H = 72, LIB_GAP = 8;
    private int libCat = -1;                       // -1 = All
    private final List<int[]> libTiles = new ArrayList<>(); // x,y,w,h
    private final List<Module> libTileMods = new ArrayList<>();
    private int libHudBtnX, libHudBtnY, libHudBtnW, libSearchX, libSearchY, libSearchW;
    private int hudBtnX, hudBtnY;

    private boolean library() { return "Hub".equals(UICustom.clickStyle); }

    private boolean panels() { return "Panels".equals(UICustom.clickStyle); }
    private int panelX(Category c) {
        int perRow = panelsPerRow();
        int row = c.ordinal() / perRow, col = c.ordinal() % perRow;
        int inRow = Math.min(perRow, Category.values().length - row * perRow);
        int rowW = inRow * panelW() + (inRow - 1) * PANEL_GAP;
        int startX = Math.max(8, (width - rowW) / 2);
        int[] o = panelOff.get(c);
        return startX + col * (panelW() + PANEL_GAP) + (o != null ? o[0] : 0);
    }
    private int panelY(Category c) {
        int row = c.ordinal() / panelsPerRow();
        int[] o = panelOff.get(c);
        return Math.max(8, PANEL_TOP + row * panelRowH() + (o != null ? o[1] : 0));
    }
    private double scrollOf(Category c) { return panelScroll.getOrDefault(c, 0.0); }
    private int panelBodyH(Category c) {
        int row = c.ordinal() / panelsPerRow();
        int rowTop = PANEL_TOP + row * panelRowH();
        return Math.min(NoxClient.INSTANCE.modules.by(c).size() * ROW_H + 6,
            Math.min(height - panelY(c) - PANEL_HEAD - 12,
                rowTop + panelRowH() - 6 - panelY(c) - PANEL_HEAD));
    }
    private int contentX() { return winX + (sidebar() ? SIDEBAR_W + 14 : 12); }
    private int contentY() {
        return winY + TITLE_H + SEARCH_H + (sidebar() ? 12 : TAB_H + 18);
    }
    private int contentW() { return winW - (sidebar() ? SIDEBAR_W + 26 : 24); }
    private int contentH() { return winY + winH - FOOT_H - 8 - contentY(); }
    private int cols() {
        if ("Compact rows".equals(UICustom.clickStyle)) return 1;
        return Math.max(2, (contentW() + GAP) / (ModuleCard.CARD_W + GAP));
    }

    /** debug console: last-rendered visible card list (x/y/width assigned in render). */
    public List<ModuleCard> debugCards() { return visible(); }

    private List<ModuleCard> visible() {
        String f = search.toLowerCase();
        List<ModuleCard> out = new ArrayList<>();
        for (Category c : Category.values()) {
            if (!f.isEmpty() || c == tab)
                for (ModuleCard mc : cards.get(c)) if (mc.matches(f)) out.add(mc);
        }
        return out;
    }

    private int totalH() {
        int n = visible().size();
        int rows = (n + cols() - 1) / cols();
        return rows * (UICustom.cardH + GAP);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        if (dev.noxclient.util.DebugConsole.hoverX >= 0) {
            mx = (int) dev.noxclient.util.DebugConsole.hoverX;
            my = (int) dev.noxclient.util.DebugConsole.hoverY;
        }
        Theme t = Theme.current();
        scroll += (scrollTarget - scroll) * Math.min(1, dt * 14);

        // open animation: cubic ease-out scale on the window, backdrop fades in
        float openP = Math.min(1f, (System.currentTimeMillis() - openedAt) / (float) OPEN_MS);
        float ease = 1f - (1f - openP) * (1f - openP) * (1f - openP);
        boolean anim = openP < 1f;

        // dim behind the window (vanilla blurs screen backgrounds already —
        // calling blurBeforeThisStratum here would be the 2nd blur → crash)
        g.fill(0, 0, width, height, ((int) (0x66 * ease)) << 24 | 0x00050810);
        NvgOverlay.clear();
        NvgOverlay.vignette(width, height, 0.30f);

        if (panels()) {
            renderPanels(g, mx, my, t);
            super.extractRenderState(g, mx, my, dt);
            if (popup != null) {
                g.fill(0, 0, width, height, 0x33000000);
                popup.render(g, mx, my);
            }
            return;
        }

        if (anim) {
            float s = 0.955f + 0.045f * ease;
            float ax = winX + winW / 2f, ay = winY + winH / 2f;
            g.pose().pushMatrix();
            g.pose().translate(ax, ay);
            g.pose().scale(s, s);
            g.pose().translate(-ax, -ay);
        }

        Gui.shadow(g, winX, winY, winW, winH, UICustom.radius);
        Gui.round(g, winX, winY, winW, winH, UICustom.radius, UICustom.winFill);
        int accentRgb = t.accent() & 0xFFFFFF;
        int accent2 = (Math.min(255, ((accentRgb >> 16) & 0xFF) + 70) << 16)
            | (Math.min(255, ((accentRgb >> 8) & 0xFF) + 70) << 8)
            | Math.min(255, (accentRgb & 0xFF) + 70);
        NvgOverlay.glow(winX, winY, winW, winH, UICustom.radius, accentRgb, 0.10f, 18);
        NvgOverlay.sheen(winX + 1, winY + 1, winW - 2, 22, UICustom.radius - 1, 0xFFFFFF, 0.05f);
        NvgOverlay.gradientStroke(winX, winY, winW, winH, UICustom.radius, accentRgb, accent2, 0.30f, 0f);
        NvgOverlay.comet(winX + UICustom.radius, winY, winW - UICustom.radius * 2, 0xFFFFFF, 0.16f, 6f);
        NvgOverlay.innerTop(winX + 2, winY + 1, winW - 4, UICustom.radius - 2, 0xFFFFFF, 0.10f);
        NvgOverlay.glass(winX + 1, winY + 1, winW - 2, winH - 2, UICustom.radius - 1, 1f);
        // window style: Solid = flat fill, Sky = gargantua + dark tint, Glass = gargantua barely tinted
        boolean solid = "Solid".equals(UICustom.winStyle);
        g.enableScissor(winX + 1, winY + 1, winX + winW - 1, winY + winH - 1);
        if (solid || !AnimatedSky.render(g, winX + 1, winY + 1, winW - 2, winH - 2)) {
            if (solid)
                Gui.vgrad(g, winX + 1, winY + 1, winW - 2, winH - 2, UICustom.radius - 1,
                    UICustom.winFill, (UICustom.winFill & 0xFFFFFF) | 0xE6000000);
            else
                Gui.vgrad(g, winX + 1, winY + 1, winW - 2, winH - 2, UICustom.radius - 1,
                    0xFA181824, 0xFA101018);
        }
        g.disableScissor();
        if (!solid)
            g.fill(winX + 1, winY + 1, winX + winW - 1, winY + winH - 1,
                "Glass".equals(UICustom.winStyle) ? 0x3C101018 : 0x9C101018);

        // title bar
        Gui.textBold(g, font, "N O X", winX + 14, winY + 10, t.accent());
        int vw = Gui.width(font, "v" + NoxClient.VERSION) + 12;
        Gui.pill(g, winX + 58, winY + 8, vw, 14, t.accent() & 0x2EFFFFFF);
        Gui.text(g, font, "v" + NoxClient.VERSION, winX + 64, winY + 11, t.accent());
        Gui.text(g, font, NoxClient.INSTANCE.modules.all().size() + " modules · "
            + Theme.currentName(), winX + 66 + vw, winY + 11, 0xFF5A5A6E);
        g.fill(winX + 12, winY + TITLE_H - 1, winX + winW - 12, winY + TITLE_H,
            t.accent() & 0x40FFFFFF);
        boolean closeHover = ModuleCard.inside(mx, my, winX + winW - 26, winY + 5, 20, 20);
        if (closeHover) Gui.circle(g, winX + winW - 16, winY + 15, 9, 0x40FF5555);
        Gui.text(g, font, "✕", winX + winW - 20, winY + 10, closeHover ? 0xFFFF5555 : 0xFF888899);

        if (library()) {
            renderLibrary(g, mx, my, t);
        } else {
        // tabs — horizontal pills, or a left sidebar in "Sidebar" click style
        if (sidebar()) {
            int sby = contentY();
            for (Category c : Category.values()) {
                boolean active = c == tab && search.isEmpty();
                boolean hover = ModuleCard.inside(mx, my, winX + 8, sby, SIDEBAR_W - 4, 20);
                if (active) Gui.pill(g, winX + 8, sby, SIDEBAR_W - 4, 20, t.accent() & 0x33FFFFFF);
                else if (hover) Gui.pill(g, winX + 8, sby, SIDEBAR_W - 4, 20, 0x14FFFFFF);
                if (active) Gui.textBold(g, font, c.label, winX + 16, sby + 6, 0xFFE8D8FF);
                else Gui.text(g, font, c.label, winX + 16, sby + 6, hover ? t.text() : 0xFF7C7C90);
                sby += 22;
            }
        } else {
            int tx = winX + 12, ty = winY + TITLE_H + 6;
            for (Category c : Category.values()) {
                int w = Gui.width(font, c.label) + 18;
                boolean active = c == tab && search.isEmpty();
                boolean hover = ModuleCard.inside(mx, my, tx, ty, w, TAB_H - 4);
                if (active) {
                    Gui.pill(g, tx, ty, w, TAB_H - 4, t.accent() & 0x33FFFFFF);
                    Gui.textBold(g, font, c.label, tx + 9, ty + 5, 0xFFE8D8FF);
                } else {
                    if (hover) Gui.pill(g, tx, ty, w, TAB_H - 4, 0x14FFFFFF);
                    Gui.text(g, font, c.label, tx + 9, ty + 5, hover ? t.text() : 0xFF7C7C90);
                }
                tx += w + 4;
            }
        }

        // search (+ HUD-settings shortcut — was only reachable via library mode)
        int sx = contentX(), sy = winY + TITLE_H + (sidebar() ? 6 : TAB_H + 10),
            sw = (sidebar() ? contentW() : winW - 24) - 50;
        Gui.roundBorder(g, sx, sy, sw, SEARCH_H, 11,
            searchFocused ? t.accent() : UICustom.border, 0xFF0E131C);
        Gui.text(g, font, "⌕", sx + 9, sy + 7, 0xFF555566);
        String shown = search.isEmpty() && !searchFocused ? "Search modules…"
            : search + (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0 ? "▏" : "");
        Gui.text(g, font, shown, sx + 20, sy + 7, search.isEmpty() && !searchFocused ? 0xFF4E4E62 : t.text());
        hudBtnX = sx + sw + 6; hudBtnY = sy;
        boolean hb = ModuleCard.inside(mx, my, hudBtnX, hudBtnY, 44, SEARCH_H);
        Gui.roundBorder(g, hudBtnX, hudBtnY, 44, SEARCH_H, 11,
            hb ? t.accent() : UICustom.border, hb ? 0xFF1A2030 : 0xFF0E131C);
        Gui.textCenter(g, font, "▤", hudBtnX + 22, hudBtnY + 7, hb ? t.text() : 0xFF7C7C90);

        // card grid
        int cy = contentY(), ch = contentH();
        int cols = cols();
        int cw = (contentW() - (cols - 1) * GAP) / cols;
        g.enableScissor(winX + 4, cy, winX + winW - 4, cy + ch);
        List<ModuleCard> vis = visible();
        for (int i = 0; i < vis.size(); i++) {
            ModuleCard b = vis.get(i);
            int col = i % cols, row = i / cols;
            b.x = contentX() + col * (cw + GAP);
            b.y = cy + row * (UICustom.cardH + GAP) - (int) scroll;
            b.width = cw;
            b.hoverNow = false;
            if (b.y + b.height > cy - 6 && b.y < cy + ch) b.render(g, mx, my);
        }
        g.disableScissor();

        // hover tooltip: full module description after the scissor closes
        if (popup == null)
            for (ModuleCard b : vis) {
                if (!b.hoverNow || !ModuleCard.inside(mx, my, b.x, Math.max(b.y, cy),
                        b.width, Math.min(b.y + b.height, cy + ch) - Math.max(b.y, cy))) continue;
                String ds = b.module.description == null ? "" : b.module.description;
                if (ds.isEmpty()) break;
                String st = null;
                try { if (b.module.isEnabled()) st = b.module.status(); } catch (Throwable ignored) {}
                int tipH = st != null ? 40 : 30;
                int tw = Math.max(Gui.width(font, ds) + 16, Gui.width(font, b.module.name) + 16);
                if (st != null) tw = Math.max(tw, Gui.width(font, st) + 16);
                int tipX = Math.max(winX + 8, Math.min(winX + winW - 8 - tw, (int) mx + 10));
                int tipY = (int) my + 14;
                if (tipY + tipH > winY + winH - 8) tipY = (int) my - tipH - 8;
                Gui.shadow(g, tipX, tipY, tw, tipH, 7);
                Gui.roundBorder(g, tipX, tipY, tw, tipH, 7, UICustom.border, 0xF6141821);
                Gui.textBold(g, font, b.module.name, tipX + 8, tipY + 5, t.accent());
                Gui.text(g, font, ds, tipX + 8, tipY + 16, 0xFFB8B8CC);
                if (st != null)
                    Gui.text(g, font, st, tipX + 8, tipY + 27,
                        b.module.lastError != null ? 0xFFFF6666 : 0xFF66D9A0);
                break;
            }

        }

        // scrollbar
        int total = library() ? libTotalH() : totalH();
        int scy = library() ? libSearchY + SEARCH_H + 12 : contentY();
        int sch = library() ? winY + winH - FOOT_H - 8 - scy : contentH();
        if (total > sch) {
            int barH = Math.max(24, sch * sch / total);
            int barY = scy + (int) ((sch - barH) * (scroll / Math.max(1, total - sch)));
            Gui.pill(g, winX + winW - 6, scy, 3, sch, 0xFF2A2A38);
            Gui.pill(g, winX + winW - 6, barY, 3, barH, t.accent());
        }

        // resize grip — diagonal drag lines, brighter when hovered/dragging
        {
            boolean gripHot = resizing
                || ModuleCard.inside(mx, my, winX + winW - 24, winY + winH - 24, 24, 24);
            int gc = gripHot ? (0xFF000000 | Theme.current().accent()) : 0xFF6A6A80;
            for (int i = 0; i < 3; i++) {
                int o = 6 + i * 5;
                for (int d = 0; d < o; d++)
                    g.fill(winX + winW - 4 - d, winY + winH - 4 - o + d,
                        winX + winW - 3 - d, winY + winH - 3 - o + d, gc);
            }
        }

        // footer
        footRects.clear();
        int fy = winY + winH - FOOT_H - 4;
        g.fill(winX + UICustom.radius, fy - 2, winX + winW - UICustom.radius, fy - 1, 0xFF1E1E2A);
        int fx = winX + 12;
        for (String label : FOOT) {
            int pw = Gui.width(font, label) + 16;
            if (fx + pw > winX + winW - 12) break;
            boolean hov = ModuleCard.inside(mx, my, fx, fy + 5, pw, 17);
            boolean panic = label.equals("PANIC");
            Gui.pill(g, fx, fy + 5, pw, 17,
                panic ? (hov ? 0x55FF5555 : 0x28FF5555)
                      : (hov ? t.accent() & 0x55FFFFFF : 0xFF1B1B28));
            Gui.text(g, font, label, fx + 8, fy + 9,
                panic ? 0xFFFF8888 : (hov ? 0xFFE8D8FF : 0xFF9A9AAE));
            footRects.add(new int[]{fx, fy + 5, pw, 17});
            fx += pw + 6;
        }

        if (anim) g.pose().popMatrix();

        super.extractRenderState(g, mx, my, dt);

        // settings popup — topmost, modal
        if (popup != null) {
            g.fill(0, 0, width, height, 0x33000000);
            popup.render(g, mx, my);
        }
    }

    /** Meteor-style render: floating columns + centered top pill nav. */
    private void renderPanels(GuiGraphicsExtractor g, int mx, int my, Theme t) {
        renderTopPill(g, mx, my, t);
        String f = search.toLowerCase();
        for (Category c : Category.values()) {
            List<Module> mods = NoxClient.INSTANCE.modules.by(c).stream()
                .filter(m -> f.isEmpty() || m.name.toLowerCase().contains(f)).toList();
            int px = panelX(c), py = panelY(c);
            int row = c.ordinal() / panelsPerRow();
            int rowTop = PANEL_TOP + row * panelRowH();
            int bodyH = Math.min(mods.size() * ROW_H + 6,
                Math.min(height - py - PANEL_HEAD - 12, rowTop + panelRowH() - 6 - py - PANEL_HEAD));
            if (bodyH < ROW_H) continue;
            int ph = PANEL_HEAD + bodyH;
            boolean headHover = ModuleCard.inside(mx, my, px, py, panelW(), PANEL_HEAD);

            Gui.shadow(g, px, py, panelW(), ph, 8);
            Gui.roundBorder(g, px, py, panelW(), ph, 8,
                headHover ? UICustom.cardAccent : UICustom.border, 0xF2141821);
            NvgOverlay.glow(px, py, panelW(), ph, 8, t.accent() & 0xFFFFFF,
                headHover ? 0.16f : 0.07f, 12);
            NvgOverlay.glass(px + 1, py + 1, panelW() - 2, ph - 2, 8, 0.8f);
            NvgOverlay.innerTop(px + 2, py + 1, panelW() - 4, 7, 0xFFFFFF, 0.08f);
            // header: category icon + label + enabled count; drag grip
            long on = mods.stream().filter(Module::isEnabled).count();
            String cnt = on + "/" + mods.size();
            int cntW = Gui.width(font, cnt + " ⌄");
            Gui.text(g, font, ModuleCard.catIcon(c), px + 7, py + 6, UICustom.cardAccent);
            String lb = c.label;
            int maxLb = panelW() - 17 - cntW - 12;
            while (Gui.width(font, lb) > maxLb && lb.length() > 2)
                lb = lb.substring(0, lb.length() - 1);
            Gui.textBold(g, font, lb, px + 17, py + 6, UICustom.cardAccent);
            Gui.text(g, font, cnt + " ⌄", px + panelW() - 8 - cntW, py + 6, 0xFF6A6A80);
            g.fill(px + 6, py + PANEL_HEAD - 1, px + panelW() - 6, py + PANEL_HEAD,
                UICustom.cardAccent & 0x44FFFFFF);

            // module rows — enabled rows render as a filled accent pill
            int bodyY = py + PANEL_HEAD + 2;
            g.enableScissor(px, bodyY, px + panelW(), bodyY + bodyH - 4);
            int ry = bodyY - (int) scrollOf(c);
            for (Module m : mods) {
                if (ry + ROW_H > bodyY && ry < bodyY + bodyH) {
                    boolean hov = ModuleCard.inside(mx, my, px + 2, ry, panelW() - 4, ROW_H - 1);
                    boolean en = m.isEnabled();
                    if (en)
                        Gui.pill(g, px + 2, ry, panelW() - 4, ROW_H - 1,
                            (UICustom.cardAccent & 0x00FFFFFF) | 0x66000000);
                    else if (hov)
                        Gui.pill(g, px + 2, ry, panelW() - 4, ROW_H - 1, 0x16FFFFFF);
                    String nm = m.name;
                    int maxW = panelW() - 20 - (m.premium ? 9 : 0);
                    while (Gui.width(font, nm) > maxW && nm.length() > 3)
                        nm = nm.substring(0, nm.length() - 1);
                    Gui.text(g, font, nm, px + 9, ry + 4,
                        panelListening == m ? 0xFFFFD870
                            : en ? 0xFFFFFFFF : (hov ? t.text() : 0xFF8A8A9E));
                    if (m.premium) Gui.text(g, font, "★", px + panelW() - 13, ry + 4, 0xFFFFD870);
                    if (panelListening == m)
                        Gui.textCenter(g, font, "press key…", px + panelW() / 2, ry + 4, 0xFFFFD870);
                }
                ry += ROW_H;
            }
            g.disableScissor();
        }
        // hint strip at the bottom
        Gui.textCenter(g, font,
            "LMB toggle · RMB settings · MMB keybind · drag headers · " + DiscordLink.shortLabel(),
            width / 2, height - 12, 0xFF55556A);
        if (panelListening != null)
            Gui.textCenter(g, font, "binding " + panelListening.name + " — press a key, ESC clears",
                width / 2, height - 24, 0xFFFFD870);
    }

    /** Centered top pill: Modules (active) | HUD | Shop | Theme | Discord | ⌕. */
    private void renderTopPill(GuiGraphicsExtractor g, int mx, int my, Theme t) {
        pillRects.clear();
        int[] iw = new int[PILL_ICON.length];
        int total = 0;
        for (int i = 0; i < PILL_ICON.length; i++) {
            iw[i] = Gui.width(font, PILL_ICON[i]) + (PILL_TXT[i].isEmpty() ? 6 : Gui.width(font, PILL_TXT[i]) + 12);
            total += iw[i];
        }
        total += (PILL_ICON.length - 1) * 2 + 12;
        if (panelSearch) total += 92;
        int x = Math.max(6, (width - total) / 2), y = 7;

        Gui.shadow(g, x, y, total, 20, 10);
        Gui.roundBorder(g, x, y, total, 20, 10, UICustom.border, 0xF6141821);
        NvgOverlay.glow(x, y, total, 20, 10, t.accent() & 0xFFFFFF, 0.12f, 12);
        NvgOverlay.gradientStroke(x, y, total, 20, 10, t.accent() & 0xFFFFFF, 0xFFFFFF, 0.22f, 1.7f);
        NvgOverlay.comet(x + 10, y, total - 20, t.accent() & 0xFFFFFF, 0.35f, 4f);
        int ix = x + 6;
        for (int i = 0; i < PILL_ICON.length; i++) {
            boolean hov = ModuleCard.inside(mx, my, ix, y + 3, iw[i], 14);
            boolean active = i == 0; // Modules = current view
            if (active)
                Gui.pill(g, ix - 3, y + 3, iw[i] + 6, 14, UICustom.cardAccent);
            else if (hov)
                Gui.pill(g, ix - 3, y + 3, iw[i] + 6, 14, 0x1EFFFFFF);
            Gui.text(g, font, PILL_ICON[i], ix + 2, y + 6,
                active ? 0xFFFFFFFF : UICustom.cardAccent);
            if (!PILL_TXT[i].isEmpty())
                Gui.text(g, font, PILL_TXT[i], ix + 4 + Gui.width(font, PILL_ICON[i]), y + 6,
                    active ? 0xFFFFFFFF : (hov ? t.text() : 0xFF9A9AAE));
            pillRects.add(new int[]{ix - 3, y + 3, iw[i] + 6, 14});
            ix += iw[i] + 2;
        }
        if (panelSearch) {
            int sx = ix + 4, sw = total - (ix - x) - 10;
            Gui.roundBorder(g, sx, y + 3, sw, 14, 7,
                searchFocused ? UICustom.cardAccent : UICustom.border, 0xFF0F141D);
            String shown = search.isEmpty() && !searchFocused ? "search…"
                : search + (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0 ? "▏" : "");
            Gui.text(g, font, shown, sx + 6, y + 6, search.isEmpty() && !searchFocused ? 0xFF4E4E62 : t.text());
            pillRects.add(new int[]{sx, y + 3, sw, 14}); // index PILL_ICON.length = search field
        }
    }

    /** LABY-style window body: left category rail + search row + sectioned tiles. */
    private void renderLibrary(GuiGraphicsExtractor g, int mx, int my, Theme t) {
        int sbx = winX + 12;
        int sby = winY + TITLE_H + 8;
        Gui.textBold(g, font, "Categories", sbx + 6, sby, 0xFF5A5A6E);
        sby += 16;
        for (int i = -1; i < Category.values().length; i++) {
            boolean active = i == libCat && search.isEmpty();
            boolean hov = ModuleCard.inside(mx, my, sbx, sby, LIB_SB_W - 8, 20);
            if (active) Gui.pill(g, sbx, sby, LIB_SB_W - 8, 20, UICustom.cardAccent);
            else if (hov) Gui.pill(g, sbx, sby, LIB_SB_W - 8, 20, 0x14FFFFFF);
            String ic = i < 0 ? "◈" : ModuleCard.catIcon(Category.values()[i]);
            String lb = i < 0 ? "All" : Category.values()[i].label;
            Gui.text(g, font, ic, sbx + 8, sby + 6, active ? 0xFFFFFFFF : UICustom.cardAccent);
            Gui.text(g, font, lb, sbx + 20, sby + 6, active ? 0xFFFFFFFF : (hov ? t.text() : 0xFF8A8A9E));
            sby += 22;
        }
        int dvx = winX + LIB_SB_W + 14;
        g.fill(dvx, winY + TITLE_H + 4, dvx + 1, winY + winH - FOOT_H - 6, 0xFF26263A);

        // search row + "HUD" shortcut button
        int cx = dvx + 10;
        int cw = winX + winW - 12 - cx;
        libSearchX = cx; libSearchY = winY + TITLE_H + 4; libSearchW = cw - 82;
        Gui.roundBorder(g, libSearchX, libSearchY, libSearchW, SEARCH_H, 11,
            searchFocused ? t.accent() : UICustom.border, 0xFF0E131C);
        Gui.text(g, font, "⌕", libSearchX + 9, libSearchY + 7, 0xFF555566);
        String shown = search.isEmpty() && !searchFocused ? "Search modules…"
            : search + (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0 ? "▏" : "");
        Gui.text(g, font, shown, libSearchX + 20, libSearchY + 7,
            search.isEmpty() && !searchFocused ? 0xFF4E4E62 : t.text());
        libHudBtnX = cx + cw - 74; libHudBtnY = libSearchY; libHudBtnW = 74;
        boolean hb = ModuleCard.inside(mx, my, libHudBtnX, libHudBtnY, libHudBtnW, SEARCH_H);
        Gui.roundBorder(g, libHudBtnX, libHudBtnY, libHudBtnW, SEARCH_H, 11,
            hb ? UICustom.cardAccent : UICustom.border,
            hb ? (UICustom.cardAccent & 0x00FFFFFF) | 0x55000000 : 0xFF0E131C);
        Gui.textCenterBold(g, font, "▤ HUD", libHudBtnX + libHudBtnW / 2, libSearchY + 7,
            hb ? 0xFFFFFFFF : t.text());

        // sectioned tile grid
        int gy = libSearchY + SEARCH_H + 12;
        int gh = winY + winH - FOOT_H - 8 - gy;
        g.enableScissor(dvx + 2, gy - 2, winX + winW - 6, gy + gh);
        libTiles.clear();
        libTileMods.clear();
        int cols = Math.max(2, (cw + LIB_GAP) / (LIB_TILE_W + LIB_GAP));
        int ry = gy - (int) scroll;
        String f = search.toLowerCase();
        for (Category c : Category.values()) {
            if (libCat >= 0 && c.ordinal() != libCat && f.isEmpty()) continue;
            List<Module> mods = NoxClient.INSTANCE.modules.by(c).stream()
                .filter(m -> f.isEmpty() || m.name.toLowerCase().contains(f)
                    || (m.description != null && m.description.toLowerCase().contains(f))).toList();
            if (mods.isEmpty()) continue;
            if (ry + 16 > gy - 4 && ry < gy + gh) {
                Gui.text(g, font, ModuleCard.catIcon(c), cx + 2, ry + 3, UICustom.cardAccent);
                Gui.textBold(g, font, c.label, cx + 14, ry + 3, 0xFFE8E8F4);
                Gui.text(g, font, String.valueOf(mods.size()),
                    cx + 20 + Gui.width(font, c.label), ry + 3, 0xFF8A8A9E);
                g.fill(cx, ry + 14, cx + 72, ry + 15, UICustom.cardAccent);
            }
            ry += 18;
            for (int i = 0; i < mods.size(); i++) {
                int col = i % cols, row = i / cols;
                int tx = cx + col * (LIB_TILE_W + LIB_GAP);
                int ty = ry + row * (LIB_TILE_H + LIB_GAP);
                libTiles.add(new int[]{tx, ty, LIB_TILE_W, LIB_TILE_H});
                libTileMods.add(mods.get(i));
                if (ty + LIB_TILE_H > gy - 4 && ty < gy + gh)
                    renderLibTile(g, mods.get(i), tx, ty, mx, my, t);
            }
            ry += ((mods.size() + cols - 1) / cols) * (LIB_TILE_H + LIB_GAP) + 8;
        }
        g.disableScissor();
    }

    /** Square module tile: icon chip + name + ON/OFF strip (LABY-style). */
    private void renderLibTile(GuiGraphicsExtractor g, Module m, int x, int y,
                               int mx, int my, Theme t) {
        boolean on = m.isEnabled();
        boolean hov = ModuleCard.inside(mx, my, x, y, LIB_TILE_W, LIB_TILE_H);
        boolean gearHov = ModuleCard.inside(mx, my, x + LIB_TILE_W - 18, y + 2, 16, 14);
        Gui.shadow(g, x, y, LIB_TILE_W, LIB_TILE_H, 9);
        int border = on ? (UICustom.cardAccent & 0x00FFFFFF) | 0xFF000000
            : hov ? UICustom.borderHover : UICustom.border;
        Gui.round(g, x, y, LIB_TILE_W, LIB_TILE_H, 9, border);
        Gui.vgrad(g, x + 1, y + 1, LIB_TILE_W - 2, LIB_TILE_H - 2, 8,
            UICustom.cardFillTop, UICustom.cardFillBottom);
        int icS = 22, icX = x + (LIB_TILE_W - icS) / 2;
        Gui.round(g, icX, y + 7, icS, icS, 7,
            on ? (UICustom.cardAccent & 0x00FFFFFF) | 0x44000000 : 0xFF20202E);
        Gui.textCenterBold(g, font, ModuleCard.glyph(m), x + LIB_TILE_W / 2, y + 14,
            on ? UICustom.cardAccent : 0xFF8A8A9E);
        String nm = m.name;
        while (Gui.width(font, nm) > LIB_TILE_W - 10 && nm.length() > 3)
            nm = nm.substring(0, nm.length() - 1);
        Gui.textCenterBold(g, font, nm, x + LIB_TILE_W / 2, y + 34,
            hov || on ? t.text() : 0xFF9A9AAE);
        int sy = y + LIB_TILE_H - 18;
        Gui.pill(g, x + 4, sy, LIB_TILE_W - 8, 14, on ? UICustom.cardAccent : 0xFF1E1E2C);
        Gui.textCenterBold(g, font, on ? "ON" : "OFF", x + LIB_TILE_W / 2, sy + 3,
            on ? 0xFFFFFFFF : 0xFF6A6A80);
        if (m.premium) Gui.text(g, font, "★", x + 6, y + 5, 0xFFFFD870);
        if (hov && !m.settings.isEmpty())
            Gui.text(g, font, "⚙", x + LIB_TILE_W - 14, y + 4, gearHov ? UICustom.cardAccent : 0xFF5A5A6E);
        if (panelListening == m)
            Gui.textCenter(g, font, "key…", x + LIB_TILE_W / 2, y + 46, 0xFFFFD870);
    }

    private int libTotalH() {
        int cw = winX + winW - 12 - (winX + LIB_SB_W + 24);
        int cols = Math.max(2, (cw + LIB_GAP) / (LIB_TILE_W + LIB_GAP));
        int total = 0;
        String f = search.toLowerCase();
        for (Category c : Category.values()) {
            if (libCat >= 0 && c.ordinal() != libCat && f.isEmpty()) continue;
            long n = NoxClient.INSTANCE.modules.by(c).stream()
                .filter(m -> f.isEmpty() || m.name.toLowerCase().contains(f)
                    || (m.description != null && m.description.toLowerCase().contains(f))).count();
            if (n == 0) continue;
            total += 18 + ((n + cols - 1) / cols) * (LIB_TILE_H + LIB_GAP) + 8;
        }
        return total;
    }

    private boolean panelsClick(double mx, double my, int btn) {
        // top pill items first
        for (int i = 0; i < pillRects.size(); i++) {
            int[] r = pillRects.get(i);
            if (!ModuleCard.inside(mx, my, r[0], r[1], r[2], r[3])) continue;
            if (i == PILL_ICON.length) { searchFocused = true; return true; } // inline search field
            if (btn != 0) return true;
            switch (i) {
                case 1 -> { Module hw = NoxClient.INSTANCE.modules.get("HudWidgets");
                            if (hw != null) popup = new SettingsPopup(hw, (int) mx, (int) my, width, height); }
                case 2 -> minecraft.setScreenAndShow(new dev.noxclient.gui.ShopScreen(this));
                case 3 -> Theme.cycle();
                case 4 -> openUri(DiscordLink.get());
                case 5 -> { panelSearch = !panelSearch; searchFocused = panelSearch; }
                default -> {}
            }
            return true;
        }
        searchFocused = false;
        for (Category c : Category.values()) {
            List<Module> mods = NoxClient.INSTANCE.modules.by(c);
            int px = panelX(c), py = panelY(c);
            int bodyH = panelBodyH(c);
            if (ModuleCard.inside(mx, my, px, py, panelW(), PANEL_HEAD)) {
                if (btn == 0) { panelDrag = c; panelDragOX = (int) mx - px; panelDragOY = (int) my - py; }
                return true;
            }
            if (!ModuleCard.inside(mx, my, px + 2, py + PANEL_HEAD, panelW() - 4, bodyH)) continue;
            int idx = (int) ((my - (py + PANEL_HEAD + 2) + scrollOf(c)) / ROW_H);
            if (idx < 0 || idx >= mods.size()) return true;
            Module m = mods.get(idx);
            if (btn == 0) { m.toggle(); }
            else if (btn == 1) { popup = new SettingsPopup(m, (int) mx, (int) my, width, height); }
            else if (btn == 2) { panelListening = panelListening == m ? null : m; }
            return true;
        }
        return true; // swallow backdrop clicks so the screen stays open
    }

    /** 26.3 passes RAW SDL buttons (1=LMB, 2=MMB, 3=RMB) — map to 0/1/2 used below. */
    private static int normButton(int sdl) {
        return switch (sdl) { case 1 -> 0; case 3 -> 1; case 2 -> 2; default -> sdl; };
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x(), my = e.y();
        int btn = normButton(e.button());

        if (popup != null) {
            int r = popup.mouseClicked(mx, my, btn);
            if (r == 2) popup = null;
            return true; // modal: swallow everything while open
        }

        // resize grip — checked BEFORE panels() so it works in every layout;
        // generous 24px zone (was 16 and unreachable in panels mode)
        if (ModuleCard.inside(mx, my, winX + winW - 24, winY + winH - 24, 24, 24)) {
            resizing = true; return true;
        }

        if (panels()) return panelsClick(mx, my, btn);

        if (ModuleCard.inside(mx, my, winX + winW - 24, winY + 4, 18, 18)) { onClose(); return true; }
        if (ModuleCard.inside(mx, my, winX, winY, winW, TITLE_H)) {
            dragging = true; dragOX = (int) mx - winX; dragOY = (int) my - winY; return true;
        }
        // footer pills — shared across all window layouts
        for (int i = 0; i < footRects.size(); i++) {
            int[] r = footRects.get(i);
            if (ModuleCard.inside(mx, my, r[0], r[1], r[2], r[3])) {
                switch (i) {
                    case 0 -> {
                        openUri(dev.noxclient.discord.DiscordLink.get());
                        dev.noxclient.gui.Toasts.push("Discord invite opened", 0xFF8B5CF6);
                    }
                    case 1 -> dev.noxclient.discord.Webhook.reportBug();
                    case 2 -> dev.noxclient.discord.Webhook.suggest();
                    case 3 -> {
                        NoxClient.INSTANCE.config.share();
                        dev.noxclient.gui.Toasts.push("Config copied to clipboard", 0xFF66D9A0);
                    }
                    case 4 -> {
                        NoxClient.INSTANCE.config.importConfig(minecraft.keyboardHandler.getClipboard());
                        dev.noxclient.gui.Toasts.push("Config imported", 0xFF66D9A0);
                    }
                    case 5 -> minecraft.setScreenAndShow(new dev.noxclient.gui.ShopScreen(this));
                    case 6 -> {
                        NoxClient.INSTANCE.modules.get("GhostMode").setEnabled(true);
                        dev.noxclient.gui.Toasts.push("PANIC — all modules off", 0xFFFF5C7A);
                    }
                }
                return true;
            }
        }
        if (library()) {
            // left rail: All + categories
            int sby = winY + TITLE_H + 8 + 16;
            for (int i = -1; i < Category.values().length; i++) {
                if (ModuleCard.inside(mx, my, winX + 12, sby, LIB_SB_W - 8, 20)) {
                    libCat = i; scrollTarget = 0; ModuleCard.click(); return true;
                }
                sby += 22;
            }
            if (ModuleCard.inside(mx, my, libHudBtnX, libHudBtnY, libHudBtnW, SEARCH_H)) {
                Module hw = NoxClient.INSTANCE.modules.get("HudWidgets");
                if (hw != null) popup = new SettingsPopup(hw, (int) mx, (int) my, width, height);
                return true;
            }
            if (ModuleCard.inside(mx, my, libSearchX, libSearchY, libSearchW, SEARCH_H)) {
                searchFocused = true; return true;
            }
            searchFocused = false;
            for (int i = 0; i < libTiles.size(); i++) {
                int[] r = libTiles.get(i);
                if (!ModuleCard.inside(mx, my, r[0], r[1], r[2], r[3])) continue;
                Module m = libTileMods.get(i);
                if (btn == 2) { panelListening = panelListening == m ? null : m; return true; }
                if (btn == 1) {
                    if (!m.settings.isEmpty())
                        popup = new SettingsPopup(m, (int) mx, (int) my, width, height);
                    return true;
                }
                // LMB: ON/OFF strip toggles, gear opens settings, body → settings or toggle
                if (ModuleCard.inside(mx, my, r[0] + 4, r[1] + r[3] - 18, r[2] - 8, 14)) {
                    m.toggle(); ModuleCard.click(); return true;
                }
                if (ModuleCard.inside(mx, my, r[0] + r[2] - 18, r[1] + 2, 16, 14)
                        && !m.settings.isEmpty()) {
                    popup = new SettingsPopup(m, (int) mx, (int) my, width, height); return true;
                }
                if (!m.settings.isEmpty())
                    popup = new SettingsPopup(m, (int) mx, (int) my, width, height);
                else { m.toggle(); ModuleCard.click(); }
                return true;
            }
            return true;
        }
        if (sidebar()) {
            int sby = contentY();
            for (Category c : Category.values()) {
                if (ModuleCard.inside(mx, my, winX + 8, sby, SIDEBAR_W - 4, 20)) {
                    tab = c; activeTab = c.ordinal(); scrollTarget = 0; return true;
                }
                sby += 22;
            }
        } else {
            int tx = winX + 12, ty = winY + TITLE_H + 6;
            for (Category c : Category.values()) {
                int w = Gui.width(font, c.label) + 18;
                if (ModuleCard.inside(mx, my, tx, ty, w, TAB_H - 4)) { tab = c; activeTab = c.ordinal(); scrollTarget = 0; return true; }
                tx += w + 4;
            }
        }
        if (ModuleCard.inside(mx, my, hudBtnX, hudBtnY, 44, SEARCH_H)) {
            Module hw = NoxClient.INSTANCE.modules.get("HudWidgets");
            if (hw != null) popup = new SettingsPopup(hw, (int) mx, (int) my, width, height);
            return true;
        }
        if (ModuleCard.inside(mx, my, contentX(), winY + TITLE_H + (sidebar() ? 6 : TAB_H + 10),
                (sidebar() ? contentW() : winW - 24) - 50, SEARCH_H)) {
            searchFocused = true; return true;
        }
        searchFocused = false;

        for (ModuleCard c : visible()) {
            int r = c.mouseClicked(mx, my, btn);
            if (r == 2) { popup = new SettingsPopup(c.module, (int) mx, (int) my, width, height); return true; }
            if (r == 1) return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    private static void openUri(String url) {
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(new URI(url));
        } catch (Exception ignored) {}
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        dragging = resizing = false;
        panelDrag = null;
        if (popup != null) popup.mouseReleased();
        for (var list : cards.values()) for (ModuleCard c : list) c.mouseReleased();
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (popup != null) { popup.mouseDragged(e.x(), e.y()); return true; }
        if (panelDrag != null) {
            int perRow = panelsPerRow();
            int row = panelDrag.ordinal() / perRow, col = panelDrag.ordinal() % perRow;
            int inRow = Math.min(perRow, Category.values().length - row * perRow);
            int rowW = inRow * panelW() + (inRow - 1) * PANEL_GAP;
            int startX = Math.max(8, (width - rowW) / 2);
            int ox = (int) e.x() - panelDragOX - startX - col * (panelW() + PANEL_GAP);
            int oy = (int) e.y() - panelDragOY - PANEL_TOP - row * panelRowH();
            panelOff.put(panelDrag, new int[]{ox, Math.max(8 - PANEL_TOP - row * panelRowH(), oy)});
            return true;
        }
        if (dragging) {
            winX = Math.max(-winW + 60, Math.min(width - 40, (int) e.x() - dragOX));
            winY = Math.max(0, Math.min(height - 20, (int) e.y() - dragOY));
            return true;
        }
        if (resizing) {
            winW = (int) Math.max(MIN_W, Math.min(width - winX - 4, e.x() - winX));
            winH = (int) Math.max(MIN_H, Math.min(height - winY - 4, e.y() - winY));
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        if (popup != null) return true;
        if (panels()) {
            for (Category c : Category.values()) {
                int px = panelX(c), py = panelY(c);
                if (!ModuleCard.inside(mx, my, px, py, panelW(), PANEL_HEAD + panelBodyH(c))) continue;
                int max = Math.max(0, NoxClient.INSTANCE.modules.by(c).size() * ROW_H + 6 - panelBodyH(c));
                panelScroll.put(c, Math.max(0, Math.min(max, scrollOf(c) - vAmt * 20)));
                return true;
            }
            return true;
        }
        int tot = library()
            ? libTotalH() - (winY + winH - FOOT_H - 8 - (libSearchY + SEARCH_H + 12))
            : totalH() - contentH();
        scrollTarget = Math.max(0, Math.min(Math.max(0, tot), scrollTarget - vAmt * 28));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int key = e.key();
        if (popup != null && popup.keyPressed(key)) return true;
        if (panelListening != null) {
            panelListening.setKeybind(key == InputConstants.KEY_ESCAPE ? -1 : key);
            panelListening = null;
            return true;
        }
        for (var list : cards.values())
            for (ModuleCard c : list) if (c.keyPressed(key)) return true;
        if (key == InputConstants.KEY_ESCAPE && popup != null) { popup = null; return true; }
        if (searchFocused) {
            if (key == InputConstants.KEY_BACKSPACE && !search.isEmpty())
                search = search.substring(0, search.length() - 1);
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_ESCAPE) searchFocused = false;
            return true;
        }
        if (key == InputConstants.KEY_T) { Theme.cycle(); return true; }
        if (key == InputConstants.KEY_RSHIFT || key == InputConstants.KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (popup != null && popup.charTyped((char) e.codepoint())) return true;
        if (searchFocused && e.isAllowedChatCharacter()) { search += e.codepointAsString(); return true; }
        return super.charTyped(e);
    }

    @Override
    public void onClose() {
        open = false;
        current = null;
        NoxClient.INSTANCE.config.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
