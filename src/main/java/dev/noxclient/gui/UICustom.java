package dev.noxclient.gui;

import dev.noxclient.NoxClient;
import dev.noxclient.module.render.Interface;

/**
 * Live GUI customization — written by the Interface module's settings,
 * read by the theme + screens every frame. Zero-cost when untouched.
 * Token values: see .agents/skills/nox-design-system/SKILL.md
 */
public final class UICustom {
    private UICustom() {}

    public static int accent  = 0xFF7A2BE2;
    public static int cardAccent = 0xFF7A2BE2; // click-gui tiles can differ from window chrome
    public static int bg      = 0xEE0D0F14;
    public static int text    = 0xFFE8E8F0;
    public static int header  = 0xFF10141C;
    public static int winFill = 0xFF141821;   // window surface
    public static int cardFillTop    = 0xFF1C222E;
    public static int cardFillBottom = 0xFF161B26;
    public static int border       = 0x26FFFFFF; // subtle 1px contour (~15 % white)
    public static int borderHover  = 0x4DFFFFFF;
    public static int textDim      = 0xFF8A8A9E;
    public static int textMute     = 0xFF5A5A6E;
    public static int radius = 12;
    public static int cardH  = 80;            // density → card height
    public static String density = "Compact"; // Compact | Normal | Spacious → row height
    public static float hudScale = 1f;        // reserved for HUD scaling
    public static String clickStyle = "Studio"; // Studio | Cards | Compact rows | Sidebar | Panels | Hub

    static void selectClickStyle(String style) {
        NoxClient client = NoxClient.INSTANCE;
        if (client.modules.get("Interface") instanceof Interface ui) ui.clickStyle.set(style);
        clickStyle = style;
        client.config.save();
    }

    public static String cardStyle = "Clean";  // Clean | Flat | Glass | Neon
    public static String winStyle  = "Solid";  // Solid | Sky | Glass
    public static boolean reducedMotion;
    public static boolean largeText;
    public static String colorBlind = "Off";   // Off | Deuteranopia | Protanopia | Tritanopia

    // a11y-adjusted semantic colors — refreshed when colorBlind changes
    public static int ok   = 0xFF66D9A0;
    public static int err  = 0xFFFF6666;
    public static int warn = 0xFFFFD870;

    /** Recompute ok/err/warn for the active color-blind mode (M17.5).
     *  Deuter/Protan (red-green): state colors → blue/orange.
     *  Tritanopia (blue-yellow): green/red stay, warn → magenta. */
    public static void refreshA11y() {
        switch (colorBlind) {
            case "Deuteranopia", "Protanopia" -> {
                ok = 0xFF4DA3FF; err = 0xFFFF9E3D; warn = 0xFFEEE05A;
            }
            case "Tritanopia" -> {
                ok = 0xFF4DD9A0; err = 0xFFFF5C8A; warn = 0xFFFF5CD9;
            }
            default -> { ok = 0xFF66D9A0; err = 0xFFFF6666; warn = 0xFFFFD870; }
        }
    }

    /** Text scale factor for screens honoring large-text mode. */
    public static float textScale() { return largeText ? 1.25f : 1f; }
    public static String season   = "Auto";    // Auto | Winter | Spring | Summer | Autumn
    public static String backdrop = "Dim";     // Dim | Frost | Prism
    public static String backdropLevel = "Medium"; // Light | Medium | Strong
    public static boolean seasonFx = true;         // ambient particles in windows
    public static boolean snowPile = true;         // snow accumulates on window top
    public static float fxDensity = 1f;            // seasonal particle density 0.25–2.5

    /* ---- ClickGUI (NVG) full customization — read every frame ---- */
    public static int guiBg      = 0xFF0F0F12;   // window body color
    public static int accent2    = 0xFFFF8AD5;   // gradient end (active tab, sparkline)
    public static float winOpacity = 1.0f;       // window bg alpha 0.5–1 (Solid = opaque)
    public static float glow       = 1f;         // shadow/glow intensity 0–2
    public static float animSpeed  = 1f;         // multiplier on anim durations
    public static float fontScale  = 1f;         // NVG text scale 0.8–1.4
    public static boolean showStats  = true;     // live-stats column
    public static boolean showPinned = true;     // pinned-modules footer bar
    public static String borderMode = "Subtle";  // Off | Subtle | Strong

    /** Reset to NOX Dark defaults. */
    public static void reset() {
        accent = 0xFF7A2BE2; bg = 0xEE0D0F14; text = 0xFFE8E8F0;
        header = 0xFF10141C; winFill = 0xFF141821;
        cardFillTop = 0xFF1C222E; cardFillBottom = 0xFF161B26;
        border = 0x26FFFFFF; borderHover = 0x4DFFFFFF;
        textDim = 0xFF8A8A9E; textMute = 0xFF5A5A6E;
        radius = 12; cardH = 80; hudScale = 1f; clickStyle = "Studio"; winStyle = "Solid";
        cardStyle = "Glass"; cardAccent = 0xFF7A2BE2; reducedMotion = false;
        largeText = false; colorBlind = "Off"; refreshA11y();
        season = "Auto"; backdrop = "Dim"; backdropLevel = "Medium"; seasonFx = true;
        snowPile = true; fxDensity = 1f;
        guiBg = 0xFF0F0F12; accent2 = 0xFFFF8AD5; winOpacity = 0.97f;
        glow = 1f; animSpeed = 1f; fontScale = 1f;
        showStats = true; showPinned = true; borderMode = "Subtle";
    }
}
