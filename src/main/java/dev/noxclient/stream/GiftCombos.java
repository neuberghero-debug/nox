package dev.noxclient.stream;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gift combo chains (Modul 4). 30s silence resets; escalation tiers:
 * 1x = base, 3x = mid, 5x = big, 10x = show. Tracks per-gift chains,
 * session top combo, and a live progress readout for the HUD.
 */
public final class GiftCombos {
    private static final long WINDOW_MS = 30_000;
    private static final Map<String, Integer> counts = new LinkedHashMap<>();
    private static long lastGiftAt;
    private static String lastGift = "";
    private static int topCombo;
    private static String topGift = "";

    public static int tierOf(String gift) {
        int n = counts.getOrDefault(gift, 0);
        return n >= 10 ? 4 : n >= 5 ? 3 : n >= 3 ? 2 : n >= 1 ? 1 : 0;
    }

    /** Returns current tier after registering the gift (1..4). */
    public static int onGift(TikTokBridge.Event e) {
        long now = System.currentTimeMillis();
        if (now - lastGiftAt > WINDOW_MS) counts.clear();
        lastGiftAt = now;
        lastGift = e.giftName();
        int n = counts.merge(e.giftName(), 1, Integer::sum);
        if (n > topCombo) { topCombo = n; topGift = e.giftName(); }
        return tierOf(e.giftName());
    }

    /** HUD readout: current combo + progress to next tier (0..1). */
    public static String live() {
        if (lastGift.isEmpty() || System.currentTimeMillis() - lastGiftAt > WINDOW_MS) return "";
        int n = counts.getOrDefault(lastGift, 0);
        return lastGift + " x" + n + "  T" + tierOf(lastGift);
    }

    public static float progress() {
        int n = counts.getOrDefault(lastGift, 0);
        int next = n >= 10 ? 10 : n >= 5 ? 10 : n >= 3 ? 5 : 3;
        int prev = n >= 10 ? 5 : n >= 5 ? 3 : n >= 3 ? 1 : 0;
        return Math.min(1f, (n - prev) / (float) (next - prev));
    }

    public static String topCombo() { return topCombo > 0 ? topGift + " x" + topCombo : "—"; }
}
