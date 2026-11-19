package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * AutoCommander — generic DonutSMP automation: fires a rotating list of server
 * commands on a human-paced interval. Covers /kit, /daily, /vote claim, /sell,
 * /crates, /shop openers — anything you'd otherwise macro.
 * Syntax: comma-separated in the Commands setting, e.g. "kit daily, sellall, vote claim"
 */
public class AutoCommander extends Module {
    private final Setting.Str commands = add(new Setting.Str("Commands", "sellall"));
    private final Setting.Num interval = add(new Setting.Num("Interval (s)", 30, 5, 600));
    private final Setting.Bool chatLog = add(new Setting.Bool("Chat log", true));
    private final Queue<String> queue = new ArrayDeque<>();
    private long last;

    public AutoCommander() { super("AutoCommander", "Scheduled server commands", Category.DONUTSMP); }

    @Override
    protected void onEnable() {
        queue.clear();
        for (String c : commands.get().split(",")) {
            c = c.trim().replaceFirst("^/", "");
            if (!c.isEmpty()) queue.offer(c);
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.getConnection() == null || queue.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (!dev.noxclient.util.Legitifier.ready(this, 1.0 / interval.get(), 0.5)) return;
        last = now;
        String cmd = queue.poll();
        queue.offer(cmd); // rotate
        mc.getConnection().sendCommand(cmd);
        if (chatLog.on()) mc.player.sendSystemMessage(Component.literal("§7[NOX] /" + cmd));
    }
}
