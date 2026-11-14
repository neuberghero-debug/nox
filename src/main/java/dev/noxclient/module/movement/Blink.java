package dev.noxclient.module.movement;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.network.protocol.Packet;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Blink [PREMIUM] — buffers every outgoing packet while enabled; on disable
 * they flush instantly and the server sees you teleport (lag-switch). The
 * mixin on Connection.send feeds this queue. "Pulse" mode auto-flushes every
 * N ticks for a fake-lag effect.
 */
public class Blink extends Module {
    private final Setting.Mode mode = add(new Setting.Mode("Mode", "Hold", "Hold", "Pulse"));
    private final Setting.Num pulseTicks = add(new Setting.Num("Pulse ticks", 20, 5, 60));
    public static final ConcurrentLinkedQueue<Packet<?>> QUEUE = new ConcurrentLinkedQueue<>();
    private static volatile boolean holding;
    private static volatile boolean flushing;
    private int tick;

    public Blink() { super("Blink", "Buffer + dump packets", Category.MOVEMENT); premium = true; }

    public static boolean holding() { return holding && !flushing; }

    @Override
    protected void onEnable() { holding = true; }
    @Override
    protected void onDisable() { holding = false; flush(); }

    @Override
    public void onTick() {
        if (!mode.get().equals("Pulse")) return;
        if (++tick >= pulseTicks.intVal()) { tick = 0; flush(); }
    }

    private void flush() {
        if (mc.getConnection() == null) { QUEUE.clear(); return; }
        // without the flag the send-mixin would re-queue everything we dump
        flushing = true;
        try {
            Packet<?> p;
            while ((p = QUEUE.poll()) != null) mc.getConnection().send(p);
        } finally {
            flushing = false;
        }
    }
}
