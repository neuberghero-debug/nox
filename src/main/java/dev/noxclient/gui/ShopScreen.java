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
 * NOX cosmetics shop — free + premium cosmetics with one-click equip.
 * Premium items need a valid license; the buy button drops into the
 * Discord /buy flow where keys are issued for crypto.
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

    private final Screen parent;
    private final List<int[]> rects = new ArrayList<>();
    private int scroll;

    public ShopScreen(Screen parent) { super(Component.literal("NOX Shop")); this.parent = parent; }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        Theme t = Theme.current();
        var font = Minecraft.getInstance().font;
        int w = Math.min(460, width - 40), x0 = (width - w) / 2, y0 = Math.max(24, height / 2 - 200);
        Gui.shadow(g, x0, y0, w, 400, UICustom.radius);
        Gui.round(g, x0, y0, w, 400, UICustom.radius, UICustom.winFill);
        Gui.textBold(g, font, "N O X   S H O P", x0 + 16, y0 + 12, t.accent());
        boolean prem = LicenseManager.premium();
        String tier = prem ? "§lPREMIUM" : "free tier";
        Gui.text(g, font, tier, x0 + w - 16 - Gui.width(font, tier), y0 + 12,
            prem ? 0xFFFFD870 : 0xFF6A6A80);
        g.fill(x0 + 16, y0 + 30, x0 + w - 16, y0 + 31, UICustom.border);

        rects.clear();
        int iy = y0 + 38 - scroll, cardH = 46;
        g.enableScissor(x0 + 8, y0 + 34, x0 + w - 8, y0 + 400 - 46);
        String lastGroup = "";
        for (Item it : ITEMS) {
            String grp = it.kind;
            if (!grp.equals(lastGroup)) {
                lastGroup = grp;
                String lbl = switch (grp) {
                    case "trail" -> "— TRAILS —";
                    case "aura" -> "— AURAS —";
                    case "kill" -> "— KILL EFFECTS —";
                    default -> "— MISC —";
                };
                if (iy + 18 > y0 + 34 && iy < y0 + 400 - 46)
                    Gui.textCenter(g, font, lbl, x0 + w / 2, iy + 4, 0xFF5A5A6E);
                iy += 18;
            }
            if (iy + cardH > y0 + 34 && iy < y0 + 400 - 46) {
                boolean owned = !it.premiumOnly || prem;
                Gui.roundBorder(g, x0 + 14, iy, w - 28, cardH - 6, 8,
                    UICustom.border, UICustom.cardFillBottom);
                Gui.circle(g, x0 + 30, iy + 20, 8, it.color);
                Gui.textBold(g, font, it.name, x0 + 48, iy + 8, t.text());
                Gui.text(g, font, it.desc, x0 + 48, iy + 21, 0xFF7C7C90);
                String btn = owned ? (isEquipped(it) ? "EQUIPPED" : "EQUIP")
                                   : "$" + it.priceUsd + "  —  BUY";
                int bw = Gui.width(font, btn) + 16;
                boolean hov = ModuleCard.inside(mx, my, x0 + w - 14 - bw - 10, iy + 8, bw + 10, 24);
                Gui.round(g, x0 + w - 14 - bw - 10, iy + 8, bw + 10, 24, 6,
                    isEquipped(it) ? t.accent() : owned ? (hov ? t.accent() : 0xFF32324A)
                                   : (hov ? 0xFFB44DFF : 0xFF3A2A52));
                Gui.textCenter(g, font, btn, x0 + w - 14 - bw - 10 + (bw + 10) / 2, iy + 15,
                    owned || hov ? 0xFFFFFFFF : 0xFFC9A0FF);
                rects.add(new int[]{x0 + w - 14 - bw - 10, iy + 8, bw + 10, 24, ITEMS.indexOf(it)});
            }
            iy += cardH;
        }
        g.disableScissor();
        Gui.textCenter(g, font, "Premium keys via /buy in the NOX Discord — crypto accepted",
            width / 2, y0 + 400 - 30, 0xFF5A5A6E);
        Gui.textCenter(g, font, dev.noxclient.discord.DiscordLink.get(),
            width / 2, y0 + 400 - 17, t.accent());
        super.extractRenderState(g, mx, my, dt);
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
                    c.trail.set(it.value); c.setEnabled(true);
                }
            }
            case "aura" -> {
                if (mm.get("Cosmetics") instanceof Cosmetics c) {
                    c.aura.set(it.value); c.setEnabled(true);
                }
            }
            case "kill" -> {
                if (mm.get("KillEffects") instanceof
                        dev.noxclient.module.render.KillEffects k) {
                    k.effect.set(it.value); k.setEnabled(true);
                }
            }
            case "cape" -> {
                if (mm.get("Cosmetics") instanceof Cosmetics c) {
                    c.capeStyle.set(it.value); c.noxCape.set(true); c.setEnabled(true);
                }
            }
        }
        Toasts.push("Equipped " + it.name, 0xFF66FF99);
    }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
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
        scroll = Math.max(0, Math.min(ITEMS.size() * 46 + 80 - 320, scroll - (int) v * 24));
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
