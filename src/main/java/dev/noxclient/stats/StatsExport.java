package dev.noxclient.stats;

import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** CSV/JSON export of stats (Modul 26.7). Writes into nox/exports/. */
public final class StatsExport {
    public static Path exportCsv() {
        try {
            Path dir = dir();
            Files.createDirectories(dir);
            Path f = dir.resolve("stats-" + stamp() + ".csv");
            StringBuilder sb = new StringBuilder("metric,value\n");
            for (var e : Stats.sessionView().entrySet())
                sb.append(e.getKey()).append(',').append(e.getValue().replace(",", ".")).append('\n');
            sb.append("kills_lifetime,").append(StatsStore.lifetime("kills")).append('\n');
            sb.append("deaths_lifetime,").append(StatsStore.lifetime("deaths")).append('\n');
            for (var e : Stats.topModules(20).entrySet())
                sb.append("module_min_").append(e.getKey()).append(',')
                  .append(String.format("%.1f", e.getValue())).append('\n');
            Files.writeString(f, sb.toString());
            return f;
        } catch (Throwable t) { return null; }
    }

    public static Path exportJson() {
        try {
            Path dir = dir();
            Files.createDirectories(dir);
            Path f = dir.resolve("stats-" + stamp() + ".json");
            var o = new com.google.gson.JsonObject();
            var s = new com.google.gson.JsonObject();
            for (var e : Stats.sessionView().entrySet()) s.addProperty(e.getKey(), e.getValue());
            o.add("session", s);
            var l = new com.google.gson.JsonObject();
            for (String k : new String[]{"kills", "deaths", "blocks_broken", "blocks_placed", "player_hits"})
                l.addProperty(k, StatsStore.lifetime(k));
            o.add("lifetime", l);
            Files.writeString(f, o.toString());
            return f;
        } catch (Throwable t) { return null; }
    }

    private static Path dir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("nox/exports");
    }
    private static String stamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }
}
