package dev.noxclient.module.donutsmp;

import dev.noxclient.gui.Toasts;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auction — runs a timed /pay-based auction while enabled. Incoming payments
 * are tracked as bids; the HUD shows the animated leader board and a countdown.
 * When the timer ends the top bidder wins with a celebration banner.
 *
 * Enable → auction starts. Toggle off/on restarts. Duration in settings.
 */
public class Auction extends Module {
    public final Setting.Num duration = add(new Setting.Num("Minutes", 2, 0.25, 60));
    public final Setting.Str item = add(new Setting.Str("Item", "Item"));
    public final Setting.Num minBid = add(new Setting.Num("Min Bid", 0, 0, 1e9));
    public final Setting.Bool topThree = add(new Setting.Bool("Top 3", true));

    /** name → highest bid from that player */
    public final LinkedHashMap<String, Long> bids = new LinkedHashMap<>();
    public long endsAt, startedAt, winnerAt = -1;
    public String winnerName, leader, lastBidder;
    public long winnerBid, leaderAmount, leaderChangedAt;

    // DonutSMP-style incoming pay lines — SYSTEM messages only (ChatMixin
    // feeds addServerSystemMessage). Anchored: optional "[Server]" prefix,
    // optional trailing decoration; find() can't match a random substring.
    private static final Pattern[] PAY = {
        Pattern.compile("(?i)^\\s*(?:\\[[^]]{1,20}]\\s*)?(?:you |)received \\$?([\\d,\\.]+[KkMmBb]?) (?:from|by) (\\w{2,16})\\s*.*$"),
        Pattern.compile("(?i)^\\s*(?:\\[[^]]{1,20}]\\s*)?(\\w{2,16}) (?:sent you|paid you|gave you|tipped you|paid) \\$?([\\d,\\.]+[KkMmBb]?)\\s*.*$"),
        Pattern.compile("(?i)^\\s*(?:\\[[^]]{1,20}]\\s*)?(\\w{2,16}) has paid \\$?([\\d,\\.]+[KkMmBb]?)\\s*.*$"),
    };
    /** name → first-seen tick — earliest bid wins ties (leader consistency). */
    private final java.util.Map<String, Long> firstSeen = new LinkedHashMap<>();
    private static final Pattern NUM = Pattern.compile("([\\d,\\.]+)([KkMmBb]?)");

    public Auction() { super("Auction", "Timed /pay auction with animated HUD", Category.DONUTSMP); }

    @Override protected void onEnable() {
        bids.clear();
        firstSeen.clear();
        leader = winnerName = lastBidder = null;
        winnerBid = leaderAmount = 0;
        startedAt = System.currentTimeMillis();
        endsAt = startedAt + (long) (duration.get() * 60_000);
        winnerAt = -1;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(
            net.minecraft.network.chat.Component.literal(
                "§dAuction started §7— " + item.get() + " · " + duration.get() + "min · bids via /pay"));
    }

    @Override public void onTick() {
        if (winnerAt >= 0) return;
        long now = System.currentTimeMillis();
        if (now < endsAt) return;
        // time up — crown the top bidder
        winnerAt = now;
        if (leader != null) {
            winnerName = leader; winnerBid = leaderAmount;
            Toasts.push("Auction won: " + winnerName + " — $" + fmt(winnerBid), 0xFFFFD870);
            LocalPlayer p = Minecraft.getInstance().player;
            if (p != null) {
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§6★ §eAuction over§6 ★ §f" + winnerName + " §7wins §f" + item.get()
                        + " §7for §a$" + fmt(winnerBid)));
                p.playSound(SoundEvents.PLAYER_LEVELUP, 1f, 1.2f);
            }
        } else {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p != null) p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§7Auction ended with no bids."));
        }
    }

    /** Feed every chat line here (ChatMixin). Returns true when it was a bid. */
    public boolean onChat(String raw) {
        String msg = raw.replaceAll("§.", "").trim();
        for (Pattern p : PAY) {
            Matcher m = p.matcher(msg);
            if (!m.matches()) continue;   // anchored full-line match only
            // group order is explicit per pattern: P1 amount=g1 name=g2;
            // P2/P3 name=g1 amount=g2 — no guessing.
            boolean amountFirst = p == PAY[0];
            Long amt = parse(m.group(amountFirst ? 1 : 2));
            String name = m.group(amountFirst ? 2 : 1);
            if (amt == null || name == null || amt < minBid.get()) continue;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && name.equalsIgnoreCase(mc.player.getGameProfile().name())) continue;
            firstSeen.putIfAbsent(name, System.currentTimeMillis());
            bids.merge(name, amt, Math::max);
            lastBidder = name;
            refreshLeader();
            Toasts.push("Bid: " + name + " — $" + fmt(amt), 0xFF7AFFE0);
            return true;
        }
        return false;
    }

    /** Leader = highest amount; ties go to the EARLIEST bidder (firstSeen). */
    private void refreshLeader() {
        String best = null; long bestAmt = -1, bestAt = Long.MAX_VALUE;
        for (var e : bids.entrySet()) {
            long at = firstSeen.getOrDefault(e.getKey(), Long.MAX_VALUE);
            if (e.getValue() > bestAmt || (e.getValue() == bestAmt && at < bestAt)) {
                best = e.getKey(); bestAmt = e.getValue(); bestAt = at;
            }
        }
        if (best != null && !best.equals(leader)) leaderChangedAt = System.currentTimeMillis();
        if (best != null) { leader = best; leaderAmount = bestAmt; }
    }

    public boolean running() { return isEnabled() && winnerAt < 0; }
    public float progress() {
        if (winnerAt >= 0) return 1;
        long total = endsAt - startedAt;
        return total <= 0 ? 1 : Math.max(0, Math.min(1, 1f - (float) (System.currentTimeMillis() - startedAt) / total));
    }
    public long millisLeft() { return Math.max(0, endsAt - System.currentTimeMillis()); }

    public static String fmt(long v) {
        if (v >= 1e6) return String.format("%.2fM", v / 1e6);
        if (v >= 1e3) return String.format("%.1fK", v / 1e3);
        return String.valueOf(v);
    }
    private static Long parse(String s) {
        Matcher m = NUM.matcher(s.replace(",", ""));
        if (!m.matches()) return null;
        try {
            double v = Double.parseDouble(m.group(1));
            return (long) (v * switch (m.group(2).toLowerCase()) {
                case "k" -> 1e3; case "m" -> 1e6; case "b" -> 1e9; default -> 1;
            });
        } catch (NumberFormatException e) { return null; }
    }
    /** Top-N entries for the HUD (leader first). */
    public java.util.List<Map.Entry<String, Long>> top() {
        return bids.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .limit(3).toList();
    }
}
