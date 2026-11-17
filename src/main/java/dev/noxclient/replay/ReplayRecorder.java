package dev.noxclient.replay;

import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.zip.GZIPOutputStream;

/**
 * .donutreplay recorder (Modul 22, record side).
 *
 * Ring buffer: 10 min at 4 Hz snapshots → small gzip files.
 * Frame format (binary, delta-friendly):
 *   u32 ms-since-start | u16 entity-count
 *   per entity: i32 id | u8 type-tag | f32 x,y,z | f32 yaw,pitch | u8 flags
 * Event records (chat, module toggles, damage) are appended as u8 tag 0xF0+
 * frames with a UTF-8 payload — the playback screen reads the same spec.
 */
public final class ReplayRecorder {
    private static final int MAX_FRAMES = 10 * 60 * 4;          // 10 min @ 4Hz
    private static final Deque<byte[]> frames = new ArrayDeque<>();
    private static long startMs, lastSample;
    private static boolean recording;

    public static boolean recording() { return recording; }
    public static int frames() { return frames.size(); }

    public static void start() {
        frames.clear();
        startMs = System.currentTimeMillis();
        recording = true;
        event("rec_start", "");
    }

    public static void stop() { recording = false; }

    /** 4 Hz sampler — call per client tick. */
    public static void tick() {
        if (!recording) return;
        long now = System.currentTimeMillis();
        if (now - lastSample < 250) return;
        lastSample = now;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        ByteArrayOutputStream bos = new ByteArrayOutputStream(512);
        DataOutputStream d = new DataOutputStream(bos);
        try {
            d.writeInt((int) (now - startMs));
            var list = new java.util.ArrayList<Entity>();
            for (Entity e : mc.level.entitiesForRendering())
                if (e.distanceTo(mc.player) < 128) list.add(e);
            d.writeShort(list.size());
            for (Entity e : list) {
                d.writeInt(e.getId());
                d.writeByte(tag(e));
                d.writeFloat((float) e.getX());
                d.writeFloat((float) e.getY());
                d.writeFloat((float) e.getZ());
                d.writeFloat(e.getYRot());
                d.writeFloat(e.getXRot());
                d.writeByte(flags(e));
            }
            d.flush();
            push(bos.toByteArray());
        } catch (IOException ignored) {}
    }

    /** Tagged event frame: chat, toggles, damage, kills. */
    public static void event(String kind, String data) {
        if (!recording) return;
        ByteArrayOutputStream bos = new ByteArrayOutputStream(64);
        try {
            DataOutputStream d = new DataOutputStream(bos);
            d.writeInt((int) (System.currentTimeMillis() - startMs));
            d.writeByte(0xF0);
            d.writeUTF(kind);
            d.writeUTF(data == null ? "" : data.substring(0, Math.min(200, data.length())));
            d.flush();
            push(bos.toByteArray());
        } catch (IOException ignored) {}
    }

    private static void push(byte[] frame) {
        synchronized (frames) {
            if (frames.size() >= MAX_FRAMES) frames.pollFirst();
            frames.addLast(frame);
        }
    }

    /** Write the buffered replay to run/nox/replays/<ts>.donutreplay (gzip). */
    public static Path save() {
        try {
            Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("nox/replays");
            Files.createDirectories(dir);
            Path f = dir.resolve("rec-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + ".donutreplay");
            try (var out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(f)))) {
                out.writeInt(0x444E5250);              // 'DNRP' magic
                out.writeShort(1);                     // format version
                out.writeUTF("NOX " + dev.noxclient.update.UpdateManager.currentVersion());
                synchronized (frames) {
                    out.writeInt(frames.size());
                    for (byte[] fr : frames) { out.writeInt(fr.length); out.write(fr); }
                }
            }
            return f;
        } catch (IOException e) { return null; }
    }

    private static byte tag(Entity e) {
        if (e instanceof net.minecraft.world.entity.player.Player) return 1;
        if (e instanceof net.minecraft.world.entity.item.ItemEntity) return 2;
        if (e instanceof net.minecraft.world.entity.projectile.Projectile) return 3;
        return 0;
    }

    private static byte flags(Entity e) {
        int f = 0;
        if (e.isSprinting()) f |= 1;
        if (e.isCrouching()) f |= 2;
        if (e.isInvisible()) f |= 4;
        return (byte) f;
    }

    /** Small metadata sidecar for clip/replay tooling (Modul 2.3). */
    public static JsonObject meta() {
        JsonObject o = new JsonObject();
        o.addProperty("frames", frames.size());
        o.addProperty("ms", System.currentTimeMillis() - startMs);
        o.addProperty("version", dev.noxclient.update.UpdateManager.currentVersion());
        return o;
    }
}
