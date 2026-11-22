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
    public final Module module;
    public int x, y, width = CARD_W, height = UICustom.cardH;
    public long appearAt;   // stagger timestamp — card slides in after this ms
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
        return String.valueOf(Character.toUpperCase(m.name.charAt(0)));
    }

    static String catIcon(Category c) {
        String i = CAT_ICON.get(c);
        return i != null ? i : "•";
    }

    int gearX() { return x + width - 24; }
    int gearY() { return y + 8; }
    /** Show the category label on the card — only meaningful during a search. */
    public boolean showCategory;

    void render(GuiGraphicsExtractor g, int mx, int my) {
        float ap = UICustom.reducedMotion || appearAt == 0 ? 1f
            : Math.min(1f, Math.max(0f, (System.currentTimeMillis() - appearAt) / 140f));
        ap = 1f - (1f - ap) * (1f - ap) * (1f - ap);
        if (ap <= 0f) return;
        int dy = (int) ((1f - ap) * 10);
        y -= dy;
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
        boolean gearHover = inside(mx, my, gearX(), gearY(), 16, 16);
        boolean starHover = inside(mx, my, gearX() - 18, gearY(), 16, 16);
        int accent = UICustom.cardAccent;
        int fill = blend(UICustom.cardFillBottom, UICustom.cardFillTop, hover ? 1 : .45f);
        if ("Neon".equals(UICustom.cardStyle)) fill = blend(fill, accent, anim * .12f);
        // enabled → accent border ~40% + soft glow; hover lifts the border otherwise
        int border = on ? (accent & 0xFFFFFF) | 0x66000000
            : hover ? UICustom.borderHover : UICustom.border;
        if (on) Gui.shadow(g, x, y, width, height, 9);
        Gui.roundBorder(g, x, y, width, height, 8, border, fill);

        // — 28×28 icon chip — animated vector glyph
        int chipX = x + 10, chipY = y + 10;
        Gui.round(g, chipX, chipY, 28, 28, 8,
            on ? accent | 0xFF000000 : UICustom.cardFillTop);
        if (!on) Gui.roundBorder(g, chipX, chipY, 28, 28, 8, UICustom.border, 0);
        ModuleIcons.draw(g, ModuleIcons.forModule(module), chipX + 14, chipY + 14, 14,
            (on ? UICustom.winFill
                : dev.noxclient.gui.ds.DS.lerp(0xFF000000 | (accent & 0xFFFFFF), t.text(), .42f)) & 0xFFFFFF,
            System.currentTimeMillis() / 1000f, on, hover);

        String ds = module.description == null ? "" : module.description;
        if (cachedWidth != width || !cachedDescription.equals(ds)) {
            cachedWidth = width; cachedDescription = ds;
            cachedName = clip(font, module.name, width - 96);
            String first = clip(font, ds, width - 60);
            if (!first.equals(ds)) {
                int end = Math.max(0, first.length() - 1);
                int space = ds.lastIndexOf(' ', end);
                if (space > 8) end = space;
                cachedFirst = ds.substring(0, end);
                cachedSecond = clip(font, ds.substring(end).stripLeading(), width - 60);
            } else { cachedFirst = ds; cachedSecond = ""; }
        }
        Gui.textBold(g, font, cachedName, chipX + 36, chipY + 3, t.text());
        Gui.text(g, font, cachedFirst, chipX + 36, chipY + 15, UICustom.textDim);
        if (height >= 76) Gui.text(g, font, cachedSecond, chipX + 36, chipY + 27, UICustom.textDim);

        // — ⚙ affordance —
        Gui.round(g, gearX(), gearY(), 16, 16, 5,
            gearHover ? UICustom.cardFillTop : 0);
        ModuleIcons.draw(g, ModuleIcons.Kind.GEAR, gearX() + 8, gearY() + 8, 10,
            gearHover ? t.text() : UICustom.textDim,
            System.currentTimeMillis() / 1000f, false, gearHover);

        // — ★ favorite — visible when starred or on hover —
        boolean fav = Favorites.is(module);
        if (fav || hover) {
            Gui.round(g, gearX() - 18, gearY(), 16, 16, 5,
                starHover ? UICustom.cardFillTop : 0);
            ModuleIcons.draw(g, ModuleIcons.Kind.STAR, gearX() - 10, gearY() + 8, 9,
                (fav ? 0xFFE4BD7B : UICustom.textMute) & 0xFFFFFF,
                System.currentTimeMillis() / 1000f, fav, starHover);
        }

        // — bottom row: status + meta left, bind chip + toggle right —
        int botY = y + height - 22;
        Gui.circle(g, x + 14, botY + 4, 3, on ? accent | 0xFF000000 : UICustom.textMute);
        String status = on ? "Active" : "Idle";
        Gui.text(g, font, status, x + 22, botY,
            on ? accent | 0xFF000000 : UICustom.textMute);
        String meta = module.gated() ? "Premium"
            : showCategory ? module.category.label : null;
        if (meta != null)
            Gui.text(g, font, meta, x + 22 + Gui.width(font, status) + 10, botY,
                module.gated() ? 0xFFE4BD7B : UICustom.textMute);
        if (listening)
            Gui.text(g, font, "Press a key…", x + 22 + Gui.width(font, status) + 10, botY, 0xFFFFD870);

        int swX = x + width - 42, swY = y + height - 24;
        if (module.keybind() > 0) {
            String kn = keyName(module.keybind());
            int kw = Gui.width(font, kn) + 10;
            Gui.roundBorder(g, swX - 6 - kw, swY - 1, kw, 16, 5, UICustom.border, UICustom.cardFillTop);
            Gui.textCenter(g, font, kn, swX - 6 - kw / 2, swY + 3, UICustom.textDim);
        }
        Gui.pill(g, swX, swY, 28, 14, blend(0xFF373D4A, accent | 0xFF000000, anim));
        Gui.circle(g, swX + 6 + (int) (14 * anim), swY + 6, 4,
            blend(0xFFB4BBC8, 0xFFFFFFFF, anim));
        y += dy;
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
        if (inside(mx, my, gearX() - 18, gearY(), 16, 16)
            && (Favorites.is(module) || hoverNow)) {
            Favorites.toggle(module); click(); return 1;
        }
        if (inside(mx, my, gearX(), gearY(), 16, 16)) { click(); return 2; }
        if (btn == 0) { module.toggle(); Favorites.markUsed(module); click(); return 1; }
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
