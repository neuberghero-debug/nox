package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/** Learns AH prices while you browse — reports avg/min per item in chat.
 *  The book persists to nox/prices.json so sniping works from a cold start. */
public class AutoStonks extends Module {
    public final Setting.Bool announce = add(new Setting.Bool("Report on close", true));
    /** session-wide price book: item name → [count, sum, min] — read by AHSniper. */
    public static final Map<String, double[]> PRICE_BOOK = new HashMap<>();
    private static final java.io.File BOOK = new java.io.File(
        net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().toFile(),
        "nox/prices.json");
    private static boolean loaded;
    private final Map<String, double[]> stats = PRICE_BOOK;
    private Object lastMenu;

    public AutoStonks() { super("AutoStonks", "Tracks AH price trends", Category.DONUTSMP); }

    private static void load() {
        if (loaded) return; loaded = true;
        try {
            if (!BOOK.isFile()) return;
            var o = com.google.gson.JsonParser.parseReader(
                new java.io.FileReader(BOOK)).getAsJsonObject();
            for (var e : o.entrySet()) {
                var a = e.getValue().getAsJsonArray();
                if (a.size() == 3)
                    PRICE_BOOK.put(e.getKey(), new double[]{
                        a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()});
            }
        } catch (Throwable ignored) {}
    }

    private static void save() {
        try {
            BOOK.getParentFile().mkdirs();
            var o = new com.google.gson.JsonObject();
            for (var e : PRICE_BOOK.entrySet()) {
                var a = new com.google.gson.JsonArray();
                a.add(e.getValue()[0]); a.add(e.getValue()[1]); a.add(e.getValue()[2]);
                o.add(e.getKey(), a);
            }
            try (var w = new java.io.FileWriter(BOOK)) { w.write(o.toString()); }
        } catch (Throwable ignored) {}
    }

    @Override protected void onDisable() { save(); }

    @Override public void onTick() {
        if (mc.player == null) return;
        load();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> sc)) {
            if (lastMenu != null) { if (announce.on()) report(); save(); }
            lastMenu = null; return;
        }
        var menu = sc.getMenu();
        if (menu == lastMenu) return;
        lastMenu = menu;
        for (var slot : menu.slots) {
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            Double p = dev.noxclient.util.LorePrice.of(st);
            if (p == null) continue;
            String name = st.getHoverName().getString();
            double[] s = stats.computeIfAbsent(name, k -> new double[]{0, 0, Double.MAX_VALUE});
            s[0]++; s[1] += p; if (p < s[2]) s[2] = p;
        }
    }

    private void report() {
        if (mc.player == null || stats.isEmpty()) return;
        mc.player.sendSystemMessage(Component.literal("§6[NOX] AH snapshot:"));
        stats.entrySet().stream().sorted((a, b) ->
            Double.compare(b.getValue()[1] / b.getValue()[0], a.getValue()[1] / a.getValue()[0]))
            .limit(5).forEach(e -> {
                double[] s = e.getValue();
                mc.player.sendSystemMessage(Component.literal(
                    " §f" + e.getKey() + " §7avg $" + fmt(s[1] / s[0]) + " min $" + fmt(s[2])));
            });
    }

    /** Session average for an item name, or null if never seen. */
    public static Double avg(String itemName) {
        double[] s = PRICE_BOOK.get(itemName);
        return s != null && s[0] > 0 ? s[1] / s[0] : null;
    }

    /** Lowest price ever seen for an item name, or null. */
    public static Double min(String itemName) {
        double[] s = PRICE_BOOK.get(itemName);
        return s != null && s[0] > 0 ? s[2] : null;
    }

    private static String fmt(double v) {
        return v >= 1_000_000 ? String.format("%.1fM", v / 1e6)
             : v >= 1000 ? String.format("%.1fK", v / 1e3) : String.valueOf((int) v);
    }
}
