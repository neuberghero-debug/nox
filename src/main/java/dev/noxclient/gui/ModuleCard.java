package dev.noxclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.noxclient.gui.theme.Theme;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.Map;

/**
 * One module tile in the NoxWindow card grid: icon glyph + name + status
 * accent + ⚙ settings button (opens the floating SettingsPopup).
 *   LMB card → toggle   LMB gear → settings popup   MMB card → keybind listen
 */
public class ModuleCard {
    public static final int CARD_W = 150;
    private static int ch() { return dev.noxclient.gui.UICustom.cardH; }

    private static final Map<Category, String> CAT_ICON = Map.of(
        Category.COMBAT, "⚔", Category.MOVEMENT, "➤", Category.RENDER, "◉",
        Category.DONUTSMP, "◆", Category.MISC, "⚙");
    private static final Map<String, String> ICONS = Map.ofEntries(
        Map.entry("KillAura", "⌖"), Map.entry("AutoCrystal", "◆"),
        Map.entry("AutoTotem", "✚"), Map.entry("Velocity", "⚡"),
        Map.entry("Triggerbot", "⌖"), Map.entry("AnchorMacro", "⚓"),
        Map.entry("AutoMace", "✦"), Map.entry("ShieldBreak", "⛏"),
        Map.entry("AutoPearl", "●"), Map.entry("AutoArmor", "♜"),
        Map.entry("AntiTrap", "▣"), Map.entry("AutoWeb", "⌘"),
        Map.entry("ESP", "◉"), Map.entry("PlayerESP", "◉"),
        Map.entry("Tracers", "✛"), Map.entry("Fullbright", "☀"),
        Map.entry("CustomSky", "☾"), Map.entry("StorageESP", "▦"),
        Map.entry("Blink", "‖"), Map.entry("Flight", "✈"),
        Map.entry("Speed", "»"), Map.entry("NoFall", "▽"),
        Map.entry("AutoSell", "$"), Map.entry("AHSniper", "$"),
        Map.entry("AutoEat", "♨"), Map.entry("StaffNotifier", "⚠"),
        Map.entry("AntiAFK", "◷"), Map.entry("CombatLog", "⚑"),
        Map.entry("AutoReconnect", "↻"), Map.entry("OpSec", "◘"),
        Map.entry("ModSpoofer", "⛭"), Map.entry("TamperWarning", "⚠"),
        Map.entry("GhostMode", "○"), Map.entry("DiscordRPC", "◐"),
        Map.entry("SelfDestruct", "✖"), Map.entry("AutoTool", "⛏"),
        Map.entry("FastPlace", "▣"), Map.entry("TunnelBaseFinder", "⌖"),
        Map.entry("RTPBaseFinder", "◎"), Map.entry("AntiESPBypass", "◈"),
        Map.entry("AntiVanish", "◌"), Map.entry("ChunkFinder", "▦"),
        Map.entry("AmethystBaseFinder", "◆"), Map.entry("ArtificialLightFinder", "✧"),
        Map.entry("SusChunkFinder", "◍"), Map.entry("AutoTotemOrder", "✚"),
        Map.entry("AutoCommander", "»"), Map.entry("AutoHarvest", "❀"),
        Map.entry("AutoVoiceConfirm", "♫"),
        Map.entry("ArmorAlert", "▥"), Map.entry("SpotifyHud", "♫"),
        Map.entry("LyricsSky", "♪"), Map.entry("HudWidgets", "▤"),
        Map.entry("TargetHUD", "⌖"),
        Map.entry("Cosmetics", "★"), Map.entry("Criticals", "⚔"),
        Map.entry("SprintReset", "»"), Map.entry("StepAssist", "▲"),
        Map.entry("AutoSprint", "»"), Map.entry("InventoryMove", "⇄"),
        Map.entry("NoSlow", "▶"), Map.entry("FlyBypass", "✈"),
        Map.entry("ElytraGlide", "✈"));

    public final Module module;
    public int x, y, width = CARD_W, height = UICustom.cardH;
    public boolean hoverNow;
    private boolean listening;
    private float anim;                     // 0=off .. 1=on, lerped per frame
    private long lastFrame;

    public ModuleCard(Module module) { this.module = module; }

    boolean matches(String f) {
        return module.name.toLowerCase().contains(f) || module.description.toLowerCase().contains(f);
    }

    private String icon() { return glyph(module); }

    static String glyph(Module m) {
        String i = ICONS.get(m.name);
        if (i != null) return i;
        String c = CAT_ICON.get(m.category);
        return c != null ? c : m.name.substring(0, 1);
    }

    static String catIcon(Category c) {
        String i = CAT_ICON.get(c);
        return i != null ? i : "•";
    }

    int gearX() { return x + width - 18; }
    int gearY() { return y + 4; }

    void render(GuiGraphicsExtractor g, int mx, int my) {
        Theme t = Theme.current();
        var font = Minecraft.getInstance().font;
        boolean on = module.isEnabled();
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        anim += ((on ? 1f : 0f) - anim) * Math.min(1f, dt * 12f);
        height = UICustom.cardH;
        boolean hover = inside(mx, my, x, y, width, height);
        hoverNow = hover;
        boolean gearHover = inside(mx, my, gearX(), gearY(), 14, 14);
        on = anim > 0.5f;

        int accent = UICustom.cardAccent;

        // shadow + surface — style-aware (Flat / Glass / Neon)
        String cst = UICustom.cardStyle;
        boolean neon = cst.equals("Neon"), flat = cst.equals("Flat");
        if (!flat) Gui.shadow(g, x, y, width, height, 10);
        int borderCol = hover ? UICustom.borderHover : UICustom.border;
        if (neon && anim > 0.02f)
            borderCol = blend(borderCol, accent & 0x00FFFFFF | 0xFF000000, anim);
        else if (anim > 0.02f)
            borderCol = blend(borderCol, accent & 0x00FFFFFF | 0xFF000000, anim * 0.7f);
        if (cst.equals("Glass"))
            borderCol = hover ? UICustom.borderHover : UICustom.border;
        if (!flat)
            Gui.round(g, x, y, width, height, 10, borderCol);
        if (neon && anim > 0.15f)
            Gui.roundBorder(g, x - 1, y - 1, width + 2, height + 2, 11,
                (accent & 0x00FFFFFF) | ((int) (0x60 * anim) << 24), 0x00000000);
        int fillTop = blend(UICustom.cardFillTop, (accent & 0xF2FFFFFF) | 0xF2000000,
            anim * (neon ? 0.5f : 0.35f));
        int fillBot = UICustom.cardFillBottom;
        if (flat) { Gui.round(g, x, y, width, height, 10, fillTop); }
        else Gui.vgrad(g, x + 1, y + 1, width - 2, height - 2, 9, fillTop, fillBot);

        // accent bar on the left edge while enabled
        if (anim > 0.02f) {
            int aa = (int) (0xE6 * anim) << 24;
            Gui.round(g, x + 1, y + 8, 3, height - 16, 1, (accent & 0x00FFFFFF) | aa);
        }

        // icon chip
        int icX = x + 10, icY = y + (height - 28) / 2;
        int chipBg = blend(0xFF20202E, accent & 0x00FFFFFF | 0xFF000000, anim * 0.35f);
        Gui.round(g, icX, icY, 28, 28, 8, chipBg);
        String ic = icon();
        Gui.textCenterBold(g, font, ic, icX + 14, icY + 9,
            on ? accent : (hover ? t.text() : 0xFF8A8A9E));

        // name (bold, clipped) + description (small, muted, clipped)
        int tx = icX + 36, tw = width - 36 - 12 - (module.premium ? 14 : 0) - 14;
        String nm = clip(font, module.name, tw);
        Gui.textBold(g, font, nm, tx, y + 11, on ? 0xFFF0E8FF : t.text());
        String ds = module.description == null ? "" : module.description;
        Gui.text(g, font, clip(font, ds, tw + 16), tx, y + 23, 0xFF7C7C90);

        // iOS-style toggle bottom-right
        int swX = x + width - 34, swY = y + height - 18;
        Gui.pill(g, swX, swY, 26, 12, blend(0xFF2A2A3A, accent & 0x00FFFFFF | 0xFF000000, anim));
        Gui.circle(g, swX + 6 + (int) (14 * anim), swY + 6, 4,
            blend(0xFF6A6A80, 0xFFFFFFFF, anim));

        // gear top-right
        Gui.text(g, font, "⚙", gearX() + 3, gearY() + 3, gearHover ? accent : 0xFF5A5A6E);

        // premium star — left of the gear
        if (module.premium) Gui.text(g, font, "★", gearX() - 12, gearY() + 3, 0xFFFFD870);

        // bottom strip: status dot + label, keybind chip tucked right of it
        int bx = x + 10;
        int dotA = (int) (0xFF * (0.35f + 0.65f * anim)) << 24;
        Gui.circle(g, bx + 3, y + height - 12, 3,
            (blend(0xFF4A4A5E, accent, anim) & 0x00FFFFFF) | dotA);
        String st = anim > 0.5f ? "Active" : "Idle";
        Gui.text(g, font, st, bx + 10, y + height - 15,
            blend(0xFF55556A, 0xFF9C9CB8, anim));
        bx += 12 + Gui.width(font, st) + 6;
        if (module.keybind() > 0) {
            String kb = keyName(module.keybind());
            int kw = Gui.width(font, kb) + 8;
            Gui.round(g, bx, y + height - 17, kw, 11, 3, 0xFF1E1E2C);
            Gui.text(g, font, kb, bx + 4, y + height - 15, 0xFF6A6A80);
        }
        if (listening)
            Gui.textCenter(g, font, "press key…", x + width / 2, y + height - 16, 0xFFFFD870);
    }

    private static String clip(net.minecraft.client.gui.Font font, String s, int w) {
        if (s == null || s.isEmpty()) return "";
        String out = s;
        while (Gui.width(font, out + "…") > w && !out.isEmpty()) out = out.substring(0, out.length() - 1);
        return out.equals(s) ? s : out + "…";
    }

    /** @return 0 none, 1 card clicked (toggle), 2 gear/settings clicked (popup)
     *  GLFW buttons: 0=LMB toggle, 1=RMB settings, 2=MMB keybind listen. */
    int mouseClicked(double mx, double my, int btn) {
        if (!inside(mx, my, x, y, width, height)) return 0;
        if (inside(mx, my, gearX(), gearY(), 14, 14)) { click(); return 2; }
        if (btn == 0) { module.toggle(); click(); return 1; }
        if (btn == 1 && !module.settings.isEmpty()) return 2; // RMB opens settings
        if (btn == 2) { listening = !listening; return 1; }
        return 1;
    }

    void mouseReleased() {}

    boolean keyPressed(int key) {
        if (!listening) return false;
        module.setKeybind(key == InputConstants.KEY_ESCAPE ? -1 : key);
        listening = false;
        return true;
    }

    private static int blend(int a, int b, float t) {
        int aa = (a >>> 24) + (int) (((b >>> 24) - (a >>> 24)) * t);
        int ar = ((a >> 16) & 0xFF) + (int) ((((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int ag = ((a >> 8) & 0xFF) + (int) ((((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int ab = (a & 0xFF) + (int) (((b & 0xFF) - (a & 0xFF)) * t);
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }

    static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static String keyName(int code) {
        try { return InputConstants.Type.KEYBOARD.getOrCreate(code).getDisplayName().getString(); }
        catch (Throwable t) { return String.valueOf(code); }
    }

    static void click() {
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }
}
