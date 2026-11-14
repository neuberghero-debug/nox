package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AutoSell — sells farmed items via DonutSMP's /sell commands on a human-paced
 * interval. Whitelist mode sells ONLY listed items ("/sell hand" per stack);
 * Blacklist mode sells everything EXCEPT listed items via "/sellall".
 * Filter syntax: comma-separated registry ids, e.g. "minecraft:emerald,minecraft:diamond".
 * Set it via config file or the GUI text field; empty whitelist = module idles.
 */
public class AutoSell extends Module {
    private final Setting.Mode mode = add(new Setting.Mode("Filter", "Whitelist", "Whitelist", "Blacklist"));
    private final Setting.Str items = add(new Setting.Str("Items", "minecraft:cobblestone,minecraft:dirt"));
    private final Setting.Num interval = add(new Setting.Num("Interval (s)", 20, 3, 120));
    private final Setting.Bool chatLog = add(new Setting.Bool("Chat log", true));
    // command variants differ by server: UltimateDonutSmp uses "sellhand",
    // live DonutSMP uses "sell hand" — pick what your server answers to
    private final Setting.Str handCmd = add(new Setting.Str("Hand cmd", "sellhand"));
    private final Setting.Str allCmd = add(new Setting.Str("All cmd", "sellall"));

    private long last;
    private final java.util.Set<String> warned = new java.util.HashSet<>();

    public AutoSell() { super("AutoSell", "Auto-sells items via /sell", Category.DONUTSMP); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.getConnection() == null) return;
        long now = System.currentTimeMillis();
        if (now - last < interval.get() * 1000) return;

        Set<String> filter = parse();
        var inv = mc.player.getInventory();

        if (mode.get().equals("Blacklist")) {
            // one command sells all non-filtered stacks
            if (hasAny(inv, filter, false)) {
                send(allCmd.get());
                last = now;
            }
            return;
        }

        // whitelist: sell each matching stack with /sell hand
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            String id = id(st);
            if (!filter.contains(id) && !filter.contains(id.substring(id.indexOf(':') + 1))) continue;
            if (i >= 9) {
                // container-0 clicks get reverted without an open screen and the
                // local prediction is unreliable — never sell a stack that isn't
                // actually held. Hint once per item instead of spamming.
                if (chatLog.on() && warned.add(id))
                    mc.player.sendSystemMessage(Component.literal(
                        "§7[NOX] autoSell: '" + id + "' liegt im Main-Inventar — erst in die Hotbar legen"));
                continue;
            }
            selectSlot(i);
            send(handCmd.get());
            last = now; // reset the interval only on an actual sale
            return; // one stack per interval keeps the pacing human
        }
    }

    private boolean hasAny(net.minecraft.world.entity.player.Inventory inv, Set<String> filter, boolean whitelist) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            boolean in = filter.contains(id(st)) || filter.contains(id(st).substring(id(st).indexOf(':') + 1));
            if (whitelist == in) return true;
        }
        return false;
    }

    private void selectSlot(int invSlot) {
        mc.player.getInventory().setSelectedSlot(invSlot);
        // setSelectedSlot only writes the local field — the vanilla pick packet
        // goes out later in gameMode.tick(), so the sell command would run with
        // the previously held item. Send the carried-item packet first.
        if (mc.getConnection() != null)
            mc.getConnection().send(
                new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(invSlot));
    }

    private void send(String cmd) {
        mc.getConnection().sendCommand(cmd);
        if (chatLog.on() && mc.player != null)
            mc.player.sendSystemMessage(Component.literal("§7[NOX] /" + cmd));
    }

    private Set<String> parse() {
        return Arrays.stream(items.get().split(","))
            .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    private static String id(ItemStack st) {
        return BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
    }
    @Override public String status() {
        if (mc.player == null) return "no player";
        var want = parse();
        if (want.isEmpty()) return "idle — 'Items' list empty (set it in module settings)";
        int n = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++)
            if (!inv.getItem(i).isEmpty() && want.contains(id(inv.getItem(i)))) n++;
        return n == 0 ? "idle — no matching items in hotbar" : n + " sellable stacks";
    }

}
