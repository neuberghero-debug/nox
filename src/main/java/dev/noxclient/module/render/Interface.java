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
    public final Setting.Mode theme = add(new Setting.Mode("Theme", "NOX Dark",
        "NOX Dark", "Midnight", "Snow", "Custom"));
    public final Setting.Col accent  = add(new Setting.Col("Window accent", 0xFF7A2BE2));
    public final Setting.Col cardAcc = add(new Setting.Col("Card accent", 0xFF7A2BE2));
    public final Setting.Mode cardStyle = add(new Setting.Mode("Card style", "Glass",
        "Flat", "Glass", "Neon"));
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

    private String lastTheme = "";

    public Interface() { super("Interface", "Customize the whole NOX GUI", Category.RENDER); }

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
        UICustom.clickStyle = clickStyle.get();
        UICustom.hudScale = (float) (double) hudScale.get();
        UICustom.reducedMotion = reducedMotion.on();
        if (!theme.get().equals(lastTheme)) {
            lastTheme = theme.get();
            for (int i = 0; i < Theme.ALL.length; i++)
                if (Theme.ALL[i].name().equals(lastTheme)) { Theme.set(i); break; }
        }
    }
}
