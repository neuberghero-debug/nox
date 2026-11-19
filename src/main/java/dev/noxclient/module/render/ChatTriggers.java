package dev.noxclient.module.render;

import dev.noxclient.media.TwitchService;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.module.misc.ClipMarker;
import dev.noxclient.setting.Setting;
import dev.noxclient.gui.Toasts;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * ChatTriggers — Twitch chat controls the client through a small whitelist.
 * Watches the TwitchService line buffer for "<user>: !cmd"; only whitelisted
 * users fire, and every trigger is rate-limited per user. Commands are
 * deliberately non-leaky: !pos shows YOU the coords, nothing echoes back to
 * the stream.
 */
public class ChatTriggers extends Module {
    private final Setting.Str channel = add(new Setting.Str("Channel", "",
        "Same as TwitchChat — needed so the reader is running"));
    private final Setting.Str allow = add(new Setting.Str("Allowed users", "",
        "csv — e.g. 'yourname,moduser' — only these fire triggers"));
    private final Setting.Num cooldown = add(new Setting.Num("User cooldown (s)", 15, 3, 120));

    private final Map<String, Long> lastFire = new HashMap<>();
    private int seen;

    public ChatTriggers() { super("ChatTriggers", "Twitch chat runs whitelisted client commands",
        Category.RENDER); }

    @Override public void onTick() {
        if (mc.player == null) return;
        String ch = channel.get();
        if (!ch.isEmpty()) TwitchService.start(ch, 8);
        var lines = TwitchService.lines();
        if (seen > lines.size()) seen = 0;                  // buffer wrapped
        for (int i = seen; i < lines.size(); i++)
            handle(lines.get(i));
        seen = lines.size();
    }

    private void handle(String line) {
        int c = line.indexOf(':');
        if (c < 0) return;
        String user = line.substring(0, c).trim().toLowerCase();
        String msg = line.substring(c + 1).trim();
        if (!msg.startsWith("!") || !allowed(user)) return;
        Long lf = lastFire.get(user);
        if (lf != null && System.currentTimeMillis() - lf < cooldown.get() * 1000) return;
        lastFire.put(user, System.currentTimeMillis());
        String cmd = msg.substring(1).split(" ")[0].toLowerCase();
        switch (cmd) {
            case "clip" -> { ClipMarker.stamp("twitch !clip by " + user); fired(user, cmd); }
            case "rtp" -> { send("rtp"); fired(user, cmd); }
            case "kit" -> { send("kit"); fired(user, cmd); }
            case "pos" -> {                       // local-only — never leaks to chat
                var p = mc.player.blockPosition();
                Toasts.push("@" + user + " — pos: " + p.getX() + " " + p.getY() + " " + p.getZ(),
                    0xFF5AC8FF);
                fired(user, cmd);
            }
            case "stats" -> fired(user, cmd);      // toast below
            default -> { return; }
        }
    }

    private boolean allowed(String user) {
        String a = allow.get().trim().toLowerCase();
        if (a.isEmpty()) return false;
        for (String u : a.split(",")) if (u.trim().equals(user)) return true;
        return false;
    }

    private void fired(String user, String cmd) {
        if (mc.player != null)
            mc.player.sendSystemMessage(Component.literal(
                "§d[NOX] twitch @" + user + " fired !" + cmd));
        mark("!" + cmd + " by @" + user);
    }

    private void send(String cmd) {
        if (mc.getConnection() != null) mc.getConnection().sendCommand(cmd);
    }

    @Override public String status() {
        return isEnabled() ? "watching twitch for !triggers" : "off";
    }
}
