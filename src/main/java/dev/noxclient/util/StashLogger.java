package dev.noxclient.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/** Appends base-hunt hits to run/nox/stashes.json — survives restarts. */
public final class StashLogger {
    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("nox/stashes.json");
    }

    public static synchronized void log(String source, BlockPos pos, String note) {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            JsonArray arr = Files.exists(f)
                ? JsonParser.parseString(Files.readString(f)).getAsJsonArray()
                : new JsonArray();
            JsonObject o = new JsonObject();
            o.addProperty("source", source);
            o.addProperty("x", pos.getX()); o.addProperty("y", pos.getY()); o.addProperty("z", pos.getZ());
            o.addProperty("note", note);
            o.addProperty("at", Instant.now().toString());
            var d = Minecraft.getInstance().getCurrentServer();
            o.addProperty("server", d == null ? "singleplayer" : d.ip);
            arr.add(o);
            Files.writeString(f, arr.toString());
        } catch (Exception ignored) {}
    }
}
