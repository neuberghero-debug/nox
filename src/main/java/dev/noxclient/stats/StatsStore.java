package dev.noxclient.stats;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lifetime counters persisted to nox/stats.json (Modul 26.2).
 * Local only — sync endpoint is opt-in via telemetry, not wired here.
 * All counters are atomic; saveAsync batches on a virtual thread.
 */
public final class StatsStore {
    private static final ConcurrentHashMap<String, AtomicLong> lifetime = new ConcurrentHashMap<>();
    private static volatile boolean loaded, dirty;

    static void load() {
        if (loaded) return; loaded = true;
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            for (var e : o.entrySet())
                lifetime.put(e.getKey(), new AtomicLong(e.getValue().getAsLong()));
        } catch (Throwable ignored) {}
    }

    public static void bump(String key) { bump(key, 1); }
    public static void bump(String key, long n) {
        lifetime.computeIfAbsent(key, k -> new AtomicLong()).addAndGet(n);
        dirty = true;
    }

    public static long lifetime(String key) {
        AtomicLong v = lifetime.get(key);
        return v == null ? 0 : v.get();
    }

    public static void saveAsync() {
        if (!dirty) return;
        dirty = false;
        JsonObject o = new JsonObject();
        for (var e : lifetime.entrySet()) o.addProperty(e.getKey(), e.getValue().get());
        Thread.ofVirtual().start(() -> {
            try {
                Files.createDirectories(file().getParent());
                Path tmp = file().resolveSibling("stats.json.tmp");
                Files.writeString(tmp, o.toString());
                Files.move(tmp, file(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Throwable ignored) {}
        });
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("nox/stats.json");
    }
}
