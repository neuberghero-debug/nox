package dev.noxclient.stream;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stream chat ring buffer (Modul 1.7) — the last N TikTok events,
 * rendered in-game by NoxOverlay. Entries are pre-formatted display
 * lines tagged with a kind for coloring:
 *   CHAT / GIFT / LIKE / META / SUB / STATUS
 * Also keeps stream stats for the panel footer (event count, top gifter).
 */
public final class StreamChat {
    public record Entry(String kind, String text, long ts) {}

    private static final int CAP = 40;
    private static final Deque<Entry> entries = new ArrayDeque<>();
    private static final Map<String, Long> coinsByUser = new HashMap<>();
    private static long events;
    private static String topGifter = "";
    private static long topCoins;

    public static synchronized void add(TikTokBridge.Event e) {
        events++;
        String kind = switch (e.type()) {
            case "COMMENT" -> "CHAT";
            case "GIFT" -> "GIFT";
            case "LIKE" -> "LIKE";
            case "SUBSCRIBE" -> "SUB";
            case "STATUS" -> "STATUS";
            default -> "META";
        };
        String text = switch (e.type()) {
            case "COMMENT" -> e.user() + ": " + e.giftName();
            case "GIFT" -> {
                if (e.coinValue() > 0) {
                    long total = coinsByUser.merge(e.user(), e.coinValue(), Long::sum);
                    if (total > topCoins) { topCoins = total; topGifter = e.user(); }
                }
                yield e.user() + " gifted " + e.giftName()
                    + (e.value() > 1 ? " x" + e.value() : "")
                    + (e.coinValue() > 0 ? "  ·" + e.coinValue() + "c" : "");
            }
            case "LIKE" -> e.user() + " liked x" + e.value();
            case "FOLLOW" -> e.user() + " followed";
            case "SHARE" -> e.user() + " shared";
            case "SUBSCRIBE" -> e.user() + " subscribed!";
            case "STATUS" -> "● " + e.giftName();
            default -> e.user() + " " + e.type().toLowerCase();
        };
        if (entries.size() >= CAP) entries.pollFirst();
        entries.addLast(new Entry(kind, text, System.currentTimeMillis()));
    }

    /** Last n entries, oldest → newest. */
    public static synchronized List<Entry> last(int n) {
        var all = new ArrayList<>(entries);
        return all.subList(Math.max(0, all.size() - n), all.size());
    }

    public static synchronized long events() { return events; }
    public static synchronized String topGifter() {
        return topGifter.isEmpty() ? "—" : topGifter + " (" + topCoins + "c)";
    }
    public static synchronized void clear() {
        entries.clear(); coinsByUser.clear(); topGifter = ""; topCoins = 0; events = 0;
    }
}
