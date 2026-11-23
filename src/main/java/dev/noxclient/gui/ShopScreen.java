package dev.noxclient.gui;

import dev.noxclient.gui.ds.DS;
import dev.noxclient.gui.theme.Theme;
import dev.noxclient.module.render.Cosmetics;
import dev.noxclient.premium.LicenseManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * NOX cosmetics shop — 3D try-on: the left panel renders your actual skin on
 * a turntable with the selected cosmetic previewed live (every item can be
 * tried before buying — that's the hook). Right column: filter tabs + the
 * item list. Premium items unlock via the Discord /buy handoff (crypto).
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
    private final List<int[]> rects = new ArrayList<>();      // CTA + row hitboxes {x,y,w,h,itemIdx}
    private final List<int[]> tabRects = new ArrayList<>();
    private int tab, scroll;
    private int[] activateRect;
    private int[] ctaRect;
    private int[] previewRect;
    private final java.util.Map<String, Float> hoverT = new java.util.HashMap<>();

    // — preview state —
    private int selIdx = -1;                 // ITEMS index under preview (-1 = first visible)
    private float selGlow;                   // lerped selection glow
    private long tabAnimAt;                  // stagger anchor for row entrance
    private float dragYaw;                   // user drag offset on the turntable
    private int dragStartX = -1;
    private float dragBase;
    private AvatarRenderState avatar;        // cached render state (model wired)
    private volatile PlayerSkin resolvedSkin;
    private boolean skinFetchStarted;
    private static Throwable entityErr;      // first preview failure (debug)
    private static boolean dbgTheme;

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

    private Item selected() {
        List<Item> vis = visible();
        for (Item it : vis) if (ITEMS.indexOf(it) == selIdx) return it;
        return vis.isEmpty() ? ITEMS.get(0) : vis.get(0);
    }

    // ---------- 3D preview ----------

    /** Own skin in-game; real account skin via SkinManager on the menu;
     *  DefaultPlayerSkin until the fetch resolves. */
    private PlayerSkin previewSkin() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) return mc.player.getSkin();
        PlayerSkin r = resolvedSkin;
        if (r != null) return r;
        if (!skinFetchStarted) {
            skinFetchStarted = true;
            try {
                mc.getSkinManager().get(mc.getGameProfile())
                    .thenAccept(opt -> resolvedSkin = opt.orElse(null));
            } catch (Throwable ignored) {}
        }
        return net.minecraft.client.resources.DefaultPlayerSkin.get(mc.getGameProfile());
    }

    private AvatarRenderState avatarState(PlayerSkin skin) {
        if (avatar == null || !skin.equals(avatar.skin)) {
            Minecraft mc = Minecraft.getInstance();
            EntityRenderDispatcher d = mc.getEntityRenderDispatcher();
            AvatarRenderState probe = new AvatarRenderState();
            probe.skin = skin;
            AvatarRenderer<?> r = d.getRenderer(probe);
            if (r == null) {
                if (entityErr == null) { entityErr = new IllegalStateException("no AvatarRenderer");
                    entityErr.printStackTrace(); }
                return null;
            }
            AvatarRenderState st = r.createRenderState();
            st.entityType = EntityTypes.PLAYER;
            st.skin = skin;
            st.boundingBoxWidth = 0.6f;
            st.boundingBoxHeight = 1.8f;
            st.eyeHeight = 1.62f;
            st.scale = 1f;
            st.lightCoords = 0xF000F0;
            st.pose = Pose.STANDING;
            st.showHat = st.showJacket = st.showLeftPants = st.showRightPants = true;
            st.showLeftSleeve = st.showRightSleeve = true;
            st.id = -1;                        // never matches the real player (CapeMixin guard)
            avatar = st;
        }
        return avatar;
    }

    /** Cape texture per item so the turntable previews the real asset. */
    private static ClientAsset.Texture capeFor(String value) {
        String path = switch (value) {
            case "Inferno" -> "textures/cape/nox_cape_inferno.png";
            case "Void"    -> "textures/cape/nox_cape_void.png";
            default        -> "textures/cape/nox_cape.png";
        };
        return new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath("noxclient", path));
    }

    /** Complete render state from the live player — the same object the world
     *  renderer produces, so every field the AvatarRenderer needs is real. */
    private AvatarRenderState extractedAvatar(Item it) {
        Minecraft mc = Minecraft.getInstance();
        try {
            var r = mc.getEntityRenderDispatcher().getRenderer(mc.player);
            if (!(r.createRenderState(mc.player, 1f) instanceof AvatarRenderState st)) return null;
            st.shadowPieces.clear();
            st.outlineColor = 0;
            return st;
        } catch (Throwable t) {
            if (entityErr == null) { entityErr = t; t.printStackTrace(); }
            return null;
        }
    }

    private void renderPlayerPreview(GuiGraphicsExtractor g, Item it, int x, int y, int w, int h,
                                     int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        AvatarRenderState st;
        if (mc.player != null) {
            st = extractedAvatar(it);
        } else {
            st = avatarState(previewSkin());
            if (st != null) st.ageInTicks = (System.currentTimeMillis() % 100000L) / 50f;
        }
        if (st == null || st.skin == null) return;
        // cape items preview the real cape asset on the skin
        if ("cape".equals(it.kind)) {
            var cap = capeFor(it.value);
            st.skin = PlayerSkin.insecure(st.skin.body(), cap, cap, st.skin.model());
            st.showCape = true;
        }
        // vanilla PiP recipe: normalize bbox by scale, then render at scale 1
        if (st.scale <= 0) st.scale = 1f;
        st.boundingBoxWidth /= st.scale;
        st.boundingBoxHeight /= st.scale;
        st.scale = 1f;
        // turntable: slow auto-spin + drag offset (vanilla front-facing = 180°)
        float spin = (System.currentTimeMillis() % 9000L) / 9000f * 360f + dragYaw;
        st.bodyRot = 180f + spin;
        st.yRot = 0f;
        st.xRot = 0f;
        Quaternionf ori = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf tilt = new Quaternionf().rotateX(-0.10f);
        ori.mul(tilt);
        int scale = Math.min((int) (h * 0.42), w);
        try {
            g.entity(st, scale,
                new Vector3f(0, st.boundingBoxHeight / 2f + 0.06f, 0),
                ori, tilt, x, y, x + w, y + h);
        } catch (Throwable t) {
            if (entityErr == null) { entityErr = t; t.printStackTrace(); }
        }   // preview must never break the screen
    }

    /** Kind-specific particle layer over the 3D figure. */
    private static void previewParticles(GuiGraphicsExtractor g, Item it, int cx, int feetY) {
        if (UICustom.reducedMotion) return;
        double t = System.currentTimeMillis() / 1000.0;
        int col = it.color;
        switch (it.kind) {
            case "trail" -> {
                for (int i = 0; i < 5; i++) {
                    double p = (t * 0.55 + i * 0.2) % 1.0;
                    int a = (int) (220 * (1 - p));
                    Gui.circle(g, cx + (int) (Math.sin(t * 2 + i * 1.7) * 14),
                        feetY - 4 - (int) (p * 44), 1 + (i % 2), (a << 24) | (col & 0xFFFFFF));
                }
            }
            case "aura" -> {
                int headY = feetY - 58;
                for (int i = 0; i < 7; i++) {
                    double a = t * 1.4 + i * (Math.PI * 2 / 7);
                    int al = (int) (120 + 100 * (0.5 + 0.5 * Math.sin(a)));
                    Gui.circle(g, cx + (int) (Math.cos(a) * 20),
                        headY + (int) (Math.sin(a) * 6), 1, (al << 24) | (col & 0xFFFFFF));
                }
            }
            case "kill" -> {
                double p = (t * 0.7) % 1.0;
                int a = (int) (170 * (1 - p));
                Gui.circle(g, cx, feetY - 30, 8 + (int) (p * 26), (a << 24) | (col & 0xFFFFFF));
                Gui.circle(g, cx, feetY - 30, 5, (0xAA << 24) | (col & 0xFFFFFF));
            }
            case "cape" -> {
                for (int i = -5; i <= 5; i++) {
                    int dy = (int) (Math.sin(t * 2.4 + i * 0.7) * 3);
                    Gui.pill(g, cx + i * 4 - 1, feetY - 56 + dy, 3, 30,
                        (0x70 << 24) | (col & 0xFFFFFF));
                }
            }
        }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        Theme t = Theme.current();

        var font = Minecraft.getInstance().font;
        Gui.backdrop(g, width, height, 1f);
        int w = Math.min(720, width - 40), h = Math.min(430, height - 40);
        int x0 = (width - w) / 2, y0 = (height - h) / 2;
        // window surface in the DEFERRED batch — the 3D PiP preview renders in the
        // same batch, so any NVG op over its rect would paint over the model
        SmoothShapes.roundBorder(g, x0, y0, w, h, (int) UICustom.radius,
            UICustom.border, UICustom.winFill);
        Gui.windowFx(x0, y0, w, h, UICustom.radius);
        Gui.windowFrost(x0, y0, w, h, UICustom.radius);

        // header
        Gui.textBold(g, font, "N O X   S H O P", x0 + 16, y0 + 12, t.accent());
        Gui.text(g, font, ITEMS.size() + " cosmetics — try anything on", x0 + 16, y0 + 24, UICustom.textMute);
        boolean prem = LicenseManager.premium();
        String tier = prem ? "★ PREMIUM · " + LicenseManager.daysLeft() + "d left" : "free tier";
        int tw = Gui.width(font, tier) + 16;
        Gui.pill(g, x0 + w - 16 - tw, y0 + 10, tw, 16, prem ? 0x33FFB84D : 0xFF101014);
        Gui.text(g, font, tier, x0 + w - 16 - tw + 8, y0 + 14, prem ? 0xFFFFB84D : UICustom.textDim);
        boolean xHov = ModuleCard.inside(mx, my, x0 + w - 24, y0 + h - 28, 16, 16);
        Gui.textCenter(g, font, "✕", x0 + w - 16, y0 + h - 24, xHov ? 0xFFFF8888 : UICustom.textDim);

        if (!prem) {
            boolean ahov = ModuleCard.inside(mx, my, x0 + 16, y0 + h - 30, 150, 16);
            Gui.text(g, font, "Activate license key →",
                x0 + 16, y0 + h - 26, ahov ? t.text() : t.accent());
            activateRect = new int[]{x0 + 16, y0 + h - 30, 150, 16};
        } else activateRect = null;

        // ============ LEFT: 3D try-on panel ============
        Item sel = selected();
        selGlow += (1f - selGlow) * 0.12f;
        int pw = Math.min(224, w * 32 / 100), ph = h - 66;
        int px = x0 + 14, py = y0 + 40;
        int glowC = lerp(UICustom.border, sel.color, 0.5f + 0.3f * selGlow);
        // preview surface must stay in the deferred batch (PiP renders on top of it)
        SmoothShapes.roundBorder(g, px, py, pw, ph, 10, glowC, UICustom.cardFillBottom);
        // radial stage glow — deferred rings, smooth alpha ramp (NVG ops would
        // cover the 3D model)
        int scx = px + pw / 2, scy = py + ph / 2 - 12;
        for (int i = 9; i >= 0; i--) {
            int a = (int) ((0x2E - i * 2.4f) * (0.6f + 0.4f * selGlow));
            if (a <= 0) continue;
            SmoothShapes.circle(g, scx, scy, 24 + i * 8,
                (a << 24) | (sel.color & 0xFFFFFF));
        }
        // floor disc
        SmoothShapes.round(g, scx - 34, py + ph - 92, 68, 10, 5,
            (sel.color & 0xFFFFFF) | 0x2E000000);
        previewRect = new int[]{px + 6, py + 8, pw - 12, ph - 108};
        renderPlayerPreview(g, sel, px + 10, py + 14, pw - 20, ph - 110, mx, my);
        previewParticles(g, sel, scx, py + ph - 96);
        // "drag" hint
        Gui.textCenter(g, font, "⟲ drag to spin", scx, py + 10, UICustom.textMute);

        // selected item caption + CTA inside the panel
        int capY = py + ph - 88;
        Gui.textBold(g, font, sel.name, px + 12, capY, t.text());
        Gui.text(g, font, sel.desc, px + 12, capY + 11, UICustom.textDim);
        boolean owned = !sel.premiumOnly || prem;
        boolean eq = isEquipped(sel);
        String price = eq ? "equipped" : owned ? (sel.priceUsd == 0 ? "free" : "owned")
            : "$" + sel.priceUsd + " · premium";
        Gui.text(g, font, price, px + 12, capY + 22,
            eq ? 0xFF4DFFA0 : owned ? UICustom.textDim : 0xFFFFB84D);

        // CTA — big accent button; pulse when it buys/equips something
        String cta = eq ? "Unequip" : owned ? "Equip" : "Unlock $" + sel.priceUsd;
        int bw = pw - 24, bh = 24;
        boolean bhov = ModuleCard.inside(mx, my, px + 12, py + ph - 38, bw, bh);
        float pulse = owned && !eq ? (0.85f + 0.15f * (float) Math.sin(System.currentTimeMillis() / 240.0)) : 1f;
        int ctaFill = eq ? UICustom.cardFillTop
            : (bhov ? DS.lighten(t.accent(), 0.1f) : t.accent());
        Gui.pill(g, px + 12, py + ph - 38, bw, bh,
            owned && !eq ? DS.withAlpha(ctaFill, (int) (255 * pulse)) : ctaFill);
        Gui.textCenter(g, font, cta, px + 12 + bw / 2, py + ph - 30,
            eq ? t.text() : DS.onAccent());
        ctaRect = new int[]{px + 12, py + ph - 38, bw, bh};

        // ============ RIGHT: tabs + item list ============
        int rx = px + pw + 14, rw = x0 + w - 16 - rx;
        tabRects.clear();
        int tx = rx, ty = y0 + 40;
        for (int i = 0; i < TABS.length; i++) {
            int pw2 = Gui.width(font, TABS[i]) + 16;
            boolean on = tab == i;
            boolean hov = ModuleCard.inside(mx, my, tx, ty, pw2, 17);
            Gui.pill(g, tx, ty, pw2, 17, on ? t.accent() : hov ? UICustom.cardFillTop : UICustom.cardFillBottom);
            if (!on && hov) Gui.pill(g, tx, ty, pw2, 17, 0x14FFFFFF);
            Gui.text(g, font, TABS[i], tx + 8, ty + 5, on ? DS.onAccent() : UICustom.textDim);
            tabRects.add(new int[]{tx, ty, pw2, 17});
            tx += pw2 + 6;
        }
        Gui.fill(g, rx + 4, ty + 23, x0 + w - UICustom.radius, ty + 24, UICustom.border);

        rects.clear();
        List<Item> vis = visible();
        int ch = 44, gridY = ty + 30, gridH = y0 + h - 46 - gridY;
        long now = System.currentTimeMillis();
        Gui.clipOn(rx - 2, gridY, x0 + w - 6, gridY + gridH);
        int iy = gridY - scroll;
        for (int vi = 0; vi < vis.size(); vi++) {
            Item it = vis.get(vi);
            int cy = iy + vi * (ch + 6);
            if (cy + ch < gridY || cy > gridY + gridH) continue;
            int idx = ITEMS.indexOf(it);
            boolean on = idx == selIdx || (selIdx < 0 && vi == 0);
            boolean itOwned = !it.premiumOnly || prem;
            boolean itEq = isEquipped(it);
            boolean hov = ModuleCard.inside(mx, my, rx, cy, rw, ch);
            float hvt = hoverT.computeIfAbsent(it.name, k -> 0f);
            hvt += (((hov || on) ? 1f : 0f) - hvt) * (UICustom.reducedMotion ? 1f : 0.28f);
            hoverT.put(it.name, hvt);
            // staggered entrance on tab switch
            float enter = UICustom.reducedMotion ? 1f
                : Math.clamp((now - tabAnimAt - vi * 28L) / 160f, 0f, 1f);
            int slide = (int) ((1f - enter) * 8);
            int alpha = (int) (255 * Math.min(1f, 0.2f + 0.8f * enter));
            int borderC = on ? t.accent()
                : itEq ? lerp(UICustom.border, it.color, 0.5f)
                : hvt > 0.4f ? lerp(UICustom.border, it.color, 0.5f) : UICustom.border;
            Gui.roundBorder(g, rx, cy + slide, rw, ch, 8, borderC,
                DS.withAlpha(lerp(UICustom.cardFillBottom, UICustom.cardFillTop, hvt), alpha));
            if (on || itEq)
                Gui.pill(g, rx + 2, cy + slide + 7, 2, ch - 14,
                    (on ? t.accent() : it.color) & 0x00FFFFFF | (alpha << 24) & 0xF0FFFFFF | 0xC0000000);
            // icon chip
            int ix = rx + 22, iy2 = cy + slide + ch / 2;
            Gui.circle(g, ix, iy2, 10, (it.color & 0x30FFFFFF));
            Gui.circle(g, ix, iy2, 7, 0xFF101014);
            miniIcon(g, it, ix, iy2);
            Gui.textBold(g, font, it.name, rx + 40, cy + slide + 7,
                DS.withAlpha(t.text(), alpha));
            Gui.text(g, font, it.desc, rx + 40, cy + slide + 19,
                DS.withAlpha(UICustom.textDim, alpha));
            String tag = itEq ? "EQUIPPED" : itOwned ? (it.priceUsd == 0 ? "FREE" : "OWNED")
                        : "$" + it.priceUsd;
            int pw3 = Gui.width(font, tag) + 12;
            Gui.pill(g, rx + rw - 10 - pw3, cy + slide + 7, pw3, 14,
                itEq ? t.accent() : itOwned ? 0xFF101014 : 0xFF241A10);
            Gui.text(g, font, tag, rx + rw - 10 - pw3 + 6, cy + slide + 11,
                itEq ? DS.onAccent() : itOwned ? UICustom.textDim : 0xFFFFB84D);
            rects.add(new int[]{rx, cy + slide, rw, ch, idx});
        }
        Gui.clipOff();
        // scrollbar
        int total = vis.size() * (ch + 6);
        if (total > gridH) {
            int barH = Math.max(24, gridH * gridH / total);
            int barY = gridY + (gridH - barH) * scroll / Math.max(1, total - gridH);
            Gui.pill(g, x0 + w - 8, gridY, 3, gridH, 0xFF1A1A20);
            Gui.pill(g, x0 + w - 8, barY, 3, barH, t.accent());
        }
        Gui.textCenter(g, font, "Premium keys via /buy in the NOX Discord — crypto accepted",
            (rx + x0 + w) / 2, y0 + h - 14, UICustom.textMute);
        super.extractRenderState(g, mx, my, dt);
    }

    /** Tiny animated glyph inside a row chip. */
    private static void miniIcon(GuiGraphicsExtractor g, Item it, int cx, int cy) {
        double t = System.currentTimeMillis() / 1000.0 + it.name.length() * 0.7;
        int col = UICustom.reducedMotion ? (0xAA << 24) | (it.color & 0xFFFFFF) : it.color;
        switch (it.kind) {
            case "trail" -> {
                for (int i = 0; i < 3; i++) {
                    double p = (t * 0.9 + i * 0.33) % 1.0;
                    int a = (int) (200 * (1 - p));
                    Gui.circle(g, cx + (int) (Math.sin(t * 3 + i * 2) * 3),
                        cy + 4 - (int) (p * 8), 1, (a << 24) | (it.color & 0xFFFFFF));
                }
            }
            case "aura" -> {
                for (int i = 0; i < 4; i++) {
                    double a = t * 1.6 + i * (Math.PI / 2);
                    Gui.circle(g, cx + (int) (Math.cos(a) * 5),
                        cy + (int) (Math.sin(a) * 5), 1, 0xCC000000 | (col & 0xFFFFFF));
                }
            }
            case "kill" -> {
                double p = (t * 0.8) % 1.0;
                Gui.circle(g, cx, cy, 3 + (int) (p * 4),
                    ((int) (160 * (1 - p)) << 24) | (col & 0xFFFFFF));
                Gui.circle(g, cx, cy, 2, col);
            }
            case "cape" -> {
                for (int i = -2; i <= 2; i++) {
                    int dy = (int) (Math.sin(t * 3 + i * 0.8) * 1);
                    Gui.pill(g, cx + i * 2 - 1, cy - 5 + dy, 2, 10,
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
        Toasts.push((isEquipped(it) ? "Equipped " : "Unequipped ") + it.name, 0xFF4DFFA0);
    }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        int w = Math.min(720, width - 40), h = Math.min(430, height - 40);
        int x0 = (width - w) / 2, y0 = (height - h) / 2;
        if (ModuleCard.inside(e.x(), e.y(), x0 + w - 24, y0 + h - 28, 16, 16)) { onClose(); return true; }
        if (activateRect != null && ModuleCard.inside(e.x(), e.y(),
                activateRect[0], activateRect[1], activateRect[2], activateRect[3])) {
            minecraft.setScreenAndShow(new PremiumScreen(this)); return true;
        }
        // preview drag start
        if (previewRect != null && ModuleCard.inside(e.x(), e.y(),
                previewRect[0], previewRect[1], previewRect[2], previewRect[3])) {
            dragStartX = (int) e.x(); dragBase = dragYaw; return true;
        }
        if (ctaRect != null && ModuleCard.inside(e.x(), e.y(),
                ctaRect[0], ctaRect[1], ctaRect[2], ctaRect[3])) {
            Item it = selected();
            if (!it.premiumOnly || LicenseManager.premium()) {
                equip(it); ModuleCard.click();
            } else {
                try {
                    java.awt.Desktop.getDesktop().browse(
                        new java.net.URI(dev.noxclient.discord.DiscordLink.get()));
                } catch (Exception ignored) {}
                Toasts.push("Premium required — opened Discord", 0xFFFFB84D);
            }
            return true;
        }
        for (int i = 0; i < tabRects.size(); i++) {
            int[] r = tabRects.get(i);
            if (ModuleCard.inside(e.x(), e.y(), r[0], r[1], r[2], r[3])) {
                tab = i; scroll = 0; tabAnimAt = System.currentTimeMillis();
                ModuleCard.click(); return true;
            }
        }
        for (int[] r : rects) {
            if (!ModuleCard.inside(e.x(), e.y(), r[0], r[1], r[2], r[3])) continue;
            selIdx = r[4]; selGlow = 0f;
            ModuleCard.click();
            return true;
        }
        return super.mouseClicked(e, dbl);
    }

    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragStartX >= 0) {
            dragYaw = dragBase + ((int) e.x() - dragStartX) * 0.9f;
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override public boolean mouseReleased(MouseButtonEvent e) {
        dragStartX = -1;
        return super.mouseReleased(e);
    }

    @Override public boolean mouseScrolled(double mx, double my, double h, double v) {
        int gridH = Math.min(430, height - 40) - 46 - 70;
        int max = Math.max(0, visible().size() * 50 - gridH);
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
