package dev.noxclient.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import dev.noxclient.gui.nvg.Nvg;
import dev.noxclient.gui.nvg.NvgOverlay;

/**
 * Shape/text helpers — EVERY method here queues a NanoVG op so all NOX screens
 * render as crisp vector UI (real AA, Inter type, gradients) in the overlay
 * post-pass instead of the batched vanilla pipeline. The only exception is
 * {@link #backdrop}: the dim/frost layer must stay UNDER the window, so it
 * keeps drawing through the deferred GUI batch (NVG always lands on top).
 */
public final class Gui {
    private Gui() {}

    /** Rounded rectangle. */
    public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        NvgOverlay.op((c, st) -> Nvg.round(c, st, x, y, w, h, r, color));
    }

    /** Rounded rect with a 1px border: inner fill + border stroke. */
    public static void roundBorder(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                   int r, int borderColor, int fillColor) {
        NvgOverlay.op((c, st) -> Nvg.roundB(c, st, x, y, w, h, r, fillColor, borderColor));
    }

    /** Pill — fully rounded capsule. */
    public static void pill(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        NvgOverlay.op((c, st) -> Nvg.round(c, st, x, y, w, h, h / 2f, color));
    }

    /* ---- float variants for vector icon drawing (icons live on a float grid) ---- */
    public static void round(GuiGraphicsExtractor g, float x, float y, float w, float h,
                             float r, int color) {
        NvgOverlay.op((c, st) -> Nvg.round(c, st, x, y, w, h, r, color));
    }
    public static void roundBorder(GuiGraphicsExtractor g, float x, float y, float w, float h,
                                   float r, int borderColor, int fillColor) {
        NvgOverlay.op((c, st) -> Nvg.roundB(c, st, x, y, w, h, r, fillColor, borderColor));
    }
    public static void pill(GuiGraphicsExtractor g, float x, float y, float w, float h, int color) {
        NvgOverlay.op((c, st) -> Nvg.round(c, st, x, y, w, h, h / 2f, color));
    }
    public static void circle(GuiGraphicsExtractor g, float cx, float cy, float r, int color) {
        NvgOverlay.op((c, st) -> Nvg.circle(c, st, cx, cy, r, color));
    }

    /** Filled circle. */
    public static void circle(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        NvgOverlay.op((c, st) -> Nvg.circle(c, st, cx, cy, r, color));
    }

    /** Soft drop shadow — real box-gradient blur. */
    public static void shadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int r) {
        NvgOverlay.op((c, st) -> Nvg.shadow(c, st, x, y, w, h, r, 9f, 0x50000000));
    }

    /** Plain axis-aligned fill (separators, scissor bars). */
    public static void rect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        NvgOverlay.op((c, st) -> Nvg.rect(c, st, x, y, w, h, color));
    }

    /** Drop-in for {@code g.fill(x0,y0,x1,y1,color)} — same END coords. */
    public static void fill(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        NvgOverlay.op((c, st) -> Nvg.rect(c, st, x0, y0, x1 - x0, y1 - y0, color));
    }

    /** Clip following NVG ops to a rect — pair with {@link #clipEnd}. */
    public static void clip(int x, int y, int w, int h) {
        NvgOverlay.op((c, st) -> Nvg.scissor(c, x, y, w, h));
    }
    public static void clipEnd() {
        NvgOverlay.op((c, st) -> Nvg.unScissor(c));
    }
    /** Drop-in for {@code g.enableScissor(x0,y0,x1,y1)} (END coords). */
    public static void clipOn(int x0, int y0, int x1, int y1) {
        NvgOverlay.op((c, st) -> Nvg.scissor(c, x0, y0, x1 - x0, y1 - y0));
    }
    /** Drop-in for {@code g.disableScissor()}. */
    public static void clipOff() { clipEnd(); }

    /* ---- Inter text (NVG vector glyphs — sizes chosen to match MC's 9px rows) ---- */
    private static final FontDescription F_NOX =
        new FontDescription.Resource(Identifier.fromNamespaceAndPath("noxclient", "nox"));
    private static final FontDescription F_NOX_BOLD =
        new FontDescription.Resource(Identifier.fromNamespaceAndPath("noxclient", "nox_bold"));
    private static final FontDescription F_MONO =
        new FontDescription.Resource(Identifier.fromNamespaceAndPath("noxclient", "nox_mono"));
    private static final java.util.Map<String, Component> TEXT = new java.util.LinkedHashMap<>(256, .75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Component> e) {
            return size() > 2048;
        }
    };
    private static final java.util.Map<String, Component> MONO_TXT = new java.util.LinkedHashMap<>(256, .75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Component> e) {
            return size() > 1024;
        }
    };

    private static Component monoStyled(String s) {
        return MONO_TXT.computeIfAbsent(s,
            k -> Component.literal(k).withStyle(st -> st.withFont(F_MONO)));
    }

    /** JetBrains Mono text — for numeric values (spec: all numbers in mono). */
    public static void mono(GuiGraphicsExtractor g, Font f, String s, int x, int y, int color) {
        if (s == null || s.isEmpty()) return;
        NvgOverlay.op((c, st) -> Nvg.text(c, st, "mono", 9f, x, y, s, color));
    }
    public static int monoWidth(Font f, String s) { return f.width(monoStyled(s)); }

    public static Component styled(String s, boolean bold) {
        String key = (bold ? "\1" : "\0") + s;
        Component result = TEXT.get(key);
        if (result == null) {
            result = Component.literal(s).withStyle(st -> st.withFont(bold ? F_NOX_BOLD : F_NOX));
            TEXT.put(key, result);
        }
        return result;
    }

    public static void text(GuiGraphicsExtractor g, Font f, String s, int x, int y, int color) {
        if (s == null || s.isEmpty()) return;
        NvgOverlay.op((c, st) -> Nvg.text(c, st, "inter", 9.5f, x, y, s, color));
    }

    /** Text with a soft drop shadow — the modern HUD look. */
    public static void textShadow(GuiGraphicsExtractor g, Font f, String s, int x, int y, int color) {
        if (s == null || s.isEmpty()) return;
        NvgOverlay.op((c, st) -> {
            Nvg.text(c, st, "inter", 9.5f, x + 1, y + 1, s, 0x88000000);
            Nvg.text(c, st, "inter", 9.5f, x, y, s, color);
        });
    }

    public static void textBold(GuiGraphicsExtractor g, Font f, String s, int x, int y, int color) {
        if (s == null || s.isEmpty()) return;
        NvgOverlay.op((c, st) -> Nvg.text(c, st, "inter_b", 9.5f, x, y, s, color));
    }

    public static void textCenter(GuiGraphicsExtractor g, Font f, String s, int cx, int y, int color) {
        if (s == null || s.isEmpty()) return;
        NvgOverlay.op((c, st) -> Nvg.text(c, st, "inter", 9.5f, cx, y, s, color,
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_TOP));
    }

    public static void textCenterBold(GuiGraphicsExtractor g, Font f, String s, int cx, int y, int color) {
        if (s == null || s.isEmpty()) return;
        NvgOverlay.op((c, st) -> Nvg.text(c, st, "inter_b", 9.5f, cx, y, s, color,
            org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_TOP));
    }

    public static int width(Font f, String s) {
        return f.width(styled(s, false));
    }

    /** Vertical gradient with rounded corners. */
    public static void vgrad(GuiGraphicsExtractor g, int x, int y, int w, int h, int r,
                             int top, int bottom) {
        NvgOverlay.op((c, st) -> Nvg.vgrad(c, st, x, y, w, h, r, top, bottom));
    }

    // ---------- backdrop (Dim | Frost | Prism) ----------
    private static float lvl() {
        return switch (UICustom.backdropLevel) {
            case "Light" -> 0.6f; case "Strong" -> 1.5f; default -> 1f;
        };
    }
    /** Full-screen backdrop. Vanilla already blurs once — frost is simulated
     * with tint layers. Everything here uses the deferred GUI batch so it lands
     * BEHIND the window — NVG fullscreen ops would paint over the GUI's text. */
    public static void backdrop(GuiGraphicsExtractor g, int w, int h, float ease) {
        float L = lvl();
        switch (UICustom.backdrop) {
            case "Frost" -> {
                g.fill(0, 0, w, h, ((int) (0x58 * L * ease)) << 24 | 0x0A1420);
                int a = (int) (0x24 * L * ease) << 24;
                edgeVignette(g, w, h, (int) (0x40 * L * ease));
            }
            case "Prism" -> {
                g.fill(0, 0, w, h, ((int) (0x62 * L * ease)) << 24 | 0x0E0A20);
                int a = (int) (0x28 * L * ease) << 24;
                vgrad(g, 0, 0, w, h / 3, 0, 0x9B4DFF & 0xFFFFFF | a, a);
                vgrad(g, 0, h * 2 / 3, w, h / 3, 0, 0x5AC8FF & 0xFFFFFF | a, a);
                edgeVignette(g, w, h, (int) (0x44 * L * ease));
            }
            default -> g.fill(0, 0, w, h, ((int) (0x66 * ease)) << 24 | 0x00050810);
        }
    }
    /** Screen-edge darkening drawn in the deferred batch (behind the window). */
    private static void edgeVignette(GuiGraphicsExtractor g, int w, int h, int alpha) {
        int t = Math.min(w, h) / 5;
        int dk = alpha << 24;
        g.fill(0, 0, w, t, dk);
        g.fill(0, h - t, w, t, dk);
        g.fill(0, 0, t, h, dk);
        g.fill(w - t, 0, t, h, dk);
    }
    public static void hgrad(GuiGraphicsExtractor g, int x, int y, int w, int h,
            int left, int right) {
        NvgOverlay.op((c, st) -> Nvg.hgrad(c, st, x, y, w, h, 0, left, right));
    }
    /** Per-window specular border FX for Prism mode. */
    public static void windowFx(int x, int y, int w, int h, float r) {
        if (!"Prism".equals(UICustom.backdrop)) return;
        float L = lvl();
        float ph = UICustom.reducedMotion ? 0f : dev.noxclient.physics.AnimationSystem.seconds() * 0.35f;
        int a = UICustom.accent & 0xFFFFFF;
        int b = hueShift(a, 20f / 360f);
        // chromatic glass wash across the whole pane — prism tints the window
        // itself, not only the border/space around it
        NvgOverlay.wash(x, y, w, h, r, a, b, 0.10f * L);
        NvgOverlay.wash(x, y + h * 0.55f, w, h * 0.45f, r, b, a, 0.06f * L);
        NvgOverlay.gradientStroke(x, y, w, h, r, a, b, 0.55f * L, ph);
        NvgOverlay.gradientStroke(x - 1, y - 1, w + 2, h + 2, r + 1, b, a, 0.22f * L, ph + 0.5f);
        NvgOverlay.innerTop(x, y, w, r, 0xFFFFFF, 0.20f * L);
        if (!UICustom.reducedMotion) NvgOverlay.comet(x, y, w, a, 0.25f * L, 6f);
    }
    /** Frost-mode window frosting pass — a slim top sheen only, so it never
     * milks over card text in the post-pass. */
    public static void windowFrost(int x, int y, int w, int h, float r) {
        if (!"Frost".equals(UICustom.backdrop)) return;
        float L = lvl();
        // cold milky wash over the whole pane + stronger top sheen = frozen glass
        NvgOverlay.wash(x, y, w, h, r, 0xCFE9FF, 0x8FB8E8, 0.09f * L);
        NvgOverlay.sheen(x, y, w, Math.min(h * 0.45f, 56f), r, 0xD8F0FF, 0.10f * L);
        NvgOverlay.innerTop(x, y, w, r, 0xFFFFFF, 0.14f * L);
    }
    private static int hueShift(int rgb, float turns) {
        float[] hsb = java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        return java.awt.Color.HSBtoRGB((hsb[0] + turns) % 1f, hsb[1], hsb[2]) & 0xFFFFFF;
    }
}
