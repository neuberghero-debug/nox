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
    public static final int CARD_W = 210;
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
    private String cachedDescription = "", cachedFirst = "", cachedSecond = "", cachedName = "";
    private int cachedWidth = -1;

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
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        anim += ((on ? 1f : 0f) - anim) * (UICustom.reducedMotion ? 1f : (float) (1 - Math.exp(-16f * dt)));
        height = UICustom.cardH;
        boolean hover = inside(mx, my, x, y, width, height);
        hoverNow = hover;
        boolean gearHover = inside(mx, my, gearX(), gearY(), 14, 14);
        int accent = UICustom.cardAccent;
        int fill = blend(UICustom.cardFillBottom, UICustom.cardFillTop, hover ? 1 : .45f);
        if ("Neon".equals(UICustom.cardStyle)) fill = blend(fill, accent, anim * .12f);
        Gui.roundBorder(g, x, y, width, height, 8,
            hover ? UICustom.borderHover : UICustom.border, fill);
        if (on) Gui.round(g, x + 1, y + 14, 2, height - 28, 1, accent);

        String ds = module.description == null ? "" : module.description;
        if (cachedWidth != width || !cachedDescription.equals(ds)) {
            cachedWidth = width; cachedDescription = ds;
            cachedName = clip(font, module.name, width - 42);
            String first = clip(font, ds, width - 28);
            if (!first.equals(ds)) {
                int end = Math.max(0, first.length() - 1);
                int space = ds.lastIndexOf(' ', end);
                if (space > 8) end = space;
                cachedFirst = ds.substring(0, end);
                cachedSecond = clip(font, ds.substring(end).stripLeading(), width - 28);
            } else { cachedFirst = ds; cachedSecond = ""; }
        }
        Gui.textBold(g, font, cachedName, x + 14, y + 12, t.text());
        Gui.text(g, font, cachedFirst, x + 14, y + 29, UICustom.textDim);
        if (height >= 76) Gui.text(g, font, cachedSecond, x + 14, y + 41, UICustom.textDim);

        int swX = x + width - 42, swY = y + height - 22;
        Gui.pill(g, swX, swY, 28, 14, blend(0xFF373D4A, accent | 0xFF000000, anim));
        Gui.circle(g, swX + 6 + (int) (14 * anim), swY + 6, 4,
            blend(0xFFB4BBC8, 0xFFFFFFFF, anim));
        for (int i = 0; i < 3; i++) Gui.circle(g, gearX() + 4 + i * 3, gearY() + 10, 1,
            gearHover ? t.text() : UICustom.textDim);
        String meta = module.gated() ? "Premium" : module.keybind() > 0 ? keyName(module.keybind()) : module.category.label;
        Gui.text(g, font, meta, x + 14, y + height - 19, module.gated() ? 0xFFE4BD7B : UICustom.textDim);
        if (listening)
            Gui.text(g, font, "Press a key…", x + 14, y + height - 19, 0xFFFFD870);
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
