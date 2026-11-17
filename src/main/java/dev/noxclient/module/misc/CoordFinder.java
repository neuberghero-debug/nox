package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Coordinate extractor (Modul 5, ohne OCR). Parses coords out of the
 * clipboard or chat — "X: -123 Y: 64 Z: 456", "(-123, 64, 456)",
 * "-123 64 456" — and drops a waypoint toast + copies a clean line back.
 * Real OCR of screenshots needs Tesseract → [OFFEN], not bundled.
 */
public class CoordFinder extends Module {
    public final Setting.Bool watchChat = add(new Setting.Bool("Watch chat", true));
    public final Setting.Bool waypoints = add(new Setting.Bool("Waypoints", true));

    private static final Pattern[] PATTERNS = {
        Pattern.compile("X[:\\s]*(-?\\d{1,7})[,\\s]+Y[:\\s]*(-?\\d{1,4})[,\\s]+Z[:\\s]*(-?\\d{1,7})", Pattern.CASE_INSENSITIVE),
        // F3 debug overlay: "XYZ: -123.456 / 64.0 / 456.2" + "Block: -123 64 456"
        Pattern.compile("(?i)XYZ:\\s*(-?\\d{1,7})[\\.\\d]*\\s*/\\s*(-?\\d{1,4})[\\.\\d]*\\s*/\\s*(-?\\d{1,7})[\\.\\d]*"),
        Pattern.compile("(?i)Block:\\s*(-?\\d{1,7})\\s+(-?\\d{1,4})\\s+(-?\\d{1,7})"),
        Pattern.compile("\\((-?\\d{1,7})[,\\s]+(-?\\d{1,4})[,\\s]+(-?\\d{1,7})\\)"),
        Pattern.compile("\\b(-?\\d{2,6})\\s+(-?\\d{1,3})\\s+(-?\\d{2,6})\\b"),
    };
    private static long lastFound;

    public CoordFinder() { super("CoordFinder", "Extracts coords from chat/clipboard → waypoint", Category.MISC); }

    /** Feed any chat line; true when coords were found + announced. */
    public static boolean scan(String text) {
        for (Pattern p : PATTERNS) {
            Matcher m = p.matcher(text);
            if (!m.find()) continue;
            try {
                int x = Integer.parseInt(m.group(1));
                int y = Integer.parseInt(m.group(2));
                int z = Integer.parseInt(m.group(3));
                if (Math.abs(y) > 400 || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000) continue;
                if (System.currentTimeMillis() - lastFound < 2000) return true;
                lastFound = System.currentTimeMillis();
                announce(x, y, z);
                dev.noxclient.util.CoordBook.add(x, y, z, source(text));
                return true;
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }

    private static void announce(int x, int y, int z) {
        dev.noxclient.gui.Toasts.push("Coords: " + x + " " + y + " " + z, 0xFF66D9A0);
        var mc = net.minecraft.client.Minecraft.getInstance();
        try { mc.keyboardHandler.setClipboard(x + " " + y + " " + z); } catch (Throwable ignored) {}
    }

    /** Per-frame waypoint markers (CoordBook) while "Waypoints" is on. */
    @Override
    public void onTick() {
        if (waypoints.on())
            dev.noxclient.render.NoxRender.once(dev.noxclient.util.CoordBook::emit);
    }

    /** Label for the coord book: where the coords came from. */
    private static String source(String text) {
        if (text.matches("(?i).*block:.*")) return "F3 block";
        if (text.matches("(?i).*xyz:.*")) return "F3 xyz";
        return "chat";
    }

    /** Clipboard scan (hotkey path or /nox coords). */
    public static boolean scanClipboard() {
        try {
            String clip = net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard();
            return clip != null && scan(clip);
        } catch (Throwable t) { return false; }
    }
}
