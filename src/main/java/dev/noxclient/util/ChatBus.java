package dev.noxclient.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Inbound system-chat lines → any module that subscribes. ChatMixin feeds it. */
public final class ChatBus {
    private static final Map<String, Consumer<String>> SUBS = new ConcurrentHashMap<>();
    private ChatBus() {}
    public static void register(String name, Consumer<String> fn) { SUBS.put(name, fn); }
    public static void unregister(String name) { SUBS.remove(name); }
    public static void feed(String raw) {
        for (var fn : SUBS.values())
            try { fn.accept(raw); } catch (Throwable ignored) {}
    }
}
