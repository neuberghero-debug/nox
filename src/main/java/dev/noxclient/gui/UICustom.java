package dev.noxclient.gui;

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
    public static float hudScale = 1f;        // reserved for HUD scaling
    public static String clickStyle = "Studio"; // Studio | Cards | Compact rows | Sidebar | Panels | Hub
    public static String cardStyle = "Glass";  // Flat | Glass | Neon
    public static String winStyle  = "Solid";  // Solid | Sky | Glass
    public static boolean reducedMotion;

    /** Reset to NOX Dark defaults. */
    public static void reset() {
        accent = 0xFF7A2BE2; bg = 0xEE0D0F14; text = 0xFFE8E8F0;
        header = 0xFF10141C; winFill = 0xFF141821;
        cardFillTop = 0xFF1C222E; cardFillBottom = 0xFF161B26;
        border = 0x26FFFFFF; borderHover = 0x4DFFFFFF;
        textDim = 0xFF8A8A9E; textMute = 0xFF5A5A6E;
        radius = 12; cardH = 80; hudScale = 1f; clickStyle = "Studio"; winStyle = "Solid";
        cardStyle = "Glass"; cardAccent = 0xFF7A2BE2;
    }
}
