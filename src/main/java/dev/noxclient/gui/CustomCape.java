package dev.noxclient.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.noxclient.module.render.Cosmetics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Local file override > configured URL > first local PNG > no override.
 * I/O and decode run on a bounded worker, GPU ownership stays on the client
 * thread. A generation token prevents late uploads after reload/disconnect.
 */
public final class CustomCape {
    private static final int MAX_BYTES = 4 * 1024 * 1024, MAX_FRAMES = 32, CACHE_SIZE = 4;
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(1), r -> {
            Thread t = new Thread(r, "nox-cape-loader"); t.setDaemon(true); return t;
        }, new ThreadPoolExecutor.DiscardOldestPolicy());
    private static final Map<String, Frames> CACHE = new LinkedHashMap<>(8, .75f, true);
    private static String requested = "", loadedFrom, lastError, loggedError = "";
    private static volatile long generation;
    private static long retryAt;
    private static boolean loading;
    private static Frames active;

    private record Decoded(NativeImage[] images, int[] delays, String name, String hash) {
        void close() { for (NativeImage image : images) if (image != null) image.close(); }
    }
    private static final class Frames {
        final Identifier[] ids;
        final int[] delays;
        final int total;
        final String name;
        long lastNanos, elapsedMs;
        Frames(Identifier[] ids, int[] delays, String name) {
            this.ids = ids; this.delays = delays; this.name = name;
            int sum = 0; for (int delay : delays) sum += delay; total = sum;
        }
        Identifier current() {
            long now = System.nanoTime();
            if (lastNanos != 0 && !Minecraft.getInstance().isPaused()) elapsedMs += (now - lastNanos) / 1_000_000;
            lastNanos = now;
            long offset = elapsedMs % total;
            for (int i = 0; i < ids.length; i++) { if (offset < delays[i]) return ids[i]; offset -= delays[i]; }
            return ids[0];
        }
        void release() {
            for (Identifier id : ids) if (id != null) Minecraft.getInstance().getTextureManager().release(id);
        }
    }
    private CustomCape() {}
    public static Path dir() { return Minecraft.getInstance().gameDirectory.toPath().resolve("nox").resolve("capes"); }

    /** File listing is for explicit editor/debug actions, never the render loop. */
    public static List<Path> available() { return available(dir()); }
    private static List<Path> available(Path directory) {
        List<Path> out = new ArrayList<>();
        try (var stream = Files.list(directory)) {
            stream.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png"))
                .sorted().limit(128).forEach(out::add);
        } catch (IOException ignored) {}
        return out;
    }

    /** Queue a forced refresh. Completion is reported asynchronously. */
    public static String reload() { request(true); return null; }
    public static void request() { request(false); }
    private static void request(boolean force) {
        Cosmetics cosmetics = Cosmetics.INSTANCE;
        String file = cosmetics == null ? "" : cosmetics.capeFile.get().strip();
        String url = cosmetics == null ? "" : cosmetics.capeUrl.get().strip();
        String key = file + "\n" + url;
        if (!force && key.equals(requested) && (active != null || loading || System.nanoTime() < retryAt)) return;
        if (!force) {
            Frames cached = CACHE.get(key);
            if (cached != null) { generation++; loading = false; requested = key; active = cached; loadedFrom = cached.name; return; }
        }
        long token = ++generation;
        requested = key; active = null; loadedFrom = null; loading = true; lastError = null;
        Minecraft mc = Minecraft.getInstance();
        Path directory = dir(), disk = directory.resolve(".cache");
        WORKER.execute(() -> {
            Decoded decoded = null;
            String error = null;
            try { decoded = decode(directory, disk, file, url); }
            catch (Exception ex) { error = ex.getClass().getSimpleName() + ": " + ex.getMessage(); }
            Decoded result = decoded; String failure = error;
            if (token != generation) { if (result != null) result.close(); return; }
            mc.execute(() -> {
                if (token != generation) { if (result != null) result.close(); return; }
                loading = false;
                if (failure != null) {
                    lastError = failure; retryAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                    String message = key + failure;
                    if (!loggedError.equals(message)) {
                        // Avoid logging a URL that might carry a private signed query.
                        org.slf4j.LoggerFactory.getLogger("NOX").warn("Custom cape unavailable: {}", failure.replace(url, url.isEmpty() ? "" : "<cape URL>"));
                        loggedError = message;
                    }
                    return;
                }
                upload(key, result, token);
            });
        });
    }
    private static Decoded decode(Path directory, Path disk, String file, String url) throws Exception {
        Path local = null;
        byte[] bytes;
        String name;
        if (!file.isEmpty()) {
            local = directory.resolve(file).normalize();
            if (!local.startsWith(directory.normalize())) throw new IOException("Cape file must be inside nox/capes");
            bytes = readBounded(local); name = local.getFileName().toString();
        } else if (!url.isEmpty()) {
            bytes = fetch(url, disk); name = "URL cape";
        } else {
            List<Path> files = available(directory);
            if (files.isEmpty()) throw new IOException("No PNG in nox/capes");
            local = files.get(0); bytes = readBounded(local); name = local.getFileName().toString();
        }
        if (bytes.length < 24 || ByteBuffer.wrap(bytes).getLong() != 0x89504E470D0A1A0AL)
            throw new IOException("Not a PNG");
        int width = ByteBuffer.wrap(bytes, 16, 4).getInt(), height = ByteBuffer.wrap(bytes, 20, 4).getInt();
        if (width < 64 || width > 1024 || width % 64 != 0 || height < width / 2 || height > 16384
            || (long) width * height > 8_388_608) throw new IOException("Unsupported cape dimensions");
        JsonObject metadata = null;
        if (local != null) {
            Path sidecar = local.resolveSibling(local.getFileName() + ".json");
            if (Files.exists(sidecar)) {
                if (Files.size(sidecar) > 65536) throw new IOException("Cape frame table too large");
                metadata = JsonParser.parseString(Files.readString(sidecar)).getAsJsonObject();
            }
        }
        int count = metadata != null && metadata.has("frameCount") ? metadata.get("frameCount").getAsInt() : 1;
        if (count < 1 || count > MAX_FRAMES) throw new IOException("Cape needs 1..32 frames");
        int frameH = width / 2;
        if (height != frameH * count && !(count == 1 && height == width))
            throw new IOException("Use a 64x32 cape, square cape, HD multiple, or explicit frameCount");
        var table = metadata != null && metadata.has("frames") ? metadata.getAsJsonArray("frames") : null;
        int tableSize = table == null ? count : table.size();
        if (tableSize < 1 || tableSize > MAX_FRAMES) throw new IOException("Frame table needs 1..32 entries");
        NativeImage[] images = new NativeImage[tableSize];
        int[] delays = new int[tableSize];
        try (NativeImage input = NativeImage.read(bytes)) {
            int[] pixels = input.getPixelsABGR();
            for (int i = 0; i < tableSize; i++) {
                int index = i, delay = metadata != null && metadata.has("frameTimeMs") ? metadata.get("frameTimeMs").getAsInt() : 100;
                if (table != null) {
                    JsonObject entry = table.get(i).getAsJsonObject();
                    index = entry.get("index").getAsInt(); delay = entry.get("durationMs").getAsInt();
                }
                if (index < 0 || index >= count || delay < 16 || delay > 60000) throw new IOException("Invalid frame index/duration");
                images[i] = new NativeImage(64, 32, true);
                int scale = width / 64;
                for (int y = 0; y < 32; y++) for (int x = 0; x < 64; x++)
                    images[i].setPixelABGR(x, y, pixels[(index * frameH + y * scale) * width + x * scale]);
                delays[i] = delay;
            }
        } catch (Exception e) {
            for (NativeImage image : images) if (image != null) image.close();
            throw e;
        }
        return new Decoded(images, delays, name, hash(bytes));
    }
    private static byte[] readBounded(Path path) throws IOException {
        if (Files.size(path) > MAX_BYTES) throw new IOException("Cape exceeds 4 MiB");
        try (var in = Files.newInputStream(path)) {
            byte[] data = in.readNBytes(MAX_BYTES + 1);
            if (data.length > MAX_BYTES) throw new IOException("Cape exceeds 4 MiB");
            return data;
        }
    }
    private static byte[] fetch(String address, Path disk) throws Exception {
        URI uri = URI.create(address);
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))
            throw new IOException("Cape URL must use HTTP or HTTPS");
        Files.createDirectories(disk);
        Path cached = disk.resolve(hash(address.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + ".png");
        if (Files.exists(cached) && System.currentTimeMillis() - Files.getLastModifiedTime(cached).toMillis() < 86_400_000)
            return readBounded(cached);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setConnectTimeout(5000); conn.setReadTimeout(8000);
        conn.setRequestProperty("User-Agent", "NOX-Cape/1");
        try {
            int status = conn.getResponseCode();
            if (status != 200) throw new IOException("HTTP " + status);
            if (conn.getContentLengthLong() > MAX_BYTES) throw new IOException("Cape exceeds 4 MiB");
            byte[] data;
            try (var stream = conn.getInputStream()) { data = stream.readNBytes(MAX_BYTES + 1); }
            if (data.length > MAX_BYTES) throw new IOException("Cape exceeds 4 MiB");
            Path tmp = Files.createTempFile(disk, "cape-", ".tmp");
            try {
                Files.write(tmp, data);
                Files.move(tmp, cached, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(tmp); }
            try (var files = Files.list(disk)) {
                List<Path> entries = files.filter(p -> p.toString().endsWith(".png"))
                    .sorted(java.util.Comparator.comparingLong(p -> p.toFile().lastModified())).toList();
                for (int i = 0; i < entries.size() - 16; i++) Files.deleteIfExists(entries.get(i));
            }
            return data;
        } catch (IOException e) {
            if (Files.exists(cached)) return readBounded(cached);
            throw e;
        } finally { conn.disconnect(); }
    }
    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static void upload(String key, Decoded decoded, long token) {
        Identifier[] ids = new Identifier[decoded.images.length];
        int transferred = 0;
        try {
            for (int i = 0; i < ids.length; i++) {
                Identifier id = Identifier.fromNamespaceAndPath("noxclient", "cape/" + decoded.hash + "/" + token + "/" + i);
                DynamicTexture texture = new DynamicTexture(() -> "nox_custom_cape", decoded.images[i]);
                try {
                    texture.upload();
                    Minecraft.getInstance().getTextureManager().register(id, texture);
                } catch (RuntimeException e) { texture.close(); decoded.images[i] = null; throw e; }
                ids[i] = id; transferred++;
            }
            Frames frames = new Frames(ids, decoded.delays, decoded.name);
            Frames old = CACHE.put(key, frames);
            if (old != null) old.release();
            while (CACHE.size() > CACHE_SIZE) {
                var iterator = CACHE.entrySet().iterator();
                Frames removed = iterator.next().getValue(); iterator.remove(); removed.release();
            }
            active = frames; loadedFrom = frames.name; lastError = null; loggedError = "";
        } catch (RuntimeException e) {
            for (Identifier id : ids) if (id != null) Minecraft.getInstance().getTextureManager().release(id);
            for (int i = transferred; i < decoded.images.length; i++) if (decoded.images[i] != null) decoded.images[i].close();
            lastError = "Texture upload failed"; retryAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            org.slf4j.LoggerFactory.getLogger("NOX").warn("Custom cape upload failed", e);
        }
    }
    /** Client-thread lifecycle callback; stale worker results close their decoded images. */
    public static void unload() {
        generation++; loading = false; requested = ""; active = null; loadedFrom = null; retryAt = 0;
        for (Frames frames : CACHE.values()) frames.release();
        CACHE.clear();
    }
    public static boolean loaded() { return active != null; }
    public static Identifier id() { request(); return active == null ? null : active.current(); }
    public static String loadedFrom() { return loading ? "Loading…" : loadedFrom; }
    public static String lastError() { return lastError; }
}
