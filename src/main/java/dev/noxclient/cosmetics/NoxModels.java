package dev.noxclient.cosmetics;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cosmetic mesh registry. Built-ins ship as assets/noxclient/models/*.glb;
 * user customs drop into <gameDir>/nox/models/*.glb (same pipeline, picked
 * up live — Higgsfield-generated models just land there too).
 */
public final class NoxModels {
    private static final Map<String, GlbModel> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> MISSING = new ConcurrentHashMap<>();
    private static final Identifier WHITE =
        Identifier.fromNamespaceAndPath("noxclient", "textures/white.png");

    /** Resolve "<name>" -> parsed model, or null (cached; never throws). */
    public static GlbModel get(String name) {
        if (name == null || name.equals("Off")) return null;
        GlbModel m = CACHE.get(name);
        if (m != null) return m;
        if (MISSING.containsKey(name)) return null;
        try {
            // user customs first — overrides built-ins
            File f = new File(new File(Minecraft.getInstance().gameDirectory, "nox/models"),
                name.toLowerCase() + ".glb");
            if (f.isFile()) {
                try (InputStream in = new FileInputStream(f)) {
                    m = GlbModel.load(in);
                }
            }
            if (m == null) {
                var res = Minecraft.getInstance().getResourceManager().getResource(
                    Identifier.fromNamespaceAndPath("noxclient",
                        "models/" + name.toLowerCase() + ".glb"));
                if (res.isPresent()) {
                    try (InputStream in = res.get().open()) { m = GlbModel.load(in); }
                }
            }
        } catch (Throwable t) {
            System.err.println("[NOX] glb " + name + ": " + t);
            m = null;
        }
        if (m == null || m.prims.isEmpty()) { MISSING.put(name, true); return null; }
        CACHE.put(name, m);
        return m;
    }

    public static Identifier whiteTex() { return WHITE; }

    /** Drop customs cache entry so re-uploaded files reload. */
    public static void invalidate(String name) {
        CACHE.remove(name); MISSING.remove(name);
    }
}
