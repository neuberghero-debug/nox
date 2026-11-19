package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Queues DonutSMP teleport commands (/tpa, /rtp, /warp, /home) and fires them
 * at a human pace when a target area is reached or requested — e.g. auto-TP to
 * a wanted/war zone. Targets are set from the GUI or chat trigger.
 */
public class AutoTP extends Module {
    private final Setting.Num intervalSec = add(new Setting.Num("Interval (s)", 6, 2, 30));
    private final Queue<String> queue = new ArrayDeque<>();
    private long last;

    public AutoTP() { super("AutoTP", "Queued auto-teleports", Category.DONUTSMP); }

    public void enqueue(String command) { queue.offer(command.startsWith("/") ? command : "/" + command); }
    public int pending() { return queue.size(); }

    @Override
    public void onTick() {
        if (mc.player == null || queue.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (!dev.noxclient.util.Legitifier.ready(this, 1.0 / intervalSec.get(), 0.5)) return;
        last = now;
        String cmd = queue.poll();
        mc.getConnection().sendCommand(cmd.substring(1));
        if (mc.player != null) mc.player.sendSystemMessage(
            Component.literal("§7[NOX] Sent " + cmd + " §8(" + queue.size() + " queued)"));
    }
}
