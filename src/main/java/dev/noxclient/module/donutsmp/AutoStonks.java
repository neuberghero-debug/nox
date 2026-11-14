package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/** Learns AH prices while you browse — reports avg/min per item in chat. */
public class AutoStonks extends Module {
    public final Setting.Bool announce = add(new Setting.Bool("Report on close", true));
    /** session-wide price book: item name → [count, sum, min] — read by AHSniper. */
    public static final Map<String, double[]> PRICE_BOOK = new HashMap<>();
    private final Map<String, double[]> stats = PRICE_BOOK;
    private Object lastMenu;

    public AutoStonks() { super("AutoStonks", "Tracks AH price trends", Category.DONUTSMP); }

    @Override public void onTick() {
        if (mc.player == null) return;
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> sc)) {
            if (lastMenu != null && announce.on()) report();
            lastMenu = null; return;
        }
        var menu = sc.getMenu();
        if (menu == lastMenu) return;
        lastMenu = menu;
        for (var slot : menu.slots) {
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            Double p = AHSniper.lorePrice(st);
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

    private static String fmt(double v) {
        return v >= 1_000_000 ? String.format("%.1fM", v / 1e6)
             : v >= 1000 ? String.format("%.1fK", v / 1e3) : String.valueOf((int) v);
    }
}
