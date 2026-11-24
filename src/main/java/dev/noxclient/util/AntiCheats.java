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
    /** Recently seen inbound plugin-message channel ids — AC plugins often
     *  announce themselves here (vulcan:proxy, grimac-*, etc). */
    private static final java.util.Set<String> channels =
            java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());

    /** Called by BrandMixin when the server brand payload arrives. */
    public static void onBrand(String b) {
        brand = b == null ? "" : b.toLowerCase();
        cached = null;
    }

    /** Feed every inbound custom-payload channel id (BrandMixin). */
    public static void onChannel(String id) {
        if (id == null || id.isBlank()) return;
        channels.add(id.toLowerCase());
        while (channels.size() > 48) channels.remove(channels.iterator().next());
        cached = null;
    }

    /** Clear per-server state — call on disconnect / server switch. */
    public static void reset() {
        brand = null; cached = null; cachedAt = 0;
        channels.clear(); source = "none";
    }

    /** Pace factor for Legitifier: <1 slows automation on strict ACs. */
    public static double paceFactor() {
        return switch (display()) {
            case "Grim", "Polar" -> 0.55;
            case "Vulcan", "Verus", "Intave" -> 0.7;
            case "Matrix", "Spartan", "Themis", "Karhu" -> 0.8;
            case "Ncp", "Watchdog", "Aac" -> 0.85;
            case "None", "Vanilla", "Unknown" -> 1.0;
            default -> 0.9;
        };
    }
    /** True when a known-strict AC is detected. */
    public static boolean strict() {
        return switch (display()) {
            case "Grim", "Polar", "Vulcan", "Verus", "Intave" -> true;
            default -> false;
        };
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

    /** Where the current detection came from — for the AC info panel. */
    private static String source = "none";

    public static String source() { display(); return source; }
    public static String brandOr(String dflt) {
        display(); return brand == null || brand.isEmpty() ? dflt : brand;
    }
    /** What the client does about the detected AC — shown in the info panel. */
    public static String advice() {
        return switch (display()) {
            case "Grim", "Polar" -> "Strict — packet-safe pacing";
            case "None", "Vanilla" -> "Relaxed";
            default -> "Cautious";
        };
    }

    private static String detect() {
        String b = brand == null ? "" : brand;
        String ac = matchAc(b);
        if (ac != null) { source = "server brand"; return ac; }

        // scoreboard teams/objectives — several ACs register named teams
        try {
            var lvl = Minecraft.getInstance().level;
            if (lvl != null) {
                var sb = lvl.getScoreboard();
                for (var t : sb.getPlayerTeams()) {
                    ac = matchAc(t.getName().toLowerCase());
                    if (ac != null) { source = "scoreboard team"; return ac; }
                }
                for (String o : sb.getObjectiveNames()) {
                    ac = matchAc(o.toLowerCase());
                    if (ac != null) { source = "scoreboard objective"; return ac; }
                }
            }
        } catch (Throwable ignored) {}

        // plugin-message channels — ACs leak their name here before any brand
        synchronized (channels) {
            for (String ch : channels) {
                ac = matchAc(ch);
                if (ac != null) { source = "plugin channel"; return ac; }
            }
        }

        var sd = Minecraft.getInstance().getCurrentServer();
        if (sd != null && sd.ip != null) {
            String ip = sd.ip.toLowerCase();
            for (var e : HOST_AC.entrySet())
                if (ip.contains(e.getKey())) { source = "hostname"; return e.getValue(); }
            ac = matchAc(ip);
            if (ac != null) { source = "hostname"; return ac; }
        }
        if (!b.isEmpty() && !b.equals("vanilla")) {
            source = "server brand";
            return b.contains("fabric") || b.contains("paper") || b.contains("purpur")
                || b.contains("spigot") ? "Unknown" : cap(b);
        }
        source = "none";
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
