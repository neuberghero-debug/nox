package dev.noxclient.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Stash index (Modul 7) — categorized, deduplicated, persistent index of
 * every stash/base-hunt hit across all finders (StorageESP, AntiESPBypass,
 * SoundFind, OreSim marks…). Sources write via add(); "/nox stashes [cat]"
 * lists nearest hits. Categories: chest, barrel, shulker, spawner, marked,
 * sound, ore, other. JSON at run/nox/stash-index.json survives restarts;
 * entries are per-dimension.
 */
public final class StashIndex {
    public record Hit(String src, String cat, String dim,
                      int x, int y, int z, long ts) {}

    private static final List<Hit> hits = new ArrayList<>();
    private static final Gson G = new Gson();
    private static boolean loaded;

    /** Dedup + record a hit. pos snapped to block; dim auto-detected. */
    public static synchronized void add(String src, String cat, BlockPos pos) {
        var mc = Minecraft.getInstance();
        String dim = mc.level != null
            ? mc.level.dimension().identifier().toString() : "?";
        for (Hit h : hits)
            if (h.dim.equals(dim) && h.x == pos.getX()
                && h.y == pos.getY() && h.z == pos.getZ()) return;
        hits.add(new Hit(src, cat, dim, pos.getX(), pos.getY(), pos.getZ(),
            System.currentTimeMillis()));
        if (hits.size() > 4000) hits.remove(0);          // cap
        saveAsync();
    }

    /** Nearest hits to the player (optionally filtered by category). */
    public static synchronized List<Hit> nearest(String cat, int max) {
        var mc = Minecraft.getInstance();
        String dim = mc.level != null
            ? mc.level.dimension().identifier().toString() : "?";
        var p = mc.player != null ? mc.player.position()
            : new net.minecraft.world.phys.Vec3(0, 0, 0);
        var list = new ArrayList<>(hits.stream()
            .filter(h -> h.dim.equals(dim))
            .filter(h -> cat == null || cat.isEmpty() || h.cat.equalsIgnoreCase(cat))
            .toList());
        list.sort(Comparator.comparingDouble(h ->
            Math.pow(h.x - p.x, 2) + Math.pow(h.y - p.y, 2) + Math.pow(h.z - p.z, 2)));
        return list.subList(0, Math.min(max, list.size()));
    }

    public static synchronized int size() { return hits.size(); }

    public static synchronized void clear() { hits.clear(); saveAsync(); }

    /* ---------- /nox stashes ---------- */

    public static void registerCommand() {
        dev.noxclient.api.AddonContext.COMMANDS.put("stashes", args -> {
            ensureLoaded();
            String cat = args.length > 0 ? args[0] : null;
            var near = nearest(cat, 8);
            var mc = Minecraft.getInstance();
            if (mc.player == null) return;
            if (near.isEmpty()) {
                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§7[NOX] no stash hits" + (cat != null ? " in '" + cat + "'" : "")));
                return;
            }
            StringBuilder sb = new StringBuilder("§6[NOX] stashes (" + hits.size() + " total): ");
            for (Hit h : near)
                sb.append("§e").append(h.cat).append("§7@").append(h.x)
                  .append(",").append(h.y).append(",").append(h.z)
                  .append("§8(").append(h.src).append(")§7 ");
            mc.player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal(sb.toString()));
        });
    }

    /* ---------- persistence ---------- */

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath()
            .resolve("nox").resolve("stash-index.json");
    }

    public static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            for (var e : JsonParser.parseString(Files.readString(f))
                    .getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                hits.add(new Hit(o.get("src").getAsString(),
                    o.get("cat").getAsString(), o.get("dim").getAsString(),
                    o.get("x").getAsInt(), o.get("y").getAsInt(),
                    o.get("z").getAsInt(), o.get("ts").getAsLong()));
            }
        } catch (Throwable ignored) {}
    }

    private static void saveAsync() {
        JsonArray a = new JsonArray();
        for (Hit h : hits) {
            JsonObject o = new JsonObject();
            o.addProperty("src", h.src); o.addProperty("cat", h.cat);
            o.addProperty("dim", h.dim);
            o.addProperty("x", h.x); o.addProperty("y", h.y); o.addProperty("z", h.z);
            o.addProperty("ts", h.ts);
            a.add(o);
        }
        String s = G.toJson(a);
        new Thread(() -> {
            try {
                Path f = file();
                Files.createDirectories(f.getParent());
                Path tmp = f.resolveSibling("stash-index.tmp");
                Files.writeString(tmp, s);
                Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Throwable ignored) {}
        }, "nox-stash-index").start();
    }
}
