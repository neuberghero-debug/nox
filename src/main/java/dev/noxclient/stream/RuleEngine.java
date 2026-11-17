package dev.noxclient.stream;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;

/**
 * Gift/like → in-game action rule engine (Modul 1.4/1.5/1.6).
 *
 * Rules persist as one-liners (editable in module settings, one per line):
 *   trigger | action
 *   trigger: gift=Rose | coins>=10 | likes>=100 | type=FOLLOW | comment~regex
 *   action : jump | drop | use | swap:<slot> | look | sound | title:<text>
 *            | command:<whitelisted> | module:<name> | theme | vote:<question>
 *            | discord
 *
 * SAFETY: testMode is DEFAULT ON — rules evaluate + report to toasts but
 * no game action runs until the user flips the setting off. Emergency
 * stop = END key or module toggle. 5 actions/s global rate-limit.
 * Events arrive on the websocket thread — game actions are queued and
 * executed on the client tick via pump().
 */
public final class RuleEngine {
    public record Rule(String trigger, String action) {
        boolean matches(TikTokBridge.Event e) {
            String t = trigger.trim();
            try {
                if (t.startsWith("gift=")) return "GIFT".equals(e.type()) && e.giftName().equalsIgnoreCase(t.substring(5));
                if (t.startsWith("coins>=")) return e.coinValue() >= Long.parseLong(t.substring(7));
                if (t.startsWith("likes>=")) return "LIKE".equals(e.type()) && e.value() >= Long.parseLong(t.substring(7));
                if (t.startsWith("type=")) return e.type().equalsIgnoreCase(t.substring(5));
                if (t.startsWith("comment~")) return "COMMENT".equals(e.type()) &&
                    Pattern.compile(t.substring(8), Pattern.CASE_INSENSITIVE).matcher(str(e)).find();
                if (t.startsWith("user=")) return e.user().equalsIgnoreCase(t.substring(5));
            } catch (Throwable ignored) {}
            return false;
        }
        private static String str(TikTokBridge.Event e) { return e.giftName(); }
    }

    public static volatile boolean testMode = true;      // default ON
    private static final List<Long> actionTimes = new ArrayList<>();
    private static final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<Runnable> QUEUE = new ConcurrentLinkedQueue<>();

    /** Feed an event through all configured rules. */
    public static void fire(TikTokBridge.Event e, List<Rule> rules) {
        for (Rule r : rules) {
            if (!r.matches(e)) continue;
            String cdKey = r.trigger + "|" + r.action;
            Long last = cooldowns.get(cdKey);
            if (last != null && System.currentTimeMillis() - last < 3000) continue;   // 3s/rule cooldown
            cooldowns.put(cdKey, System.currentTimeMillis());
            run(r.action, e);
        }
    }

    /** Drain queued actions — call from the client tick (main thread). */
    public static void pump() {
        for (int i = 0; i < 8; i++) {
            Runnable r = QUEUE.poll();
            if (r == null) return;
            try { r.run(); } catch (Throwable ignored) {}
        }
    }

    private static boolean rateOk() {
        long now = System.currentTimeMillis();
        synchronized (actionTimes) {
            actionTimes.removeIf(t -> now - t > 1000);
            if (actionTimes.size() >= 5) return false;
            actionTimes.add(now);
            return true;
        }
    }

    private static void run(String action, TikTokBridge.Event e) {
        if (testMode) {
            dev.noxclient.gui.Toasts.push("[test] " + e.user() + " → " + action, 0xFF4DA3FF);
            return;
        }
        QUEUE.add(() -> exec(action, e));
    }

    private static void exec(String action, TikTokBridge.Event e) {
        if (!rateOk()) return;
        Minecraft mc = Minecraft.getInstance();
        String a = action.trim();
        try {
            switch (a.split(":")[0]) {
                case "jump" -> { if (mc.player != null && mc.player.onGround()) mc.player.jumpFromGround(); }
                case "drop" -> { if (mc.player != null) mc.player.connection.send(
                    new net.minecraft.network.protocol.game.ServerboundPlayerActionPacket(
                        net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.DROP_ITEM,
                        net.minecraft.core.BlockPos.ZERO, net.minecraft.core.Direction.DOWN)); }
                case "use" -> { if (mc.player != null) mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND); }
                case "swap" -> {
                    int slot = Integer.parseInt(a.substring(5));
                    if (mc.player != null && slot >= 0 && slot <= 8)
                        mc.player.getInventory().setSelectedSlot(slot);
                }
                case "look" -> { if (mc.player != null) mc.player.setYRot(mc.player.getYRot() + 180); }
                case "title" -> dev.noxclient.gui.Toasts.push(
                    a.substring(6).replace("%u", e.user()), 0xFFFF6EC7);
                case "sound" -> { if (mc.player != null) mc.player.playSound(
                    net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.5f); }
                case "command" -> {
                    String cmd = a.substring(8);
                    if (mc.player != null && COMMAND_WHITELIST.matcher(cmd).matches())
                        mc.player.connection.sendChat(cmd);
                }
                case "module" -> {   // dev-stream: viewers toggle client modules
                    var m = dev.noxclient.NoxClient.INSTANCE.modules.get(a.substring(7).trim());
                    if (m != null) m.toggle();
                    else dev.noxclient.gui.Toasts.warn("no module " + a.substring(7).trim());
                }
                case "theme" -> {   // cycle the UI theme
                    dev.noxclient.gui.theme.Theme.cycle();
                    dev.noxclient.gui.Toasts.push("theme → " + dev.noxclient.gui.theme.Theme.currentName(), 0xFF7AFFE0);
                }
                case "vote" -> {    // vote:<question> — 2 options, 60s, winner → toast
                    if (!ViewerVotes.active())
                        ViewerVotes.start(a.substring(5).replace("%u", e.user()), 2, 60,
                            (opt, tally) -> dev.noxclient.gui.Toasts.push(
                                "vote winner: option " + opt, 0xFF7AFFE0));
                }
                case "discord" -> dev.noxclient.gui.Toasts.push(
                    "discord", dev.noxclient.discord.DiscordLink.shortLabel(), 0xFF5865F2);
            }
            dev.noxclient.gui.Toasts.push(e.user() + " → " + action, 0xFFFF6EC7);
        } catch (Throwable ignored) {}
    }

    /** Only harmless chat commands — never /pay, /kill, teleports. */
    private static final Pattern COMMAND_WHITELIST =
        Pattern.compile("(msg|reply|me|team|party|help|list|trigger) .*", Pattern.CASE_INSENSITIVE);

    /** Parse rules from a multi-line settings string. */
    public static List<Rule> parse(String text) {
        List<Rule> out = new ArrayList<>();
        for (String line : text.split("\\n")) {
            line = line.trim();
            int i = line.indexOf('|');
            if (i <= 0) continue;
            out.add(new Rule(line.substring(0, i).trim(), line.substring(i + 1).trim()));
        }
        return out;
    }
}
