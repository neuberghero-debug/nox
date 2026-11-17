package dev.noxclient.gui;

import dev.noxclient.gui.theme.Theme;
import dev.noxclient.module.render.Cosmetics;
import dev.noxclient.premium.LicenseManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * NOX cosmetics shop — card grid with filter tabs, one-click equip for
 * owned items and the Discord /buy handoff for premium keys (crypto).
 */
public class ShopScreen extends Screen {
    private record Item(String name, String desc, String kind, String value,
                        int priceUsd, boolean premiumOnly, int color) {}

    private static final List<Item> ITEMS = List.of(
        // — trails (free) —
        new Item("Flame Trail",   "Classic fire trail",            "trail", "Flame",   0, false, 0xFFFF7A2B),
        new Item("Hearts Trail",  "Cute heart particles",          "trail", "Hearts",  0, false, 0xFFFF5E8A),
        new Item("Cherry Trail",  "Sakura petals",                 "trail", "Cherry",  0, false, 0xFFFFB7D5),
        new Item("Notes Trail",   "Music notes while you walk",    "trail", "Notes",   0, false, 0xFF7AFFE0),
        new Item("Portal Trail",  "Ender portal sparks",           "trail", "Portal",  0, false, 0xFFB44DFF),
        new Item("Crits Trail",   "PvP crit sparkles",             "trail", "Crits",   0, false, 0xFFFFD870),
        // — trails (premium) —
        new Item("Soul Trail",    "Soul sand valley wisps",        "trail", "Soul",    4, true,  0xFF6FD8FF),
        new Item("Glow Trail",    "Deep-dark glow motes",          "trail", "Glow",    4, true,  0xFF39FFC4),
        new Item("Witch Trail",   "Purple witch magic",            "trail", "Witch",   4, true,  0xFFC96FFF),
        new Item("Void Trail",    "Falling ash — end dimension",   "trail", "Void",    6, true,  0xFF8A8AA0),
        new Item("Galaxy Trail",  "End rod starlight",             "trail", "Galaxy",  6, true,  0xFFFFE97A),
        new Item("Rainbow Trail", "Cycles every particle color",   "trail", "Rainbow", 8, true,  0xFFFF6EC7),
        // — auras (premium) —
        new Item("Halo",          "Glowing ring above your head",  "aura",  "Halo",      6, true,  0xFFFFE97A),
        new Item("Orbit",         "Twin soul-fire orbitals",       "aura",  "Orbit",     6, true,  0xFF6FD8FF),
        new Item("Wings",         "Soul-fire wing particles",      "aura",  "Wings",     8, true,  0xFF7AB8FF),
        new Item("Flame Ring",    "Spiraling fire column",         "aura",  "FlameRing", 6, true,  0xFFFF7A2B),
        new Item("Storm",         "Dark soul storm overhead",      "aura",  "Storm",     8, true,  0xFF9A6FFF),
        new Item("Tornado",       "Swirling cloud tornado",        "aura",  "Tornado",   8, true,  0xFFBFD8FF),
        new Item("Fountain",      "Starlight geyser above you",    "aura",  "Fountain",  8, true,  0xFFFFE97A),
        new Item("Galaxy Halo",   "Wobbling star ring",            "aura",  "Galaxy Halo",10, true, 0xFF6FD8FF),
        new Item("Helix",         "Twin rising DNA helix",         "aura",  "Helix",      8, true,  0xFF39FFC4),
        new Item("BlackHole",     "Swirling void + fire ring",     "aura",  "BlackHole",  12, true, 0xFFB44DFF),
        new Item("Ember Crown",   "Lava crown over your head",     "aura",  "EmberCrown", 10, true, 0xFFFF7A2B),
        new Item("Aurora",        "Rainbow shimmer all around",    "aura",  "Aurora",     10, true, 0xFFFF6EC7),
        new Item("Petals",        "Falling sakura shower",         "aura",  "Petals",     6, true,  0xFFFFB7D5),
        new Item("Spark",         "Electric crackle on your skin", "aura",  "Spark",      8, true,  0xFF7AFFE0),
        // — kill effects (premium) —
        new Item("Firework Kill", "Firework burst on kills",       "kill",  "Firework",  4, true,  0xFFFF7A2B),
        new Item("Soul Kill",     "Souls escape on kills",         "kill",  "Souls",     4, true,  0xFF6FD8FF),
        new Item("Nova Kill",     "End-rod nova on kills",         "kill",  "Nova",      4, true,  0xFFFFE97A),
        new Item("Blood Kill",    "Damage spray on kills",         "kill",  "Blood",     4, true,  0xFFFF4040),
        // — capes —
        new Item("NOX Cape",      "Branded NOX cape",              "cape",  "NOX",       10, true, 0xFFB44DFF),
        new Item("Inferno Cape",  "Ember-fire variant",            "cape",  "Inferno",   12, true, 0xFFFF7A2B),
        new Item("Void Cape",     "Abyssal teal variant",          "cape",  "Void",      12, true, 0xFF39FFC4),
        new Item("Animated Cape", "Living flowing gradient",       "cape",  "Animated",  16, true, 0xFFFF6EC7),
        new Item("Custom Cape",   "Upload your own PNG (nox/capes/)", "cape", "Custom", 20, true, 0xFFFFD870)
    );

    private static final String[] TABS = {"All", "Trails", "Auras", "Kill FX", "Capes"};
    private static final String[] KIND = {null, "trail", "aura", "kill", "cape"};

    private final Screen parent;
    private final List<int[]> rects = new ArrayList<>();   // item buttons {x,y,w,h,itemIdx}
    private final List<int[]> tabRects = new ArrayList<>();
    private int tab, scroll;
    private int[] activateRect;
    private final java.util.Map<String, Float> hoverT = new java.util.HashMap<>();

    private static int lerp(int a, int b, float t) {
        return (((a >>> 24 & 0xFF) + (int) (((b >>> 24 & 0xFF) - (a >>> 24 & 0xFF)) * t)) << 24)
            | (((a >>> 16 & 0xFF) + (int) (((b >>> 16 & 0xFF) - (a >>> 16 & 0xFF)) * t)) << 16)
            | (((a >>> 8 & 0xFF) + (int) (((b >>> 8 & 0xFF) - (a >>> 8 & 0xFF)) * t)) << 8)
            | ((a & 0xFF) + (int) (((b & 0xFF) - (a & 0xFF)) * t));
    }

    public ShopScreen(Screen parent) { super(Component.literal("NOX Shop")); this.parent = parent; }

    private List<Item> visible() {
        List<Item> out = new ArrayList<>();
        for (Item it : ITEMS) if (tab == 0 || it.kind.equals(KIND[tab])) out.add(it);
        return out;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        Theme t = Theme.current();
        var font = Minecraft.getInstance().font;
        Gui.backdrop(g, width, height, 1f);
        int w = Math.min(700, width - 40), h = Math.min(430, height - 40);
        int x0 = (width - w) / 2, y0 = (height - h) / 2;
        Gui.shadow(g, x0, y0, w, h, UICustom.radius);
        Gui.round(g, x0, y0, w, h, UICustom.radius, UICustom.winFill);
        Gui.windowFx(x0, y0, w, h, UICustom.radius);
        Gui.windowFrost(x0, y0, w, h, UICustom.radius);

        // header
        Gui.textBold(g, font, "N O X   S H O P", x0 + 16, y0 + 12, t.accent());
        Gui.text(g, font, ITEMS.size() + " cosmetics", x0 + 16, y0 + 24, UICustom.textMute);
        boolean prem = LicenseManager.premium();
        String tier = prem ? "★ PREMIUM · " + LicenseManager.daysLeft() + "d left" : "free tier";
        int tw = Gui.width(font, tier) + 16;
        Gui.pill(g, x0 + w - 16 - tw, y0 + 10, tw, 16, prem ? 0x33FFD870 : 0xFF1B1B28);
        Gui.text(g, font, tier, x0 + w - 16 - tw + 8, y0 + 14, prem ? 0xFFFFD870 : UICustom.textDim);
        boolean xHov = ModuleCard.inside(mx, my, x0 + w - 24, y0 + h - 28, 16, 16);
        Gui.textCenter(g, font, "✕", x0 + w - 16, y0 + h - 24, xHov ? 0xFFFF8888 : UICustom.textDim);

        if (!prem) {
            boolean ahov = ModuleCard.inside(mx, my, x0 + 16, y0 + h - 30, 150, 16);
            Gui.text(g, font, "Activate license key →",
                x0 + 16, y0 + h - 26, ahov ? t.text() : t.accent());
            activateRect = new int[]{x0 + 16, y0 + h - 30, 150, 16};
        } else activateRect = null;

        // filter tabs
        tabRects.clear();
        int tx = x0 + 16, ty = y0 + 36;
        for (int i = 0; i < TABS.length; i++) {
            int pw = Gui.width(font, TABS[i]) + 16;
            boolean on = tab == i;
            boolean hov = ModuleCard.inside(mx, my, tx, ty, pw, 17);
            Gui.pill(g, tx, ty, pw, 17, on ? t.accent() : hov ? UICustom.cardFillTop : UICustom.cardFillBottom);
            if (!on && hov) Gui.pill(g, tx, ty, pw, 17, 0x14FFFFFF);
            Gui.text(g, font, TABS[i], tx + 8, ty + 5, on ? 0xFFFFFFFF : UICustom.textDim);
            tabRects.add(new int[]{tx, ty, pw, 17});
            tx += pw + 6;
        }
        Gui.fill(g, x0 + UICustom.radius, ty + 23, x0 + w - UICustom.radius, ty + 24, UICustom.border);

        // 2-col card grid — animated hover lift, kind-colored edge, big preview tile
        rects.clear();
        List<Item> vis = visible();
        int cw = (w - 32 - 9) / 2, ch = 58;
        int gridY = ty + 30, gridH = y0 + h - 56 - gridY;
        Gui.clipOn(x0 + 8, gridY, x0 + w - 8, gridY + gridH);
        int iy = gridY - scroll;
        for (int vi = 0; vi < vis.size(); vi++) {
            Item it = vis.get(vi);
            int cx = x0 + 16 + (vi % 2) * (cw + 9), cy = iy + (vi / 2) * (ch + 7);
            if (cy + ch < gridY || cy > gridY + gridH) continue;
            boolean owned = !it.premiumOnly || prem;
            boolean eq = isEquipped(it);
            boolean hov = ModuleCard.inside(mx, my, cx, cy, cw, ch);
            // eased hover lift (120ms)
            float hvt = hoverT.computeIfAbsent(it.name, k -> 0f);
            hvt += ((hov ? 1f : 0f) - hvt) * (UICustom.reducedMotion ? 1f : 0.28f);
            hoverT.put(it.name, hvt);
            int lift = UICustom.reducedMotion ? 0 : (int) (-3 * hvt);
            int borderC = eq ? t.accent()
                : hov ? lerp(UICustom.border, it.color, 0.6f) : UICustom.border;
            Gui.roundBorder(g, cx, cy + lift, cw, ch, 9, borderC,
                lerp(UICustom.cardFillBottom, UICustom.cardFillTop, hvt));
            // kind-colored left edge
            if (eq || hvt > 0.05f)
                Gui.pill(g, cx + 2, cy + lift + 8, 2, ch - 16,
                    (eq ? t.accent() : it.color) & 0x00FFFFFF
                        | ((int) (0x30 + 0xCD * Math.max(hvt, eq ? 1 : 0))) << 24);
            // preview tile — rounded chip in item color, live animation inside
            int px = cx + 24, py = cy + lift + ch / 2;
            Gui.circle(g, px, py, 12, it.color & 0x33FFFFFF);
            Gui.circle(g, px, py, 9, 0xFF141821);
            Gui.roundBorder(g, px - 10, py - 10, 20, 20, 10, it.color & 0x66FFFFFF, 0);
            preview(g, it, px, py);
            Gui.textBold(g, font, it.name, cx + 42, cy + lift + 8, t.text());
            Gui.text(g, font, it.desc, cx + 42, cy + lift + 21, UICustom.textDim);
            // price / state pill — right edge
            String tag = eq ? "EQUIPPED" : owned ? (it.priceUsd == 0 ? "FREE" : "OWNED")
                        : "$" + it.priceUsd;
            int pw = Gui.width(font, tag) + 12;
            Gui.pill(g, cx + cw - 8 - pw, cy + lift + 8, pw, 14,
                eq ? t.accent() : owned ? 0xFF1B1B28 : 0xFF3A2A52);
            Gui.text(g, font, tag, cx + cw - 8 - pw + 6, cy + lift + 12,
                eq ? 0xFFFFFFFF : owned ? UICustom.textDim : 0xFFC9A0FF);
            // action button
            String btn = eq ? "Unequip" : owned ? "Equip" : "Buy $" + it.priceUsd;
            int bw = Gui.width(font, btn) + 14;
            boolean bHov = ModuleCard.inside(mx, my, cx + cw - 8 - bw, cy + lift + 33, bw, 16);
            Gui.pill(g, cx + cw - 8 - bw, cy + lift + 33, bw, 16,
                eq ? UICustom.cardFillTop : owned ? (bHov ? t.accent() : t.accent() & 0xBBFFFFFF)
                       : (bHov ? 0xFFB44DFF : 0xFF3A2A52));
            Gui.text(g, font, btn, cx + cw - 8 - bw + 7, cy + lift + 37,
                eq && !bHov ? UICustom.textDim : 0xFFFFFFFF);
            rects.add(new int[]{cx + cw - 8 - bw, cy + lift + 33, bw, 16, ITEMS.indexOf(it)});
        }
        Gui.clipOff();
        // scrollbar
        int rows = (vis.size() + 1) / 2, total = rows * (ch + 6);
        if (total > gridH) {
            int barH = Math.max(24, gridH * gridH / total);
            int barY = gridY + (gridH - barH) * scroll / Math.max(1, total - gridH);
            Gui.pill(g, x0 + w - 8, gridY, 3, gridH, 0xFF2A2A38);
            Gui.pill(g, x0 + w - 8, barY, 3, barH, t.accent());
        }
        Gui.textCenter(g, font, "Premium keys via /buy in the NOX Discord — crypto accepted",
            width / 2, y0 + h - 14, UICustom.textMute);
        super.extractRenderState(g, mx, my, dt);
    }

    /** Tiny live preview per kind: rising trail dots, orbiting aura ring,
     *  pulsing kill burst, waving cape stripe. */
    private static void preview(GuiGraphicsExtractor g, Item it, int cx, int cy) {
        if (UICustom.reducedMotion) return;
        double t = System.currentTimeMillis() / 1000.0 + it.name.length() * 0.7;
        int col = it.color;
        switch (it.kind) {
            case "trail" -> {
                for (int i = 0; i < 3; i++) {
                    double p = (t * 0.9 + i * 0.33) % 1.0;
                    int a = (int) (200 * (1 - p));
                    Gui.circle(g, cx + (int) (Math.sin(t * 3 + i * 2) * 6),
                        cy + 8 - (int) (p * 16), 1, (a << 24) | (col & 0xFFFFFF));
                }
            }
            case "aura" -> {
                for (int i = 0; i < 5; i++) {
                    double a = t * 1.6 + i * (Math.PI * 2 / 5);
                    Gui.circle(g, cx + (int) (Math.cos(a) * 13),
                        cy + (int) (Math.sin(a) * 13), 1, 0xCC000000 | (col & 0xFFFFFF));
                }
            }
            case "kill" -> {
                double p = (t * 0.8) % 1.0;
                int a = (int) (160 * (1 - p));
                Gui.circle(g, cx, cy, 6 + (int) (p * 9), (a << 24) | (col & 0xFFFFFF));
                Gui.circle(g, cx, cy, 4, col);
            }
            case "cape" -> {
                for (int i = -4; i <= 4; i++) {
                    int dy = (int) (Math.sin(t * 3 + i * 0.8) * 2);
                    Gui.pill(g, cx + i * 3 - 1, cy - 9 + dy, 3, 18,
                        (0xB0 << 24) | (col & 0xFFFFFF));
                }
            }
        }
    }

    private boolean isEquipped(Item it) {
        var mm = dev.noxclient.NoxClient.INSTANCE.modules;
        return switch (it.kind) {
            case "trail" -> mm.get("Cosmetics") instanceof Cosmetics c
                && c.trail.get().equals(it.value) && c.isEnabled();
            case "aura" -> mm.get("Cosmetics") instanceof Cosmetics c
                && c.aura.get().equals(it.value) && c.isEnabled();
            case "kill" -> mm.get("KillEffects") instanceof
                    dev.noxclient.module.render.KillEffects k
                && k.effect.get().equals(it.value) && k.isEnabled();
            case "cape" -> mm.get("Cosmetics") instanceof Cosmetics c
                && c.noxCape.on() && c.isEnabled() && c.capeStyle.get().equals(it.value);
            default -> false;
        };
    }

    private void equip(Item it) {
        var mm = dev.noxclient.NoxClient.INSTANCE.modules;
        switch (it.kind) {
            case "trail" -> {
                if (mm.get("Cosmetics") instanceof Cosmetics c) {
                    if (isEquipped(it)) { c.trail.set("None"); c.setEnabled(false); }
                    else { c.trail.set(it.value); c.setEnabled(true); }
                }
            }
            case "aura" -> {
                if (mm.get("Cosmetics") instanceof Cosmetics c) {
                    if (isEquipped(it)) { c.aura.set("None"); c.setEnabled(false); }
                    else { c.aura.set(it.value); c.setEnabled(true); }
                }
            }
            case "kill" -> {
                if (mm.get("KillEffects") instanceof
                        dev.noxclient.module.render.KillEffects k) {
                    if (isEquipped(it)) k.setEnabled(false);
                    else { k.effect.set(it.value); k.setEnabled(true); }
                }
            }
            case "cape" -> {
                if (mm.get("Cosmetics") instanceof Cosmetics c) {
                    if (isEquipped(it)) { c.noxCape.set(false); }
                    else { c.capeStyle.set(it.value); c.noxCape.set(true); c.setEnabled(true); }
                }
            }
        }
        Toasts.push((isEquipped(it) ? "Equipped " : "Unequipped ") + it.name, 0xFF66FF99);
    }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        int w = Math.min(700, width - 40), h = Math.min(430, height - 40);
        int x0 = (width - w) / 2, y0 = (height - h) / 2;
        if (ModuleCard.inside(e.x(), e.y(), x0 + w - 24, y0 + h - 28, 16, 16)) { onClose(); return true; }
        if (activateRect != null && ModuleCard.inside(e.x(), e.y(),
                activateRect[0], activateRect[1], activateRect[2], activateRect[3])) {
            minecraft.setScreenAndShow(new PremiumScreen(this)); return true;
        }
        for (int i = 0; i < tabRects.size(); i++) {
            int[] r = tabRects.get(i);
            if (ModuleCard.inside(e.x(), e.y(), r[0], r[1], r[2], r[3])) {
                tab = i; scroll = 0; ModuleCard.click(); return true;
            }
        }
        for (int[] r : rects) {
            if (!ModuleCard.inside(e.x(), e.y(), r[0], r[1], r[2], r[3])) continue;
            Item it = ITEMS.get(r[4]);
            if (!it.premiumOnly || LicenseManager.premium()) {
                equip(it);
            } else {
                try {
                    java.awt.Desktop.getDesktop().browse(
                        new java.net.URI(dev.noxclient.discord.DiscordLink.get()));
                } catch (Exception ignored) {}
                Toasts.push("Premium required — opened Discord", 0xFFFFD870);
            }
            return true;
        }
        return super.mouseClicked(e, dbl);
    }

    @Override public boolean mouseScrolled(double mx, double my, double h, double v) {
        int rows = (visible().size() + 1) / 2;
        int max = Math.max(0, rows * 60 - (Math.min(430, height - 40) - 122));
        scroll = Math.max(0, Math.min(max, scroll - (int) v * 24));
        return true;
    }

    @Override public boolean keyPressed(KeyEvent e) {
        if (e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            Minecraft.getInstance().setScreenAndShow(parent); return true;
        }
        return super.keyPressed(e);
    }

    @Override public boolean isPauseScreen() { return false; }
}
