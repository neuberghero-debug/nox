package dev.noxclient.util;

import net.minecraft.network.protocol.Packet;

import java.util.concurrent.ConcurrentLinkedQueue;

/** Packet buffer shared between BlinkMixin (free jar) and the Blink module
 *  (premium pack). The mixin hooks the send path; the module drains. */
public final class BlinkBus {
    public static final ConcurrentLinkedQueue<Packet<?>> QUEUE = new ConcurrentLinkedQueue<>();
    public static volatile boolean holding;
    public static volatile boolean flushing;

    public static boolean holding() { return holding && !flushing; }

    private BlinkBus() {}
}
