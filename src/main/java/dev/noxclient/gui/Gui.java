package dev.noxclient.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/**
 * Modern-shape drawing helpers — rounded rects, pills, circles — built from
 * chord fills since the extractor only knows axis-aligned fills.
 */
public final class Gui {
    private Gui() {}

    /** Rounded rectangle — supersampled-texture corners (see SmoothShapes). */
    public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        SmoothShapes.round(g, x, y, w, h, r, color);
    }

    /** Rounded rect with a 1px border: smooth ring corners + inner fill. */
    public static void roundBorder(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                   int r, int borderColor, int fillColor) {
        SmoothShapes.roundBorder(g, x, y, w, h, r, borderColor, fillColor);
    }

    /** Pill — fully rounded capsule. */
    public static void pill(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        round(g, x, y, w, h, h / 2, color);
    }

    /** Filled circle via chord rows. */
    public static void circle(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        SmoothShapes.circle(g, cx, cy, r, color);
    }

    /** Soft drop shadow: 3 expanding translucent rounded layers. */
    public static void shadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int r) {
        round(g, x - 2, y - 1, w + 4, h + 5, r + 2, 0x1A000000);
        round(g, x - 1, y, w + 2, h + 4, r + 1, 0x28000000);
        round(g, x, y + 2, w, h + 4, r, 0x3C000000);
    }

    /* ---- Inter text ---- */
    private static final FontDescription F_NOX =
        new FontDescription.Resource(Identifier.fromNamespaceAndPath("noxclient", "nox"));
    private static final FontDescription F_NOX_BOLD =
        new FontDescription.Resource(Identifier.fromNamespaceAndPath("noxclient", "nox_bold"));

    public static Component styled(String s, boolean bold) {
        return Component.literal(s).withStyle(st -> st.withFont(bold ? F_NOX_BOLD : F_NOX));
    }

    public static void text(GuiGraphicsExtractor g, Font f, String s, int x, int y, int color) {
        g.text(f, styled(s, false), x, y, color);
    }

    /** Text with a soft drop shadow — the modern HUD look. */
    public static void textShadow(GuiGraphicsExtractor g, Font f, String s, int x, int y, int color) {
        g.text(f, styled(s, false), x + 1, y + 1, 0x88000000);
        g.text(f, styled(s, false), x, y, color);
    }

    public static void textBold(GuiGraphicsExtractor g, Font f, String s, int x, int y, int color) {
        g.text(f, styled(s, true), x, y, color);
    }

    public static void textCenter(GuiGraphicsExtractor g, Font f, String s, int cx, int y, int color) {
        g.text(f, styled(s, false), cx - width(f, s) / 2, y, color);
    }

    public static void textCenterBold(GuiGraphicsExtractor g, Font f, String s, int cx, int y, int color) {
        g.text(f, styled(s, true), cx - width(f, s) / 2, y, color);
    }

    public static int width(Font f, String s) {
        return f.width(styled(s, false));
    }

    /** Vertical gradient fill honoring rounded corners (band-sliced). */
    public static void vgrad(GuiGraphicsExtractor g, int x, int y, int w, int h, int r,
                             int top, int bottom) {
        r = Math.min(r, Math.min(w, h) / 2);
        int aT = (top >>> 24) & 0xFF, rT = (top >>> 16) & 0xFF, gT = (top >>> 8) & 0xFF, bT = top & 0xFF;
        int aB = (bottom >>> 24) & 0xFF, rB = (bottom >>> 16) & 0xFF, gB = (bottom >>> 8) & 0xFF, bB = bottom & 0xFF;
        for (int i = 0; i < h; i++) {
            double t = i / Math.max(1.0, h - 1);
            int c = (((int) (aT + (aB - aT) * t)) << 24)
                | (((int) (rT + (rB - rT) * t)) << 16)
                | (((int) (gT + (gB - gT) * t)) << 8)
                | ((int) (bT + (bB - bT) * t));
            int inset = 0;
            if (i < r) inset = r - (int) Math.ceil(Math.sqrt(r * r - (r - i) * (double) (r - i)));
            else if (i >= h - r) inset = r - (int) Math.ceil(Math.sqrt(r * r - (i - (h - r - 1)) * (double) (i - (h - r - 1))));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, c);
        }
    }
}
