package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.gui.Toasts;
import dev.noxclient.util.ChatBus;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BountyTracker — listens for DonutSMP bounty announcements in system chat
 * and keeps the last 10. A bounty placed on YOU alarms + toasts — that's the
 * moment to get off whatever you were doing. `/nox bounties` prints the list.
 */
public class BountyTracker extends Module {
    private static final Pattern AMOUNT = Pattern.compile("\\$?([\\d,.]+)");
    private static final Pattern ON_NAME = Pattern.compile("\\bon\\s+(\\w{3,16})\\b",
        Pattern.CASE_INSENSITIVE);
    private final Setting.Bool selfAlarm = add(new Setting.Bool("Alarm on self", true));
    private final Setting.Bool toasts = add(new Setting.Bool("Toast bounties", true));

    private static final Deque<String> RECENT = new ArrayDeque<>();

    public BountyTracker() { super("BountyTracker", "Parses bounty announcements + self-alarm",
        Category.DONUTSMP); }

    @Override protected void onEnable() {
        ChatBus.register("bounty", this::onChat);
        dev.noxclient.api.AddonContext.COMMANDS.put("bounties", a -> {
            if (mc.player == null) return;
            if (RECENT.isEmpty()) { mc.player.sendSystemMessage(Component.literal("§7[NOX] no bounties seen this session")); return; }
            for (String s : RECENT) mc.player.sendSystemMessage(Component.literal("§7  " + s));
        });
    }

    @Override protected void onDisable() {
        ChatBus.unregister("bounty");
        dev.noxclient.api.AddonContext.COMMANDS.remove("bounties");
    }

    private void onChat(String raw) {
        if (!raw.toLowerCase().contains("bounty")) return;
        String line = raw.length() > 90 ? raw.substring(0, 90) + "…" : raw;
        synchronized (RECENT) {
            RECENT.addFirst(line);
            while (RECENT.size() > 10) RECENT.pollLast();
        }
        boolean self = false;
        if (mc.player != null) {
            String me = mc.player.getGameProfile().name();
            self = raw.contains(me);
        }
        Matcher on = ON_NAME.matcher(raw);
        String target = on.find() ? on.group(1) : null;
        Matcher amt = AMOUNT.matcher(raw.replace(",", ""));
        String price = amt.find() ? amt.group(1) : null;
        if (toasts.on()) {
            if (self && selfAlarm.on())
                Toasts.push("BOUNTY ON YOU — " + line, 0xFFFF6666);
            else if (target != null)
                Toasts.push("bounty " + (price != null ? "$" + price + " " : "")
                    + "on " + target, 0xFFFFD870);
        }
        mark(self ? "bounty on SELF" : "bounty seen");
    }

    @Override public String status() {
        return isEnabled() ? "tracking " + RECENT.size() + " recent bounties" : "off";
    }
}
