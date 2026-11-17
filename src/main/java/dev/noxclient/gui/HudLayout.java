package dev.noxclient.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;

/**
 * Free-position layout for HUD elements. Positions are stored normalized
 * (0..1 of the HUD-scaled screen) so they survive resolution and GUI-scale
 * changes. Elements without an entry keep their default anchor. Persisted to
 * run/nox/hud.json — independent of config presets so a layout survives
 * preset switches.
 */
public final class HudLayout {
    private HudLayout() {}

    /** [x, y] normalized top-left position in HUD space. */
    private static final Map<String, float[]> pos = new HashMap<>();
    /** Last rendered screen-space rects (mx/my coords) for editor hit tests. */
    private static final Map<String, int[]> rects = new HashMap<>();
    /** id → last report ms — entries not re-reported within TTL are stale. */
    private static final Map<String, Long> seen = new HashMap<>();
    private static final long TTL_MS = 400;
    private static boolean loaded, dirty;

    public static float[] get(String id) { load(); return pos.get(id); }
    public static boolean has(String id) { return get(id) != null; }

    public static void set(String id, float x, float y) {
        load();
        pos.put(id, new float[]{Math.clamp(x, 0f, 1f), Math.clamp(y, 0f, 1f)});
        dirty = true;
    }

    public static void reset(String id) { load(); pos.remove(id); dirty = true; }
    public static void resetAll() { load(); pos.clear(); dirty = true; }

    /** Called by NoxOverlay each frame — screen-space rect the element filled. */
    public static void report(String id, int x, int y, int w, int h) {
        int[] r = rects.get(id);
        if (r == null) rects.put(id, new int[]{x, y, w, h});
        else { r[0] = x; r[1] = y; r[2] = w; r[3] = h; }
        seen.put(id, System.currentTimeMillis());
    }

    /** Screen-space rect, or null if the element isn't rendering right now.
     *  Stale entries (element stopped rendering) are expired and removed. */
    public static int[] rect(String id) {
        Long at = seen.get(id);
        if (at == null) return null;
        if (System.currentTimeMillis() - at > TTL_MS) {
            rects.remove(id); seen.remove(id);
            return null;
        }
        return rects.get(id);
    }

    private static File file() {
        return new File(new File(Minecraft.getInstance().gameDirectory, "nox"), "hud.json");
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            File f = file();
            if (!f.isFile()) return;
            JsonObject o = JsonParser.parseString(Files.readString(f.toPath())).getAsJsonObject();
            for (var e : o.entrySet()) {
                var a = e.getValue().getAsJsonArray();
                pos.put(e.getKey(), new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat()});
            }
        } catch (Throwable t) {
            System.out.println("[NOX] hud.json unreadable, starting fresh: " + t);
        }
    }

    public static void save() {
        if (!dirty) return;
        try {
            JsonObject o = new JsonObject();
            for (var e : pos.entrySet()) {
                var a = new com.google.gson.JsonArray();
                a.add(e.getValue()[0]); a.add(e.getValue()[1]);
                o.add(e.getKey(), a);
            }
            File f = file();
            f.getParentFile().mkdirs();
            File tmp = new File(f.getParent(), "hud.json.tmp");
            Files.writeString(tmp.toPath(), o.toString());
            Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (Throwable t) {
            System.out.println("[NOX] hud.json save failed: " + t);
        }
    }
}
