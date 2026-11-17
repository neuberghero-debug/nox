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

    private static String detect() {
        String b = brand == null ? "" : brand;
        if (b.contains("grim")) return "Grim";
        if (b.contains("vulcan")) return "Vulcan";
        if (b.contains("verus")) return "Verus";
        if (b.contains("matrix")) return "Matrix";
        if (b.contains("spartan")) return "Spartan";
        if (b.contains("negativity")) return "Negativity";
        if (b.contains("intave")) return "Intave";
        if (b.contains("polar")) return "Polar";
        if (b.contains("karhu")) return "Karhu";
        if (b.contains("aac")) return "AAC";
        if (b.contains("nocheat")) return "NCP";

        var sd = Minecraft.getInstance().getCurrentServer();
        if (sd != null && sd.ip != null) {
            String ip = sd.ip.toLowerCase();
            if (ip.contains("donutsmp")) return "Grim";   // DonutSMP runs GrimAC
        }
        if (!b.isEmpty() && !b.equals("vanilla")) return b.contains("fabric") ? "Unknown" : cap(b);
        return Minecraft.getInstance().getCurrentServer() == null ? "None" : "Vanilla";
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1, Math.min(10, s.length()));
    }
}
