package dev.noxclient.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CoordBook (Modul 5, F.3) — persistent coordinate book. Every coord the
 * CoordFinder extracts lands here with a label + timestamp; entries render
 * as Gizmo markers (billboard text + vertical beam) while the module is on.
 *
 * Persisted to run/nox/coords.json — survives restarts, shared across worlds
 * (dim field kept for future per-dimension filtering).
 */
public final class CoordBook {
    public record Entry(int x, int y, int z, String dim, String label, long ts) {}
    private static final List<Entry> entries = new ArrayList<>();
    private static boolean loaded;

    public static List<Entry> entries() { load(); return entries; }

    public static Entry add(int x, int y, int z, String label) {
        load();
        String dim = "unknown";
        var mc = Minecraft.getInstance();
        if (mc.level != null) dim = mc.level.dimension().identifier().toString();
        Entry e = new Entry(x, y, z, dim, label == null ? "" : label, System.currentTimeMillis());
        // dedupe: same coords within a minute replaces the older label
        entries.removeIf(o -> o.x() == x && o.y() == y && o.z() == z);
        entries.add(e);
        save();
        return e;
    }

    public static boolean remove(int index) {
        load();
        if (index < 0 || index >= entries.size()) return false;
        entries.remove(index); save(); return true;
    }

    public static void clear() { load(); entries.clear(); save(); }

    /** Emit Gizmo markers for every entry — call from the overlay emitter. */
    public static void emit() {
        load();
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        String dim = mc.level.dimension().identifier().toString();
        for (Entry e : entries) {
            if (!e.dim().equals(dim)) continue;
            Vec3 p = new Vec3(e.x() + 0.5, e.y() + 1.0, e.z() + 0.5);
            Gizmos.billboardText(e.label().isEmpty()
                ? e.x() + " " + e.y() + " " + e.z() : e.label(), p,
                net.minecraft.gizmos.TextGizmo.Style.forColorAndCentered(0xFF7AFFE0))
                .setAlwaysOnTop();
            // thin vertical beam so the spot is findable at distance
            for (int i = 0; i < 12; i++)
                Gizmos.point(new Vec3(e.x() + 0.5, e.y() + i, e.z() + 0.5),
                    0xFF7AFFE0, 0.15f).setAlwaysOnTop();
        }
    }

    /* ---------- persistence ---------- */

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("nox/coords.json");
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.exists(file())) return;
            JsonArray arr = new Gson().fromJson(Files.readString(file()), JsonArray.class);
            if (arr == null) return;
            for (var el : arr) {
                JsonObject o = el.getAsJsonObject();
                entries.add(new Entry(o.get("x").getAsInt(), o.get("y").getAsInt(),
                    o.get("z").getAsInt(), o.has("dim") ? o.get("dim").getAsString() : "unknown",
                    o.has("label") ? o.get("label").getAsString() : "", o.get("ts").getAsLong()));
            }
        } catch (Exception ignored) {}
    }

    private static void save() {
        try {
            Files.createDirectories(file().getParent());
            JsonArray arr = new JsonArray();
            for (Entry e : entries) {
                JsonObject o = new JsonObject();
                o.addProperty("x", e.x()); o.addProperty("y", e.y()); o.addProperty("z", e.z());
                o.addProperty("dim", e.dim()); o.addProperty("label", e.label());
                o.addProperty("ts", e.ts());
                arr.add(o);
            }
            Files.writeString(file(), new Gson().toJson(arr));
        } catch (Exception ignored) {}
    }
}
