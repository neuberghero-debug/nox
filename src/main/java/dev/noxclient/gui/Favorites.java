package dev.noxclient.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import dev.noxclient.module.Module;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pinned modules (persisted to nox-favorites.json) + last-toggled list
 * (session-only) — feeds the sidebar's Favorites/Recent topics. */
public final class Favorites {
    private static final Set<String> FAVS = new LinkedHashSet<>();
    private static final List<String> RECENT = new ArrayList<>();
    private static boolean loaded;

    private Favorites() {}

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("nox-favorites.json");
    }

    private static void ensure() {
        if (loaded) return;
        loaded = true;
        try {
            String s = Files.readString(file());
            for (var el : JsonParser.parseString(s).getAsJsonArray())
                FAVS.add(el.getAsString());
        } catch (Throwable ignored) {}
    }

    private static void save() {
        try {
            JsonArray a = new JsonArray();
            for (String n : FAVS) a.add(n);
            Files.writeString(file(), a.toString());
        } catch (Throwable ignored) {}
    }

    public static boolean is(Module m) { ensure(); return FAVS.contains(m.name); }
    public static void toggle(Module m) {
        ensure();
        if (!FAVS.remove(m.name)) FAVS.add(m.name);
        save();
    }

    /** Called whenever a module is toggled from the GUI — keeps Recent fresh. */
    public static void markUsed(Module m) {
        RECENT.remove(m.name);
        RECENT.add(0, m.name);
        if (RECENT.size() > 16) RECENT.remove(RECENT.size() - 1);
    }

    public static List<String> favNames() { ensure(); return List.copyOf(FAVS); }
    public static List<String> recentNames() { return List.copyOf(RECENT); }
}
