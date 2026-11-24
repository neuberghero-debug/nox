package dev.noxclient.gui;

import dev.noxclient.NoxClient;
import dev.noxclient.discord.DiscordLink;
import dev.noxclient.gui.ds.DS;
import dev.noxclient.gui.nvg.Nvg;
import dev.noxclient.gui.nvg.NvgOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.system.MemoryStack;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * NOX main menu — full replacement for the vanilla TitleScreen (swapped in
 * Gui.setScreen). Left nav column over the animated sky: brand lockup,
 * primary "Play DonutSMP" CTA, secondary actions, tool chips. Session card
 * on the right at wide layouts. NVG post-pass draw with a plain-GuiGraphics
 * fallback when the NVG context is unavailable.
 */
public class NoxMenuScreen extends Screen {

    private static final String DONUT_HOST = "play.donutsmp.net";
    private static final Identifier NOX_MARK =
        Identifier.fromNamespaceAndPath("noxclient", "textures/icon.png");

    private static final int KIND_PRIMARY = 0, KIND_SECONDARY = 1, KIND_CHIP = 2;

    private record Btn(String label, String glyph, float x, float y, float w, float h,
                       int kind, Runnable act) {
        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Btn> btns = new ArrayList<>();
    private float[] hov = new float[0];
    private long openAt = System.currentTimeMillis();
    private long lastFrameNanos;
    private float fdt = 1f / 60f;
    private int fmx, fmy;
    // session card rect (right side)
    private float cardX, cardY, cardW, cardH;
    private boolean cardShown;
    // lazy NVG image handle for the logo mark
    private int iconKey = -1;
    private static byte[] iconPng;

    public NoxMenuScreen() { super(Component.literal("NOX")); }

    @Override
    protected void init() {
        openAt = System.currentTimeMillis();
        // first-run prompts the vanilla title used to surface
        var inst = NoxClient.INSTANCE;
        if (inst != null && inst.config != null && !inst.config.inviteShown) {
            inst.config.inviteShown = true;
            inst.config.save();
            Minecraft.getInstance().setScreenAndShow(new NoxInviteScreen(this));
            return;
        }
        if (!dev.noxclient.telemetry.Telemetry.asked())
            Minecraft.getInstance().setScreenAndShow(new PrivacyScreen(this));
    }

    /* ================= layout ================= */

    private void layout(int w, int h) {
        btns.clear();
        float cx = Math.max(30, w * 0.075f);
        float navW = Math.min(300, w - cx - 300);
        if (navW < 200) navW = Math.min(300, w - cx * 2);

        // stack: brand 54 + gap 24 + primary 46 + 8 + sec 40*2 + gap 8 + half 34 + gap 14 + chips 34
        float stackH = 54 + 24 + 46 + 8 + 40 * 2 + 8 + 34 + 14 + 34;
        float y = Math.max(16, (h - stackH) / 2f);
        float by = y + 54 + 24;

        btns.add(new Btn("Play DonutSMP", "donut", cx, by, navW, 46, KIND_PRIMARY,
            this::joinDonut));
        by += 46 + 8;
        btns.add(new Btn("Singleplayer", "cube", cx, by, navW, 40, KIND_SECONDARY,
            () -> open(new SelectWorldScreen(this))));
        by += 40 + 8;
        btns.add(new Btn("Multiplayer", "globe", cx, by, navW, 40, KIND_SECONDARY,
            () -> open(new JoinMultiplayerScreen(this))));
        by += 40 + 8;
        float half = (navW - 8) / 2f;
        btns.add(new Btn("Options", null, cx, by, half, 34, KIND_SECONDARY,
            () -> open(new OptionsScreen(this, minecraft != null ? minecraft.options : null))));
        btns.add(new Btn("Quit", null, cx + half + 8, by, half, 34, KIND_SECONDARY,
            () -> { if (minecraft != null) minecraft.stop(); }));
        by += 34 + 14;

        // tool chips — icon + label, even split
        String[][] chips = {
            {"Alts", "person"}, {"Style", "star"}, {"Shop", "coin"},
            {"GUI", "bolt"}, {"Discord", "chat"}
        };
        Runnable[] acts = {
            () -> open(new AltManagerScreen(this)),
            () -> open(new CustomizeScreen(this)),
            () -> open(new ShopScreen(this)),
            this::openGui,
            NoxMenuScreen::openDiscord
        };
        float chipW = (navW - (chips.length - 1) * 6) / chips.length;
        for (int i = 0; i < chips.length; i++)
            btns.add(new Btn(chips[i][0], chips[i][1], cx + i * (chipW + 6), by,
                chipW, 34, KIND_CHIP, acts[i]));

        if (hov.length != btns.size()) hov = new float[btns.size()];

        // right-side session card only when there is real estate for it
        cardShown = w >= 680;
        cardW = 210; cardH = 108;
        cardX = w - cardW - Math.max(24, w * 0.05f);
        cardY = Math.max(20, y);
    }

    private void joinDonut() {
        if (minecraft == null) return;
        try {
            ConnectScreen.startConnecting(this, minecraft,
                ServerAddress.parseString(DONUT_HOST),
                new ServerData("DonutSMP", DONUT_HOST, ServerData.Type.OTHER),
                false, null);
        } catch (Throwable t) {
            UiSounds.error();
        }
    }

    private void openGui() {
        open("Studio".equals(UICustom.clickStyle) ? new NoxClickGui() : new NoxWindowScreen());
    }

    private void open(Screen s) {
        if (minecraft != null) minecraft.setScreenAndShow(s);
    }

    private static void openDiscord() {
        try {
            if (java.awt.Desktop.isDesktopSupported())
                java.awt.Desktop.getDesktop().browse(new java.net.URI(DiscordLink.get()));
        } catch (Exception ignored) {}
    }

    /* ================= input ================= */

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        for (Btn b : btns)
            if (b.hit(e.x(), e.y())) {
                UiSounds.click();
                b.act().run();
                return true;
            }
        return true;   // swallow — clicks on empty menu area do nothing
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isPauseScreen() { return false; }

    /* ================= render ================= */

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        if (dev.noxclient.util.DebugConsole.hoverActive()) {
            mx = (int) dev.noxclient.util.DebugConsole.hoverX;
            my = (int) dev.noxclient.util.DebugConsole.hoverY;
        }
        fmx = mx; fmy = my;
        long now = System.nanoTime();
        fdt = lastFrameNanos == 0 ? 1f / 60f : Math.min(.05f, (now - lastFrameNanos) / 1e9f);
        lastFrameNanos = now;

        layout(width, height);

        // sky — animated mono black hole first; baked hero plate + raymarch
        // stay as fallbacks for weak GPUs / missing assets
        if (!AnimatedSky.render(g, width, height)
            && !dev.noxclient.render.MenuSky.render(g, width, height))
            Gargantua.render(g, width, height);

        // ctx is 0 until the first NVG frame (or forever, if init failed) —
        // draw the plain fallback while it's down; the NVG pass overdraws it.
        if (NvgOverlay.ctx() == 0) drawFallback(g, mx, my);
        // typed ops queue entries — call them OUTSIDE the generic-op drain
        NvgOverlay.vignette(width, height, 0.45f);
        NvgOverlay.op(this::drawAll);

        // lyrics overlay (feature parity with the old title)
        var inst = NoxClient.INSTANCE;
        if (inst != null && inst.modules != null) {
            var lm = inst.modules.get("LyricsSky");
            if (lm instanceof dev.noxclient.module.render.LyricsSky ls
                && ls.isEnabled() && ls.titleScreen.on())
                LyricsHud.render(g, width / 2, (int) (height * 0.78),
                    (int) (double) ls.lines.get());
        }
    }

    /** NVG-dead fallback: plain rects + vanilla font so the menu stays usable. */
    private void drawFallback(GuiGraphicsExtractor g, int mx, int my) {
        var font = minecraft.font;
        g.fill(0, 0, width, height, 0xB0080A10);
        for (Btn b : btns) {
            boolean hov = b.hit(mx, my);
            int fill = b.kind == KIND_PRIMARY ? DS.accent()
                : hov ? 0xFF1C222E : 0xFF161B26;
            g.fill((int) b.x, (int) b.y, (int) (b.x + b.w), (int) (b.y + b.h), fill);
            g.centeredText(font, b.label, (int) (b.x + b.w / 2),
                (int) (b.y + b.h / 2 - 4), DS.text());
        }
        if (!btns.isEmpty())
            g.text(font, "N O X", (int) btns.get(0).x, (int) (btns.get(0).y - 70), DS.text());
        g.text(font, "NOX v" + NoxClient.VERSION + " · MC 26.3", 8, height - 12, DS.muted());
    }

    /* ================= NVG draw ================= */

    private float easeOutCubic(float t) { return 1f - (float) Math.pow(1 - t, 3); }

    /** stagger-in: item i slides in 45ms after the previous, 280ms cubic. */
    private float introEase(int i) {
        if (UICustom.reducedMotion) return 1f;
        float t = (System.currentTimeMillis() - openAt - i * 45f) / 280f;
        return easeOutCubic(Math.clamp(t, 0f, 1f));
    }

    private void drawAll(long c, MemoryStack st) {
        float prevScale = Nvg.fontScale;
        Nvg.fontScale = 1f;
        int mx = fmx, my = fmy;
        int accent = DS.accent();
        int text = DS.text(), dim = DS.secondary(), mute = DS.muted();
        int cardT = DS.surfaceAlt(), cardB = DS.surface();
        int border = DS.border(), borderH = UICustom.borderHover;

        // hover eases
        for (int i = 0; i < btns.size(); i++) {
            Btn b = btns.get(i);
            float tgt = b.hit(mx, my) ? 1f : 0f;
            hov[i] += (tgt - hov[i]) * Math.min(1f, fdt * (UICustom.reducedMotion ? 1e9f : 14f));
        }

        // drifting nebula washes — gives the black hole colour + life
        float t = UICustom.reducedMotion ? 0f : NvgOverlay.time();
        int acc2 = DS.accent2();
        nebula(c, st,
            width * 0.60f + (float) Math.sin(t * .13f) * width * .05f,
            height * 0.20f + (float) Math.cos(t * .09f) * height * .05f,
            height * .60f, accent, .16f);
        nebula(c, st,
            width * 0.82f + (float) Math.sin(t * .07f + 2f) * width * .04f,
            height * 0.75f + (float) Math.cos(t * .11f) * height * .05f,
            height * .70f, acc2, .12f);
        nebula(c, st,
            width * 0.28f,
            height * 0.88f + (float) Math.sin(t * .10f) * height * .04f,
            height * .45f, accent, .08f);

        // darken left half behind the nav — sky stays visible right
        Nvg.hgrad(c, st, 0, 0, width * 0.62f, height, 0, 0xB006070C, 0x0006070C);
        Nvg.vgrad(c, st, 0, 0, width, height, 0, 0x33000000, 0x59000000);

        int i = 0;
        for (Btn b : btns) { drawBtn(c, st, b, i, accent, text, dim, cardT, cardB, border, borderH); i++; }
        drawBrand(c, st, accent, text, dim, mute);
        if (cardShown) drawSession(c, st, accent, text, dim, mute, border);
        drawFooter(c, st, accent, text, dim, mute);
        Nvg.fontScale = prevScale;
    }

    private void drawBtn(long c, MemoryStack st, Btn b, int idx, int accent, int text, int dim,
                         int cardT, int cardB, int border, int borderH) {
        float e = introEase(idx);
        if (e <= 0f) return;
        float slide = (1f - e) * -22f;
        float x = b.x + slide, y = b.y;
        float hv = hov[idx];
        float a = e;

        if (b.kind == KIND_PRIMARY) {
            // accent gradient CTA + hover glow + arrow
            int acc2 = DS.lighten(accent, .18f);
            Nvg.shadow(c, st, x, y + 2, b.w, b.h, 11, 14 + hv * 10,
                (accent & 0xFFFFFF) | ((int) ((0x3A + hv * 0x2A) * a) << 24));
            Nvg.hgrad(c, st, x, y, b.w, b.h, 11,
                Nvg.fade(accent, a), Nvg.fade(acc2, a));
            Nvg.insetHi(c, st, x, y, b.w, 11, 0x30FFFFFF);
            Nvg.roundB(c, st, x, y, b.w, b.h, 11, 0,
                Nvg.fade(DS.lerp(border, borderH, hv), a));
            int onA = DS.onAccent();
            Nvg.glyph(c, st, b.glyph, x + 20, y + b.h / 2, 15,
                Nvg.fade(onA, a));
            Nvg.text(c, st, "inter_sb", 14.5f, x + 40, y + b.h / 2 - 7f,
                b.label, Nvg.fade(onA, a));
            Nvg.text(c, st, "mono", 9f, x + 40, y + b.h / 2 + 7f,
                DONUT_HOST, Nvg.fade(onA, a * .6f));
            // arrow slides in on hover
            if (hv > 0.01f)
                Nvg.glyph(c, st, "bolt", x + b.w - 20 - hv * 4, y + b.h / 2, 11,
                    Nvg.fade(onA, a * hv * .9f));
        } else if (b.kind == KIND_SECONDARY) {
            int fa = (int) (0xE6 * a) << 24;
            int topC = DS.lerp(cardT, DS.lighten(cardT, .10f), hv) & 0xFFFFFF | fa;
            int botC = DS.lerp(cardB, DS.lighten(cardB, .10f), hv) & 0xFFFFFF | fa;
            Nvg.vgrad(c, st, x, y, b.w, b.h, b.h >= 40 ? 10 : 9, topC, botC);
            Nvg.insetHi(c, st, x, y, b.w, 10, (int) (0x14 * a) << 24 | 0xFFFFFF);
            Nvg.roundB(c, st, x, y, b.w, b.h, b.h >= 40 ? 10 : 9, 0,
                Nvg.fade(DS.lerp(border, borderH, hv), a));
            boolean hasGlyph = b.glyph != null;
            float tx = hasGlyph ? x + 38 : x;
            int align = hasGlyph ? NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE
                : NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE;
            if (hasGlyph)
                Nvg.glyph(c, st, b.glyph, x + 19, y + b.h / 2, 12,
                    Nvg.fade(DS.lerp(dim, accent, hv), a));
            Nvg.text(c, st, "inter_sb", 12.5f,
                hasGlyph ? tx : x + b.w / 2, y + b.h / 2,
                b.label, Nvg.fade(text, a), align);
        } else { // chip
            Nvg.round(c, st, x, y, b.w, b.h, 9,
                Nvg.fade(DS.lerp(0xB0141821, 0xE61C222E, hv), a));
            Nvg.roundB(c, st, x, y, b.w, b.h, 9, 0,
                Nvg.fade(DS.lerp(border, borderH, hv), a));
            Nvg.glyph(c, st, b.glyph, x + b.w / 2, y + 12, 10,
                Nvg.fade(DS.lerp(dim, accent, hv), a));
            Nvg.text(c, st, "inter", 8f, x + b.w / 2, y + 22,
                b.label, Nvg.fade(dim, a), NVG_ALIGN_CENTER | NVG_ALIGN_TOP);
        }
    }

    private void drawBrand(long c, MemoryStack st, int accent, int text, int dim, int mute) {
        float e = introEase(-2);   // brand leads the stagger
        // brand sits above the first button
        Btn first = btns.isEmpty() ? null : btns.get(0);
        if (first == null) return;
        float bx = first.x, by = first.y - 24 - 54;
        float slide = (1f - e) * -16f;
        float a = e;
        float ix = bx + slide;

        // logo mark: NVG image when ready, accent ring + glyph fallback
        int img = iconHandle();
        Nvg.shadow(c, st, ix, by, 46, 46, 13, 16, (accent & 0xFFFFFF) | ((int) (0x30 * a) << 24));
        if (img != 0) {
            Nvg.img(c, st, img, ix, by, 46, 46, 13, a);
        } else {
            Nvg.vgrad(c, st, ix, by, 46, 46, 13, Nvg.fade(cardTop(), a), Nvg.fade(cardBot(), a));
            Nvg.ring(c, st, ix + 23, by + 23, 14, 2.2f, Nvg.fade(accent, a));
            Nvg.glyph(c, st, "bolt", ix + 23, by + 23, 13, Nvg.fade(accent, a));
        }
        // wordmark
        Nvg.text(c, st, "inter_b", 30f, ix + 56, by + 4,
            "NOX", Nvg.fade(0xFFFFFFFF, a));
        float wx = ix + 56 + Nvg.textW(c, "inter_b", 30f, "NOX") + 8;
        // version pill
        String ver = "v" + NoxClient.VERSION;
        float vw = Nvg.textW(c, "inter_sb", 9f, ver) + 14;
        Nvg.roundB(c, st, wx, by + 10, vw, 16, 8f,
            Nvg.fade(accent & 0xFFFFFF | 0x33000000, a),
            Nvg.fade(accent & 0xFFFFFF | 0x66000000, a));
        Nvg.text(c, st, "inter_sb", 9f, wx + vw / 2, by + 18f, ver,
            Nvg.fade(0xFFE8E8F0, a), NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        // tagline
        Nvg.textL(c, st, "inter", 9f, ix + 57, by + 38,
            "DONUTSMP CLIENT", Nvg.fade(dim, a), 2.4f, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
        // accent hairline under the lockup
        Nvg.hgrad(c, st, ix, by + 52, 120, 1.5f, 0,
            Nvg.fade(accent, a * .8f), Nvg.fade(accent, 0));
    }

    private void drawSession(long c, MemoryStack st, int accent, int text, int dim,
                             int mute, int border) {
        float e = introEase(btns.size());
        float slide = (1f - e) * 18f;
        float x = cardX + slide, y = cardY, w = cardW, h = cardH;
        float a = e;

        Nvg.shadow(c, st, x, y, w, h, 12, 18, (int) (0x40 * a) << 24);
        Nvg.vgrad(c, st, x, y, w, h, 12,
            Nvg.fade(0xF2141821, a), Nvg.fade(0xF210131B, a));
        Nvg.insetHi(c, st, x, y, w, 12, (int) (0x14 * a) << 24 | 0xFFFFFF);
        Nvg.roundB(c, st, x, y, w, h, 12, 0, Nvg.fade(border, a));

        Nvg.textL(c, st, "inter_sb", 8f, x + 14, y + 12, "SESSION",
            Nvg.fade(mute, a), 2f, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
        Nvg.hgrad(c, st, x + 14, y + 26, w - 28, 1, 0,
            Nvg.fade(accent, a * .5f), Nvg.fade(accent, 0));

        var mc = Minecraft.getInstance();
        String acct = mc.getUser() != null ? mc.getUser().getName() : "offline";
        boolean premium = dev.noxclient.premium.LicenseManager.premium();
        int total = 0, on = 0;
        if (NoxClient.INSTANCE != null && NoxClient.INSTANCE.modules != null)
            for (var m : NoxClient.INSTANCE.modules.all()) { total++; if (m.isEnabled()) on++; }

        float ry = y + 36;
        Nvg.glyph(c, st, "person", x + 20, ry + 5, 10, Nvg.fade(accent, a));
        Nvg.text(c, st, "inter_sb", 11f, x + 34, ry, acct, Nvg.fade(text, a));

        ry += 20;
        Nvg.glyph(c, st, "star", x + 20, ry + 5, 9, Nvg.fade(premium ? 0xFFFFD870 : mute, a));
        Nvg.text(c, st, "inter", 10f, x + 34, ry,
            premium ? "Premium" : "Free",
            Nvg.fade(premium ? 0xFFFFD870 : dim, a));

        ry += 20;
        Nvg.glyph(c, st, "bolt", x + 20, ry + 5, 9, Nvg.fade(accent, a));
        Nvg.text(c, st, "inter", 10f, x + 34, ry,
            total + " modules · " + on + " active", Nvg.fade(dim, a));
    }

    private void drawFooter(long c, MemoryStack st, int accent, int text, int dim, int mute) {
        float a = introEase(btns.size() + 1);
        float y = height - 22;
        // hairline, accent → transparent
        Nvg.hgrad(c, st, 0, y - 8, width, 1, 0,
            Nvg.fade(accent, a * .5f), Nvg.fade(accent, 0));
        String left = "NOX " + NoxClient.VERSION + "  ·  Minecraft 26.3 (Modded)";
        String right = "© Mojang AB  ·  " + DiscordLink.shortLabel();
        Nvg.text(c, st, "mono", 8.5f, 12, y + 2, left, Nvg.fade(mute, a));
        Nvg.text(c, st, "mono", 8.5f, width - 12, y + 2, right, Nvg.fade(mute, a),
            NVG_ALIGN_RIGHT | NVG_ALIGN_TOP);
    }

    /** Soft radial colour puff — the "nebula" layer over the sky. */
    private static void nebula(long c, MemoryStack st, float cx, float cy, float rad,
                               int rgb, float alpha) {
        int c0 = 0xFF000000 | (rgb & 0xFFFFFF);
        NVGPaint p = nvgRadialGradient(c, cx, cy, rad * .08f, rad,
            Nvg.col(st, c0, alpha), Nvg.col(st, c0, 0), NVGPaint.malloc(st));
        nvgBeginPath(c); nvgRect(c, cx - rad, cy - rad, rad * 2, rad * 2);
        nvgFillPaint(c, p); nvgFill(c);
    }

    private int cardTop() { return DS.surfaceAlt(); }
    private int cardBot() { return DS.surface(); }

    private int iconHandle() {
        if (iconKey < 0) {
            if (iconPng == null) {
                try {
                    var id = NOX_MARK;
                    var res = Minecraft.getInstance().getResourceManager()
                        .getResource(id);
                    if (res.isPresent())
                        iconPng = res.get().open().readAllBytes();
                } catch (Throwable ignored) {}
                if (iconPng == null) iconPng = new byte[0];
            }
            if (iconPng.length > 0) iconKey = NvgOverlay.imageKey(iconPng);
        }
        return iconKey > 0 ? NvgOverlay.imageHandle(iconKey) : 0;
    }
}
