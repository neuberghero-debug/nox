package dev.noxclient.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * User-uploaded custom capes. Drop a PNG into <game>/nox/capes/ and pick
 * "Custom" as the cape style — the first valid file is loaded as a dynamic
 * texture. Standard capes are 64×32 (also accepts 22×17 elytra crops or any
 * decodable PNG, scaled by the renderer).
 */
public final class CustomCape {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("noxclient", "custom_cape");
    private static DynamicTexture tex;
    private static String loadedFrom, lastError;

    private CustomCape() {}

    public static Path dir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("nox").resolve("capes");
    }

    public static List<Path> available() {
        List<Path> out = new ArrayList<>();
        try (var s = Files.list(dir())) {
            s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
                .sorted().forEach(out::add);
        } catch (Exception ignored) {}
        return out;
    }

    /** Load the first PNG in nox/capes/. Returns an error string or null on success. */
    public static synchronized String reload() {
        unload();
        List<Path> files = available();
        if (files.isEmpty()) { lastError = "no PNG in nox/capes/"; return lastError; }
        Path p = files.get(0);
        try (InputStream in = Files.newInputStream(p)) {
            NativeImage img = NativeImage.read(in);
            if (img.getWidth() < 22 || img.getHeight() < 17) {
                img.close();
                lastError = "too small (" + img.getWidth() + "x" + img.getHeight() + ")";
                return lastError;
            }
            tex = new DynamicTexture(ID::toString, img);
            Minecraft.getInstance().getTextureManager().register(ID, tex);
            loadedFrom = p.getFileName().toString();
            lastError = null;
            return null;
        } catch (Exception e) {
            lastError = "bad PNG: " + e.getMessage();
            return lastError;
        }
    }

    public static synchronized void unload() {
        if (tex != null) {
            try { Minecraft.getInstance().getTextureManager().release(ID); } catch (Exception ignored) {}
            try { tex.close(); } catch (Exception ignored) {}
            tex = null; loadedFrom = null;
        }
    }

    public static boolean loaded() { return tex != null; }
    public static Identifier id() { return ID; }
    public static String loadedFrom() { return loadedFrom; }
    public static String lastError() { return lastError; }
}
