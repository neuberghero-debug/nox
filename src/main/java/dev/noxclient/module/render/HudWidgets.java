package dev.noxclient.module.render;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** HUD widgets drawn by NoxOverlay: watermark, module arraylist, stats line. */
public class HudWidgets extends Module {
    public final Setting.Bool watermark = add(new Setting.Bool("Watermark", true));
    public final Setting.Bool arraylist = add(new Setting.Bool("Module list", true, "Enabled modules — slides in/out with easing"));
    public final Setting.Bool coords = add(new Setting.Bool("Coords", true));
    public final Setting.Bool fps = add(new Setting.Bool("FPS", false));
    public final Setting.Bool ping = add(new Setting.Bool("Ping", false));
    public final Setting.Bool armor = add(new Setting.Bool("Armor", true));
    public final Setting.Bool totems = add(new Setting.Bool("Totem counter", true));
    public final Setting.Bool nether = add(new Setting.Bool("Nether coords", true));
    public final Setting.Bool facing = add(new Setting.Bool("Facing", true));
    public final Setting.Bool bps = add(new Setting.Bool("Speed (bps)", false));
    public final Setting.Bool effects = add(new Setting.Bool("Potion effects", false));
    public final Setting.Bool tps = add(new Setting.Bool("Server TPS", false));
    public final Setting.Bool session = add(new Setting.Bool("Session time", false));
    public final Setting.Bool compass = add(new Setting.Bool("Compass", true, "Cardinal direction bar at the top"));
    /** 16.1 — master preset: Minimal (watermark+coords), Standard, Full (all). */
    public final Setting.Mode mode = add(Setting.Mode.of("HUD mode", "Standard",
        "Master density preset", "Minimal", "Standard", "Full"));
    /** 16.2 — show disabled modules dimmed in the arraylist. */
    public final Setting.Bool showDisabled = add(new Setting.Bool("Show disabled", false,
        "Module list also shows disabled entries, dimmed"));
    public final Setting.Mode infoStyle = add(Setting.Mode.of("Info style", "Card",
        "How the info column renders", "Card", "Lines", "Pills"));
    public final Setting.Mode anchor = add(Setting.Mode.of("Info anchor", "TopLeft", "Corner for the info column",
        "TopLeft", "TopRight", "BottomLeft", "BottomRight"));
    public final Setting.Mode listSide = add(Setting.Mode.of("List anchor", "TopRight", "Corner for the module list",
        "TopRight", "TopLeft", "BottomRight", "BottomLeft"));

    public HudWidgets() { super("HudWidgets", "NOX HUD widgets", Category.RENDER); }

    /** Density gate — Minimal shows only watermark/coords; Full shows everything. */
    public boolean show(Setting.Bool w) {
        return switch (mode.get()) {
            case "Minimal" -> w == watermark || w == coords;
            case "Full" -> true;
            default -> w.on();
        };
    }
}
