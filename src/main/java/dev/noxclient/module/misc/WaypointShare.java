package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.client.Minecraft;

/**
 * Shares your position: copies a NOXWP:name:x:y:z:dim tag to the clipboard
 * (paste to a friend) and/or posts it to a Discord webhook. Import side:
 * pasting a NOXWP tag into the Import box drops a map marker.
 */
public class WaypointShare extends Module {
    public final Setting.Str name = add(new Setting.Str("Label", "base"));
    public final Setting.Str webhook = add(new Setting.Str("Webhook URL", ""));
    public final Setting.Bool alsoClipboard = add(new Setting.Bool("To clipboard", true));

    public WaypointShare() { super("WaypointShare", "Share your coords", Category.MISC); }

    @Override public void onEnable() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) { setEnabled(false); return; }
        var p = mc.player.blockPosition();
        String dim = mc.level.dimension().identifier().getPath();
        String tag = "NOXWP:" + name.get() + ":" + p.getX() + ":" + p.getY() + ":"
            + p.getZ() + ":" + dim;
        if (alsoClipboard.on()) mc.keyboardHandler.setClipboard(tag);
        if (!webhook.get().isBlank())
            dev.noxclient.discord.Webhook.post(webhook.get(),
                "NOX waypoint " + name.get(), p.toShortString() + " (" + dim + ")");
        dev.noxclient.gui.Toasts.push("Waypoint shared: " + p.toShortString(), 0xFF66FF99);
        setEnabled(false);
    }

    /** Import a NOXWP tag → drops a marker at the position (via DeathWaypoint list). */
    public static boolean importTag(String s) {
        if (s == null || !s.startsWith("NOXWP:")) return false;
        String[] f = s.trim().split(":");
        if (f.length < 5) return false;
        try {
            var pos = new net.minecraft.core.BlockPos(
                Integer.parseInt(f[2]), Integer.parseInt(f[3]), Integer.parseInt(f[4]));
            dev.noxclient.module.donutsmp.AntiESPBypass.MARKS.put(pos, 0xFF40E080);
            dev.noxclient.gui.Toasts.push("Waypoint " + f[1] + " @ " + pos.toShortString(),
                0xFF66FF99);
            return true;
        } catch (NumberFormatException e) { return false; }
    }
}
