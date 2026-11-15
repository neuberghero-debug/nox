package dev.noxclient.module.render;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** HUD widgets drawn by NoxOverlay: watermark, module arraylist, stats line. */
public class HudWidgets extends Module {
    public final Setting.Bool watermark = add(new Setting.Bool("Watermark", true));
    public final Setting.Bool arraylist = add(new Setting.Bool("Module list", true));
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
    public final Setting.Bool compass = add(new Setting.Bool("Compass", true));
    public final Setting.Mode anchor = add(new Setting.Mode("Info anchor", "TopLeft",
        "TopLeft", "TopRight", "BottomLeft", "BottomRight"));
    public final Setting.Mode listSide = add(new Setting.Mode("List anchor", "TopRight",
        "TopRight", "TopLeft", "BottomRight", "BottomLeft"));

    public HudWidgets() { super("HudWidgets", "NOX HUD widgets", Category.RENDER); }
}
