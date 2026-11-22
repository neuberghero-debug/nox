package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Staff Live — watches the tab list for known staff names and shows who is
 * currently online, for how long, and pings you on join/leave transitions.
 * Pair with AntiVanish: a name that drops off the list while its entity is
 * still rendered is probably vanished, not offline.
 *
 * The list is fully editable — comma-separated, case-insensitive. Seeded
 * with the public DonutSMP owner; add your server's admins/mods.
 */
public class StaffLive extends Module {
    public final Setting.Str staff = add(new Setting.Str("Staff list",
        "drdonut,onlyvg,wemmbu"));
    public final Setting.Bool panel = add(new Setting.Bool("Panel", true));
    public final Setting.Bool showOffline = add(new Setting.Bool("Show offline", false));
    public final Setting.Bool toasts = add(new Setting.Bool("Join/leave toasts", true));
    public final Setting.Bool sounds = add(new Setting.Bool("Sound", true));
    public final Setting.Num poll = add(new Setting.Num("Poll interval", 3, 1, 15, "s"));

    /** name(lower) → join epoch ms while online; presence = online. */
    private static final Map<String, Long> online = new LinkedHashMap<>();
    private int tick;

    public StaffLive() { super("StaffLive", "Shows which staff are online", Category.DONUTSMP); }

    /** name → join time for every listed staff currently in the tab list. */
    public static Map<String, Long> online() { return online; }

    @Override public void onDisable() { online.clear(); }

    @Override public void onTick() {
        if (mc.getConnection() == null) return;
        if (++tick % (poll.intVal() * 20) != 0) return;

        // staff set from the editable list
        java.util.Set<String> wanted = new java.util.HashSet<>();
        for (String n : staff.get().split(",")) {
            String t = n.trim().toLowerCase(java.util.Locale.ROOT);
            if (!t.isEmpty()) wanted.add(t);
        }

        java.util.Set<String> seen = new java.util.HashSet<>();
        for (PlayerInfo pi : mc.getConnection().getListedOnlinePlayers()) {
            String name = pi.getProfile().name();
            if (name == null) continue;
            String low = name.toLowerCase(java.util.Locale.ROOT);
            if (!wanted.contains(low)) continue;
            seen.add(low);
            if (!online.containsKey(low)) {
                online.put(low, System.currentTimeMillis());
                if (toasts.on()) dev.noxclient.gui.Toasts.push(
                    "Staff online: " + name, 0xFFFF5C5C);
                if (sounds.on()) mc.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, .6f, .8f));
            }
        }
        // departures
        var it = online.keySet().iterator();
        while (it.hasNext()) {
            String low = it.next();
            if (!seen.contains(low)) {
                it.remove();
                if (toasts.on()) dev.noxclient.gui.Toasts.push(
                    "Staff left: " + low, 0xFF8A8A9E);
            }
        }
    }

    @Override public String status() {
        return online.isEmpty() ? "no staff" : online.size() + " staff online";
    }
}
