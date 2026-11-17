package dev.noxclient.replay;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Highlight markers (Modul 2, E.1) — timestamped markers for clip-worthy
 * moments (kills, deaths, combo peaks, module triggers). Persisted as JSON
 * next to the replay dir so tools/highlight_reel.py can cut clips offline.
 *
 * mark("kill", "on Steve") → {ts, kind, note} → flushed on save().
 */
public final class HighlightMarks {
    public record Mark(long ts, String kind, String note) {}
    private static final List<Mark> marks = new ArrayList<>();
    private static long sessionStart = System.currentTimeMillis();

    public static void start() { sessionStart = System.currentTimeMillis(); marks.clear(); }

    /** ms offset within the current recording session (matches DNRP frames). */
    public static void mark(String kind, String note) {
        long rel = System.currentTimeMillis() - sessionStart;
        marks.add(new Mark(rel, kind, note == null ? "" : note));
        ReplayRecorder.event("highlight", kind + ":" + note);   // lands on replay timeline too
    }

    public static List<Mark> marks() { return marks; }

    /** Write markers JSON beside the replay file (same basename). */
    public static Path save(Path replayFile) {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("client", "NOX " + dev.noxclient.update.UpdateManager.currentVersion());
            o.addProperty("started", sessionStart);
            JsonArray arr = new JsonArray();
            for (Mark m : marks) {
                JsonObject j = new JsonObject();
                j.addProperty("ts", m.ts());
                j.addProperty("kind", m.kind());
                j.addProperty("note", m.note());
                arr.add(j);
            }
            o.add("marks", arr);
            Path out = replayFile != null
                ? replayFile.resolveSibling(replayFile.getFileName() + ".marks.json")
                : Minecraft.getInstance().gameDirectory.toPath().resolve("nox/replays/marks.json");
            Files.writeString(out, new Gson().toJson(o));
            return out;
        } catch (Exception e) { return null; }
    }
}
