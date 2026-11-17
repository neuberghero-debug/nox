package dev.noxclient.module.render;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * SelfTag — renders a custom nametag above your own player in F5
 * (third person). Template placeholders: %name% %health% %ping% %item%.
 */
public class SelfTag extends Module {
    public final Setting.Str template = add(new Setting.Str("Format", "%name%"));
    public final Setting.Bool health = add(new Setting.Bool("Show Health", false));
    public final Setting.Bool ping = add(new Setting.Bool("Show Ping", false));
    public final Setting.Bool item = add(new Setting.Bool("Show Item", false));
    public final Setting.Bool others = add(new Setting.Bool("Only In F5", true));

    public SelfTag() { super("SelfTag", "Custom nametag above your own head", Category.RENDER); }

    /** Build the display text for the local player, or null when off. */
    public String text() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        // F5 check: first-person camera has the camera entity == player and no
        // third-person distance — options.getCameraType().isFirstPerson()
        if (others.on() && mc.options.getCameraType().isFirstPerson()) return null;
        String s = template.get().replace("%name%", mc.player.getGameProfile().name());
        if (health.on() || s.contains("%health%"))
            s = s.replace("%health%", (int) mc.player.getHealth() + "hp");
        if (ping.on() || s.contains("%ping%")) {
            int p = -1;
            var info = mc.getConnection() != null
                ? mc.getConnection().getPlayerInfo(mc.player.getUUID()) : null;
            if (info != null) p = info.getLatency();
            s = s.replace("%ping%", Math.max(p, 0) + "ms");
        }
        if (item.on() || s.contains("%item%")) {
            var held = mc.player.getMainHandItem();
            s = s.replace("%item%", held.isEmpty() ? "fist" : held.getHoverName().getString());
        }
        s = s.replace("%health%", "").replace("%ping%", "").replace("%item%", "");
        return s.isBlank() ? null : s.trim();
    }

    public Component component() {
        String s = text();
        return s == null ? null : Component.literal(s);
    }
}
