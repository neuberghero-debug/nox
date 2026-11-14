package dev.noxclient.module.render;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/**
 * Entity box ESP. Rendering hooks into the world-render event; state lives here
 * so the renderer can query which entity types and style to draw.
 */
public class ESP extends Module {
    public final Setting.Bool players = add(new Setting.Bool("Players", true));
    public final Setting.Bool mobs = add(new Setting.Bool("Mobs", false));
    public final Setting.Bool hostileOnly = add(new Setting.Bool("HostileOnly", true));
    public final Setting.Bool items = add(new Setting.Bool("Items", false));
    public final Setting.Mode style = add(new Setting.Mode("Style", "Box", "Box", "Glow", "Outline"));
    public final Setting.Col color = add(new Setting.Col("Color", 0x66FF4040));

    public ESP() { super("ESP", "See entities through walls", Category.RENDER); }
    @Override public String status() {
        if (mc.level == null) return "no level";
        int n = 0;
        for (var e : mc.level.entitiesForRendering()) if (e != mc.player) n++;
        return n + " entities tracked";
    }

}
