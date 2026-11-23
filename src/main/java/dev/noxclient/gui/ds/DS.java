package dev.noxclient.gui.ds;

import dev.noxclient.gui.UICustom;

/**
 * NOX design tokens (Modul 14) — the single palette/spacing/radius source
 * every new component reads. Accent comes from UICustom so live theming
 * keeps working; everything else is the Master-spec dark palette.
 *
 * Type scale: Display 22 · Heading 16 · Body 13 · Caption 11 · Mono 12.
 * Spacing: 8pt grid — 4 / 8 / 16 / 24 / 32.
 * Radii: 8 inputs · 10 cards · 14 windows · 999 pills.
 */
public final class DS {
    private DS() {}

    /* ---- palette (Dark default, master spec) ---- */
    public static int bg()         { return UICustom.guiBg; }
    public static int accent2()    { return UICustom.accent2; }
    /** border honoring the user's Off/Subtle/Strong setting. */
    public static int line()       {
        return switch (UICustom.borderMode) {
            case "Off" -> 0x00000000;
            case "Strong" -> lighten(UICustom.guiBg, 0.35f) & 0x00FFFFFF | 0x66000000;
            default -> border();
        };
    }
    /** Recessed / darkest card surface (settings child, wells, off-track). */
    public static int surface()    { return UICustom.cardFillBottom; }
    /** Card surface — lighter step above surface(). */
    public static int surfaceAlt() { return UICustom.cardFillTop; }
    public static int border()     {
        return switch (UICustom.borderMode) {
            case "Off" -> 0x00000000;
            case "Strong" -> 0x59FFFFFF;
            default -> UICustom.border;
        };
    }
    public static int header()     { return UICustom.header; }
    public static int winFill()    { return UICustom.winFill; }
    public static int text()       { return UICustom.text; }
    public static int secondary()  { return UICustom.textDim; }
    public static int muted()      { return UICustom.textMute; }
    public static int accent()     { return UICustom.accent; }        // themeable
    public static int cardAccent() { return UICustom.cardAccent; }    // tile accent
    /** Content drawn ON an accent fill — near-black on bright accents
     *  (Blacknox white ON-states), white on dark ones. */
    public static int onAccent() {
        int a = UICustom.accent;
        float lum = (0.299f * ((a >>> 16) & 0xFF) + 0.587f * ((a >>> 8) & 0xFF)
                   + 0.114f * (a & 0xFF)) / 255f;
        return lum > 0.55f ? 0xFF0A0A0A : 0xFFFFFFFF;
    }
    public static int hover()      { return lighten(UICustom.accent, 0.12f); }
    // status colors route through UICustom so the color-blind remap (M17.5)
    // applies everywhere — not just to components reading UICustom directly.
    public static int success()    { return UICustom.ok; }
    public static int warning()    { return UICustom.warn; }
    public static int danger()     { return UICustom.err; }

    /* ---- spacing (8pt grid) ---- */
    public static final int S4 = 4, S8 = 8, S16 = 16, S24 = 24, S32 = 32;

    /* ---- radii ---- */
    public static final int R_INPUT = 8, R_CARD = 10, R_WIN = 14, R_PILL = 999;

    /* ---- type sizes (gui px, via scaled text) ---- */
    public static final float T_DISPLAY = 22f, T_HEAD = 16f, T_BODY = 13f,
        T_CAPTION = 11f, T_MONO = 12f;

    /* ---- shadows (layered fills matching Gui.shadow idiom) ---- */
    public static final int SH_WIN = 0x80000000;   // 0 20px 60px rgba(0,0,0,.5)
    public static final int SH_CARD = 0x4D000000;  // 0 1px 3px rgba(0,0,0,.3)
    public static final int SH_HOVER = 0x66000000; // 0 2px 8px rgba(0,0,0,.4)

    /* ---- focus ring: 2px accent + soft glow ---- */
    public static int focusRing() { return withAlpha(accent(), 0xFF); }
    public static int focusGlow() { return withAlpha(accent(), 0x33); }

    /* ---- helpers ---- */
    public static int lighten(int argb, float f) {
        int a = argb >>> 24;
        int r = Math.min(255, (int) (((argb >>> 16) & 0xFF) * (1 + f)));
        int g = Math.min(255, (int) (((argb >>> 8) & 0xFF) * (1 + f)));
        int b = Math.min(255, (int) ((argb & 0xFF) * (1 + f)));
        return a << 24 | r << 16 | g << 8 | b;
    }
    public static int withAlpha(int rgb, int a) { return (a << 24) | (rgb & 0xFFFFFF); }
    /** alpha-scaled copy of a color. */
    public static int fade(int argb, float f) {
        return (((int) (((argb >>> 24) & 0xFF) * f)) << 24) | (argb & 0xFFFFFF);
    }
    /** linear interpolate two ARGB colors. */
    public static int lerp(int a, int b, float t) {
        int ra = (a >>> 16) & 0xFF, ga = (a >>> 8) & 0xFF, ba = a & 0xFF, aa = a >>> 24;
        int rb = (b >>> 16) & 0xFF, gb = (b >>> 8) & 0xFF, bb = b & 0xFF, ab = b >>> 24;
        return ((int) (aa + (ab - aa) * t) << 24) | ((int) (ra + (rb - ra) * t) << 16)
            | ((int) (ga + (gb - ga) * t) << 8) | (int) (ba + (bb - ba) * t);
    }
    /** exponential-smoothing step — dt-based (never frame-fixed). */
    public static float approach(float cur, float target, float speed, float dt) {
        return cur + (target - cur) * Math.min(1f, dt * speed);
    }
}
