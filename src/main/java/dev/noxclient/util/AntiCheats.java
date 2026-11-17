package dev.noxclient.util;

import net.minecraft.client.Minecraft;

/**
 * Server-side anti-cheat detection for the titlebar "AC: x" badge.
 *
 * Sources, in order of confidence:
 *  1. the server's `minecraft:brand` payload (captured by BrandMixin) —
 *     Grim/Vulcan/Verus/Matrix etc. often leak in the brand string or via
 *     plugin channels
 *  2. hostname heuristics — DonutSMP is known to run GrimAC
 *
 * display() is cheap — call it per frame.
 */
public final class AntiCheats {
    private AntiCheats() {}

    private static String brand;
    private static volatile String cached;
    private static long cachedAt;

    /** Called by BrandMixin when the server brand payload arrives. */
    public static void onBrand(String b) {
        brand = b == null ? "" : b.toLowerCase();
        cached = null;
    }

    /** Display string for the badge, e.g. "Grim" / "Vulcan" / "Vanilla". */
    public static String display() {
        String c = cached;
        if (c != null && System.currentTimeMillis() - cachedAt < 5000) return c;
        c = detect();
        cached = c; cachedAt = System.currentTimeMillis();
        return c;
    }

    private static final String[] ACS = {
        "grim", "vulcan", "verus", "matrix", "spartan", "negativity",
        "intave", "polar", "karhu", "aac", "nocheat", "ncp", "themis",
        "hawk", "horizon", "kauri", "reflex", "wither", "zentra"
    };
    private static final java.util.Map<String, String> HOST_AC = java.util.Map.of(
        "donutsmp", "Grim", "crazymc", "Polar", "minemen", "Minemen");

    private static String detect() {
        String b = brand == null ? "" : brand;
        String ac = matchAc(b);
        if (ac != null) return ac;

        // scoreboard teams/objectives — several ACs register named teams
        try {
            var lvl = Minecraft.getInstance().level;
            if (lvl != null) {
                var sb = lvl.getScoreboard();
                for (var t : sb.getPlayerTeams()) {
                    ac = matchAc(t.getName().toLowerCase());
                    if (ac != null) return ac;
                }
                for (String o : sb.getObjectiveNames()) {
                    ac = matchAc(o.toLowerCase());
                    if (ac != null) return ac;
                }
            }
        } catch (Throwable ignored) {}

        var sd = Minecraft.getInstance().getCurrentServer();
        if (sd != null && sd.ip != null) {
            String ip = sd.ip.toLowerCase();
            for (var e : HOST_AC.entrySet())
                if (ip.contains(e.getKey())) return e.getValue();
            ac = matchAc(ip);
            if (ac != null) return ac;
        }
        if (!b.isEmpty() && !b.equals("vanilla"))
            return b.contains("fabric") || b.contains("paper") || b.contains("purpur")
                || b.contains("spigot") ? "Unknown" : cap(b);
        return Minecraft.getInstance().getCurrentServer() == null ? "None" : "Vanilla";
    }

    /** First AC keyword inside the haystack, pretty-printed — null if none. */
    private static String matchAc(String hay) {
        if (hay == null || hay.isEmpty()) return null;
        for (String k : ACS) if (hay.contains(k)) return cap(k);
        return null;
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1, Math.min(10, s.length()));
    }
}
