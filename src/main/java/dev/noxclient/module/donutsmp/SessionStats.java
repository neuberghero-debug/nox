package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * SessionStats — HUD panel that reads the DonutSMP sidebar (Money/Shards/Kills/Deaths)
 * and shows live values plus per-session deltas.
 */
public class SessionStats extends Module {
    public final Setting.Num posX = add(new Setting.Num("X", 4, 0, 4000));
    public final Setting.Num posY = add(new Setting.Num("Y", 60, 0, 2000));
    public final Setting.Bool deltas = add(new Setting.Bool("Deltas", true));

    /** Latest parsed sidebar values (label → value string, e.g. "Money" → "1K"). */
    public final Map<String, String> lines = new LinkedHashMap<>();
    private final Map<String, Long> baseline = new LinkedHashMap<>();
    private static final Pattern NUM = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)([KkMmBb]?)");

    private int tick;
    private long sessionStart = System.currentTimeMillis();

    public SessionStats() { super("SessionStats", "Money/kills deltas from the sidebar", Category.DONUTSMP); }

    @Override
    protected void onEnable() { baseline.clear(); sessionStart = System.currentTimeMillis(); }

    @Override
    public void onTick() {
        if (++tick % 20 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var obj = mc.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        if (obj == null) { lines.clear(); return; }

        lines.clear();
        // Parse ONLY the member teams of entries visible in the sidebar —
        // iterating all player teams pulled in unrelated prefixes (P1 fix),
        // and a layout swap poisons the baseline: reset it when the label
        // set changes.
        var sb = mc.level.getScoreboard();
        java.util.Set<String> found = new java.util.LinkedHashSet<>();
        for (var e : sb.listPlayerScores(obj)) {
            if (e.isHidden()) continue;
            var t = sb.getPlayersTeam(e.owner());
            if (t == null) continue;
            String line = t.getPlayerPrefix().getString().replaceAll("§.", "").trim();
            if (line.isEmpty()) continue;
            var m = NUM.matcher(line);
            String last = null; int idx = -1;
            while (m.find()) { last = m.group(); idx = m.start(); }
            if (last == null) { lines.put(line, ""); continue; }
            String label = line.substring(0, idx).replaceAll("[^\\p{L} ]", "").trim();
            if (label.isEmpty()) label = line.substring(0, idx).trim();
            // only real stats — skip timers/pings (Keyall countdown, Playtime, NA East ms)
            if (!label.matches("(?i).*(money|shard|kill|death|gem|soul|token).*")) continue;
            lines.put(label, last);
            found.add(label);
        }
        if (!found.equals(lastLabels)) { baseline.clear(); lastLabels = found; }
        for (String l : found) baseline.putIfAbsent(l, toLong(lines.get(l)));
    }

    private java.util.Set<String> lastLabels = java.util.Set.of();

    /** Session delta for a label, e.g. +2400; 0 when unknown. */
    public long delta(String label) {
        Long base = baseline.get(label), now = toLong(lines.get(label));
        return base == null || now == null ? 0 : now - base;
    }

    public String sessionTime() {
        long s = (System.currentTimeMillis() - sessionStart) / 1000;
        return s >= 3600 ? (s / 3600) + "h" + (s / 60 % 60) + "m" : (s / 60) + "m" + (s % 60) + "s";
    }

    /** Seconds since the module was enabled (money/hour extrapolation). */
    public double elapsedSeconds() {
        return (System.currentTimeMillis() - sessionStart) / 1000.0;
    }

    private static Long toLong(String s) {
        if (s == null) return null;
        var m = NUM.matcher(s);
        if (!m.find()) return null;
        double v = Double.parseDouble(m.group(1));
        return (long) (v * switch (m.group(2).toLowerCase()) {
            case "k" -> 1e3; case "m" -> 1e6; case "b" -> 1e9; default -> 1;
        });
    }
}
