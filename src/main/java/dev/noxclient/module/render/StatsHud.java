package dev.noxclient.module.render;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/**
 * Live-stats HUD panel — the ClickGUI stats widgets (kills/min sparkline,
 * K/D gauge, money-per-hour bars) as a draggable in-game element.
 * Position: HUD editor · Style: per-widget toggles + compact mode + opacity.
 */
public class StatsHud extends Module {
    public final Setting.Bool header = add(new Setting.Bool("Header", true, "'STATS' title bar"));
    public final Setting.Bool kpm = add(new Setting.Bool("Kills/min", true, "Sparkline of kills per minute"));
    public final Setting.Bool kd = add(new Setting.Bool("K/D ratio", true, "Radial K/D gauge"));
    public final Setting.Bool mph = add(new Setting.Bool("Money/hour", true, "Earnings bar chart"));
    public final Setting.Bool compact = add(new Setting.Bool("Compact", false,
        "Single line of numbers instead of charts"));
    public final Setting.Bool edge = add(new Setting.Bool("Accent edge", true));
    public final Setting.Num opacity = add(new Setting.Num("Background", 75, 0, 100));
    public final Setting.Num spacing = add(new Setting.Num("Spacing", 4, 0, 12));

    public StatsHud() {
        super("StatsHud", "Live stats panel — draggable via HUD editor", Category.RENDER);
    }
}
