package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Auto-accepts /tpa requests — optionally only from friends. */
public class AutoTPAccept extends Module {
    public final Setting.Bool friendsOnly = add(new Setting.Bool("Friends only", false));
    private long cooldown;

    public AutoTPAccept() { super("AutoTPAccept", "Accepts teleport requests", Category.DONUTSMP); }

    /** Hooked from chat listener in NoxClient — call on every inbound chat line. */
    public static void onChat(String raw) {
        var mod = dev.noxclient.NoxClient.INSTANCE.modules.get("AutoTPAccept");
        if (!(mod instanceof AutoTPAccept m) || !m.isEnabled()) return;
        String s = net.minecraft.util.StringUtil.stripColor(raw);
        if (s == null) return;
        String l = s.toLowerCase();
        if (!l.contains("requested to teleport") && !l.contains("teleport request")
            && !l.contains("wants to teleport")) return;
        if (System.currentTimeMillis() - m.cooldown < 2000) return;
        if (m.friendsOnly.on()) {
            boolean friend = false;
            for (String line : s.split("[^a-zA-Z0-9_]+"))
                if (dev.noxclient.module.misc.Friends.isFriendName(line)) { friend = true; break; }
            if (!friend) return;
        }
        m.cooldown = System.currentTimeMillis();
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().sendCommand("tpaccept");
    }
}
