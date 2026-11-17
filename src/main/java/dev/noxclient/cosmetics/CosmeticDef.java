package dev.noxclient.cosmetics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.noxclient.module.render.Cosmetics;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * .cosmetic package format — a single JSON file describing one cosmetic item:
 * <pre>
 * {
 *   "format": 1,
 *   "name": "Frost Crown",
 *   "author": "Player123",
 *   "slot": "hat" | "pet" | "aura" | "cape" | "trail",
 *   "model": "frostcrown",            // glb name for hat/pet/aura
 *   "model_b64": "..."                // optional embedded glb bytes
 *   "cape_b64": "..."                 // optional embedded png for capes
 *   "tint": "#aabbcc",                // optional hex tint hint
 *   "scale": 1.0, "offY": -8.5,       // transform hints
 *   "premium": false
 * }
 * </pre>
 * Import writes embedded assets into nox/models|nox/capes, registers the
 * name and applies the matching Cosmetics setting.
 */
public final class CosmeticDef {
    public String name = "cosmetic", author = "", slot = "hat";
    public String model = "", modelB64 = "", capeB64 = "";
    public String tint = "";
    public double scale = 1.0, offY = 0;
    public boolean premium;

    public static CosmeticDef parse(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        CosmeticDef d = new CosmeticDef();
        d.name = str(o, "name", d.name);
        d.author = str(o, "author", "");
        d.slot = str(o, "slot", "hat").toLowerCase();
        d.model = str(o, "model", "");
        d.modelB64 = str(o, "model_b64", "");
        d.capeB64 = str(o, "cape_b64", "");
        d.tint = str(o, "tint", "");
        d.scale = num(o, "scale", 1.0);
        d.offY = num(o, "offY", 0);
        d.premium = o.has("premium") && o.get("premium").getAsBoolean();
        return d;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("format", 1);
        o.addProperty("name", name);
        o.addProperty("author", author);
        o.addProperty("slot", slot);
        if (!model.isEmpty()) o.addProperty("model", model);
        if (!modelB64.isEmpty()) o.addProperty("model_b64", modelB64);
        if (!capeB64.isEmpty()) o.addProperty("cape_b64", capeB64);
        if (!tint.isEmpty()) o.addProperty("tint", tint);
        o.addProperty("scale", scale);
        o.addProperty("offY", offY);
        o.addProperty("premium", premium);
        return o;
    }

    /** Install embedded assets under nox/ and return the registered name
     *  (sanitised, lower-case). Returns null when nothing installable. */
    public String installAssets() {
        try {
            String slug = name.toLowerCase().replaceAll("[^a-z0-9_-]", "_");
            File dir = new File(Minecraft.getInstance().gameDirectory, "nox");
            if (!modelB64.isEmpty()) {
                Path p = new File(dir, "models/" + slug + ".glb").toPath();
                Files.createDirectories(p.getParent());
                Files.write(p, Base64.getDecoder().decode(modelB64));
                NoxModels.invalidate(slug);
                model = slug;
                return slug;
            }
            if (!capeB64.isEmpty()) {
                Path p = new File(dir, "capes/" + slug + ".png").toPath();
                Files.createDirectories(p.getParent());
                Files.write(p, Base64.getDecoder().decode(capeB64));
                return slug;
            }
            if (!model.isEmpty()) return model;
        } catch (Exception e) {
            System.err.println("[NOX] cosmetic install: " + e);
        }
        return null;
    }

    /** Apply to the live Cosmetics module settings (slot-matched). */
    public void apply() {
        Cosmetics c = Cosmetics.INSTANCE;
        if (c == null) return;
        switch (slot) {
            case "cape" -> { c.capeStyle.set("Custom"); c.capeFile.set(name); }
            case "trail" -> c.trail.set(name);
            case "aura" -> { c.aura.set(name); c.auraScale.set(scale); }
            case "pet" -> { c.pet.set(name); c.petScale.set(scale); }
            case "hat" -> { c.hat.set(name); c.hatScale.set(scale);
                            c.hatOffY.set(offY); }
            default -> { }
        }
    }

    /** Export the client's current cosmetic for a slot into a .cosmetic file. */
    public static Path export(String slot) throws Exception {
        Cosmetics c = Cosmetics.INSTANCE;
        if (c == null) throw new IllegalStateException("cosmetics module missing");
        CosmeticDef d = new CosmeticDef();
        d.slot = slot.toLowerCase();
        var mc = Minecraft.getInstance();
        File dir = new File(mc.gameDirectory, "nox");
        switch (d.slot) {
            case "hat" -> {
                d.name = c.hat.get(); d.scale = c.hatScale.get();
                d.offY = c.hatOffY.get(); d.model = d.name;
                File g = new File(dir, "models/" + d.name.toLowerCase() + ".glb");
                if (g.isFile()) d.modelB64 = Base64.getEncoder()
                    .encodeToString(Files.readAllBytes(g.toPath()));
            }
            case "pet" -> {
                d.name = c.pet.get(); d.scale = c.petScale.get();
                d.model = d.name;
                File g = new File(dir, "models/" + d.name.toLowerCase() + ".glb");
                if (g.isFile()) d.modelB64 = Base64.getEncoder()
                    .encodeToString(Files.readAllBytes(g.toPath()));
            }
            case "aura" -> {
                d.name = c.meshAura.get(); d.scale = c.auraScale.get();
                d.model = d.name;
            }
            case "cape" -> {
                d.name = c.capeFile.get().isEmpty() ? "cape" : c.capeFile.get();
                File g = new File(dir, "capes/" + d.name);
                if (g.isFile()) d.capeB64 = Base64.getEncoder()
                    .encodeToString(Files.readAllBytes(g.toPath()));
            }
            case "trail" -> d.name = c.trail.get();
            default -> throw new IllegalArgumentException("slot: hat|pet|aura|cape|trail");
        }
        var prof = mc.getUser();
        d.author = prof != null ? prof.getName() : "";
        Path out = new File(dir, "cosmetics/" + d.slot + "-"
            + d.name.toLowerCase().replaceAll("[^a-z0-9_-]", "_")
            + ".cosmetic").toPath();
        Files.createDirectories(out.getParent());
        Files.writeString(out, d.toJson().toString());
        return out;
    }

    private static String str(JsonObject o, String k, String d) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : d;
    }

    private static double num(JsonObject o, String k, double d) {
        try { return o.has(k) ? o.get(k).getAsDouble() : d; }
        catch (Exception e) { return d; }
    }
}
