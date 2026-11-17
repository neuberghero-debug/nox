package dev.noxclient.stream;

import java.util.HashMap;
import java.util.Map;

/**
 * Viewer votes (Modul 3). Options 1..5, dedupe per user, donator
 * weighting (tier2+ ×3, tier3+ ×10), live tally, winner callback at end.
 * TikTok path: comment "1".."5". Twitch path: "!vote n" via TwitchService.
 */
public final class ViewerVotes {
    public interface WinnerCb { void onWinner(int option, Map<Integer, Integer> tally); }

    private static String question = "";
    private static int options;
    private static long endsAt;
    private static boolean active;
    private static final Map<String, Integer> votes = new HashMap<>();
    private static final Map<String, Long> lastVote = new HashMap<>();
    private static WinnerCb cb;

    public static void start(String q, int optionCount, int seconds, WinnerCb callback) {
        question = q; options = Math.min(5, Math.max(2, optionCount));
        endsAt = System.currentTimeMillis() + seconds * 1000L;
        votes.clear(); lastVote.clear(); active = true; cb = callback;
    }

    public static void stop() { active = false; }
    public static boolean active() { return active; }
    public static String question() { return question; }

    /** Feed a chat/comment vote. donatorTier: 0 none, 2→×3, 3+→×10. */
    public static void vote(String user, int option, int donatorTier) {
        if (!active || option < 1 || option > options) return;
        long now = System.currentTimeMillis();
        Long last = lastVote.get(user);
        if (last != null && now - last < 5000) return;    // 5s per-user cooldown
        lastVote.put(user, now);
        int w = donatorTier >= 3 ? 10 : donatorTier >= 2 ? 3 : 1;
        votes.put(user, option * 100 + w);                // pack option+weight
    }

    /** Tick — finish at deadline. */
    public static void tick() {
        if (active && System.currentTimeMillis() >= endsAt) {
            active = false;
            var tally = tally();
            int win = tally.entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(0);
            if (cb != null) cb.onWinner(win, tally);
        }
    }

    /** option → weighted total. */
    public static Map<Integer, Integer> tally() {
        Map<Integer, Integer> t = new HashMap<>();
        for (int packed : votes.values()) {
            int opt = packed / 100, w = packed % 100;
            t.merge(opt, w, Integer::sum);
        }
        return t;
    }

    /** Parse "1".."5" or "!vote n". Returns option or 0. */
    public static int parseVote(String text) {
        text = text.trim();
        if (text.startsWith("!vote")) text = text.substring(5).trim();
        try { int v = Integer.parseInt(text); return v >= 1 && v <= 5 ? v : 0; }
        catch (Throwable t) { return 0; }
    }

    public static long msLeft() { return Math.max(0, endsAt - System.currentTimeMillis()); }
}
