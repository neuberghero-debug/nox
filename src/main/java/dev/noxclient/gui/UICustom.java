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

    // static defaults = Blacknox OLED (the stock look, before any config loads —
    // the boot screen already reads these). Interface.onTick overwrites per theme.
    public static int accent  = 0xFFFFFFFF;
    public static int cardAccent = 0xFFFFFFFF; // click-gui tiles can differ from window chrome
    public static int bg      = 0xEE000000;
    public static int text    = 0xFFF5F5F5;
    public static int header  = 0xFF000000;
    public static int winFill = 0xFF000000;   // window surface
    public static int cardFillTop    = 0xFF0A0A0A;
    public static int cardFillBottom = 0xFF050505;
    public static int border       = 0x0FFFFFFF; // subtle 1px contour (~6 % white)
    public static int borderHover  = 0x24FFFFFF;
    public static int textDim      = 0xFF8A8A8A;
    public static int textMute     = 0xFF3A3A3A;
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
    public static int guiBg      = 0xFF000000;   // window body color
    public static int accent2    = 0xFF9A9A9A;   // gradient end (active tab, sparkline)
    public static float winOpacity = 1.0f;       // window bg alpha 0.5–1 (Solid = opaque)
    public static float glow       = 1f;         // shadow/glow intensity 0–2
    public static float animSpeed  = 1f;         // multiplier on anim durations
    public static float fontScale  = 1f;         // NVG text scale 0.8–1.4
    public static float guiScale   = 1f;         // whole-window scale 0.6–1.5
    public static boolean autoGuiSize = true;    // fit ClickGUI to screen
    public static boolean showStats  = true;     // live-stats column
    public static boolean showPinned = true;     // pinned-modules footer bar
    public static String borderMode = "Subtle";  // Off | Subtle | Strong

    /** Reset to Blacknox OLED defaults. */
    public static void reset() {
        accent = 0xFFFFFFFF; bg = 0xEE000000; text = 0xFFF5F5F5;
        header = 0xFF000000; winFill = 0xFF000000;
        cardFillTop = 0xFF0A0A0A; cardFillBottom = 0xFF050505;
        border = 0x0FFFFFFF; borderHover = 0x24FFFFFF;
        textDim = 0xFF8A8A8A; textMute = 0xFF3A3A3A;
        radius = 12; cardH = 80; hudScale = 1f; clickStyle = "Studio"; winStyle = "Solid";
        cardStyle = "Glass"; cardAccent = 0xFFFFFFFF; reducedMotion = false;
        largeText = false; colorBlind = "Off"; refreshA11y();
        season = "Auto"; backdrop = "Dim"; backdropLevel = "Medium"; seasonFx = true;
        snowPile = true; fxDensity = 1f;
        guiBg = 0xFF000000; accent2 = 0xFF9A9A9A; winOpacity = 0.97f;
        glow = 1f; animSpeed = 1f; fontScale = 1f; guiScale = 1f; autoGuiSize = true;
        showStats = true; showPinned = true; borderMode = "Subtle";
    }
}
