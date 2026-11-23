package dev.noxclient.module.render;

import dev.noxclient.gui.UICustom;
import dev.noxclient.gui.theme.Theme;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/**
 * Full GUI customization: theme, every color, corner radius, card density
 * and click-GUI layout — applied live to all NOX screens.
 */
public class Interface extends Module {
    public final Setting.Mode theme = add(new Setting.Mode("Theme", "Blacknox",
        "Blacknox", "NOX Dark", "Midnight", "Snow", "Custom"));
    public final Setting.Col accent  = add(new Setting.Col("Window accent", 0xFF7A2BE2));
    public final Setting.Col cardAcc = add(new Setting.Col("Card accent", 0xFF7A2BE2));
    public final Setting.Mode cardStyle = add(new Setting.Mode("Card style", "Clean",
        "Clean", "Flat", "Glass", "Neon"));
    public final Setting.Mode winStyle = add(new Setting.Mode("Window style", "Solid",
        "Solid", "Sky", "Glass"));
    public final Setting.Col winFill = add(new Setting.Col("Window", 0xFF141821));
    public final Setting.Col cardTop = add(new Setting.Col("Card top", 0xFF1C222E));
    public final Setting.Col cardBot = add(new Setting.Col("Card bottom", 0xFF161B26));
    public final Setting.Col header  = add(new Setting.Col("Header", 0xFF10141C));
    public final Setting.Col bg      = add(new Setting.Col("Backdrop", 0xEE0D0F14));
    public final Setting.Col text    = add(new Setting.Col("Text", 0xFFE8E8F0));
    public final Setting.Num radius  = add(new Setting.Num("Corner radius", 12, 0, 24));
    public final Setting.Mode density = add(new Setting.Mode("Density", "Normal",
        "Compact", "Normal", "Spacious"));
    public final Setting.Mode clickStyle = add(new Setting.Mode("ClickGUI", "Studio",
        "Studio", "Cards", "Compact rows", "Sidebar", "Panels", "Hub"));
    public final Setting.Num hudScale = add(new Setting.Num("HUD scale", 1.0, 0.5, 2.0));
    public final Setting.Bool reducedMotion = add(new Setting.Bool("Reduced motion", false));
    public final Setting.Bool largeText = add(new Setting.Bool("Large text", false));
    public final Setting.Mode colorBlind = add(Setting.Mode.of("Color blind", "Off",
        "Off", "Deuteranopia", "Protanopia", "Tritanopia"));

    public final Setting.Mode season = add(Setting.Mode.of("Season", "Auto",
        "Seasonal palette — Auto follows the real-world calendar", "Auto", "Winter", "Spring", "Summer", "Autumn"));
    public final Setting.Mode backdrop = add(Setting.Mode.of("Screen backdrop", "Dim",
        "Window backdrop style — Prism adds an animated specular edge, Sky renders the black hole", "Dim", "Frost", "Prism", "Sky"));
    public final Setting.Mode backdropLevel = add(Setting.Mode.of("Backdrop level", "Medium",
        "Strength of the backdrop effect", "Light", "Medium", "Strong"));
    public final Setting.Bool seasonFx = add(new Setting.Bool("Seasonal FX", true,
        "Ambient particles in the GUI — snow, petals, fireflies, leaves"));
    public final Setting.Bool snowPile = add(new Setting.Bool("Snow piling", true,
        "Winter snow accumulates on the window's top edge and slowly melts"));
    public final Setting.Num fxDensity = add(new Setting.Num("FX density", 1.0, 0.25, 2.5,
        "Seasonal particle amount"));

    /* ---- ClickGUI deep customization (Design tab edits these live) ---- */
    public final Setting.Col guiBg = add(new Setting.Col("GUI background", 0xFF0F0F12,
        "ClickGUI window body color"));
    public final Setting.Col accent2 = add(new Setting.Col("Accent gradient", 0xFFFF8AD5,
        "Gradient end color — active tabs, sparkline, glows"));
    public final Setting.Num winOpacity = add(new Setting.Num("Window opacity", 0.97, 0.5, 1.0,
        "ClickGUI window transparency"));
    public final Setting.Num glow = add(new Setting.Num("Glow intensity", 1.0, 0.0, 2.0,
        "Shadow/glow strength on windows and focus rings"));
    public final Setting.Num animSpeed = add(new Setting.Num("Animation speed", 1.0, 0.25, 3.0,
        "Multiplier on every GUI animation duration"));
    public final Setting.Num fontScale = add(new Setting.Num("Font scale", 1.0, 0.8, 1.4,
        "ClickGUI text size — layout adapts automatically"));
    public final Setting.Bool showStats = add(new Setting.Bool("Live stats", true,
        "Right-side stats column in the ClickGUI"));
    public final Setting.Bool showPinned = add(new Setting.Bool("Pinned bar", true,
        "Pinned-modules strip at the bottom of the ClickGUI"));
    public final Setting.Mode borderMode = add(Setting.Mode.of("Borders", "Subtle",
        "Window/panel border visibility", "Off", "Subtle", "Strong"));
    private String lastTheme = "";
    private String lastSeason = "";

    public Interface() { super("Interface", "Customize the whole NOX GUI", Category.RENDER); }

    /** Infrastructure module — owns all GUI state; switching it off kills every
     * customization, so it can never be disabled (card click, config, PANIC). */
    @Override public void setEnabled(boolean v) { super.setEnabled(true); }
    @Override public void restore(boolean v) { super.restore(true); }

    @Override
    public void onTick() {
        UICustom.accent  = accent.get()  | 0xFF000000;
        UICustom.cardAccent = cardAcc.get() | 0xFF000000;
        UICustom.cardStyle = cardStyle.get();
        UICustom.winStyle = winStyle.get();
        UICustom.winFill = winFill.get() | 0xFF000000;
        UICustom.cardFillTop    = cardTop.get() | 0xFB000000;
        UICustom.cardFillBottom = cardBot.get() | 0xFB000000;
        UICustom.header  = header.get()  | 0xFF000000;
        UICustom.bg      = (bg.get() & 0xFFFFFF) | 0xEE000000;
        UICustom.text    = text.get()    | 0xFF000000;
        UICustom.radius  = radius.intVal();
        UICustom.cardH   = switch (density.get()) { case "Compact" -> 64; case "Spacious" -> 96; default -> 80; };
        UICustom.density = density.get();
        UICustom.clickStyle = clickStyle.get();
        UICustom.hudScale = (float) (double) hudScale.get();
        UICustom.reducedMotion = reducedMotion.on();
        UICustom.largeText = largeText.on();
        UICustom.colorBlind = colorBlind.get();
        UICustom.refreshA11y();
        UICustom.season = season.get();
        UICustom.backdrop = backdrop.get();
        UICustom.backdropLevel = backdropLevel.get();
        UICustom.seasonFx = seasonFx.on();
        UICustom.snowPile = snowPile.on();
        UICustom.fxDensity = (float) (double) fxDensity.get();
        UICustom.guiBg = guiBg.get() | 0xFF000000;
        UICustom.accent2 = accent2.get() | 0xFF000000;
        UICustom.winOpacity = (float) (double) winOpacity.get();
        UICustom.glow = (float) (double) glow.get();
        UICustom.animSpeed = (float) (double) animSpeed.get();
        // auto-fit: at default (1.0) the GUI shrinks on small logical screens
        // (vanilla GUI scale ≥3) instead of overflowing; an explicit setting
        // still wins as a multiplier on top
        float autoFit = 1f;
        var win = net.minecraft.client.Minecraft.getInstance().getWindow();
        if (win != null) {
            int lh = Math.max(1, win.getGuiScaledHeight());
            autoFit = Math.clamp(lh / 420f, 0.72f, 1f);
        }
        UICustom.fontScale = (float) (double) fontScale.get() * autoFit;
        UICustom.showStats = showStats.on();
        UICustom.showPinned = showPinned.on();
        UICustom.borderMode = borderMode.get();
        if (!season.get().equals(lastSeason)) {
            lastSeason = season.get();
            // A NAMED season applies its theme (explicit seasonal look).
            // "Auto" only makes the seasonal FX follow the calendar — it must
            // NOT touch the theme, or the user's theme pick is unreachable.
            if (!"Auto".equals(lastSeason)) Theme.set(Theme.indexOf(lastSeason));
        }
        if (!theme.get().equals(lastTheme)) {
            lastTheme = theme.get();
            for (int i = 0; i < Theme.ALL.length; i++)
                if (Theme.ALL[i].name().equals(lastTheme)) { Theme.set(i); break; }
        }
        applyThemePalette();
    }

    /** Theme presets own the palette — explicit color settings apply under "Custom". */
    private void applyThemePalette() {
        Theme th = Theme.current();
        if (!"Custom".equals(th.name())) th.applyPalette();
    }
}
