package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AH Sniper [PREMIUM] — watches the open Auction House container, parses price
 * from lore ("$1,234" / "Price: 1234"), and buys items under the threshold with
 * a container click. Pings the Discord webhook on every snipe.
 */
public class AHSniper extends Module {
    private static final Pattern PRICE = Pattern.compile("(?:price|\\$)[:\\s]*([0-9,.]+)", Pattern.CASE_INSENSITIVE);
    private final Setting.Num maxPrice = add(new Setting.Num("Max price", 5000, 100, 10_000_000));
    private final Setting.Num minProfit = add(new Setting.Num("Min profit %", 0, 0, 90));
    private final Setting.Str itemFilter = add(new Setting.Str("Item id contains", ""));
    private final Setting.Num cooldown = add(new Setting.Num("Cooldown (s)", 5, 1, 60));
    private long last;

    public AHSniper() { super("AHSniper", "Snipes underpriced AH items", Category.DONUTSMP); premium = true; }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == mc.player.inventoryMenu) return; // no shop UI open
        if (System.currentTimeMillis() - last < cooldown.get() * 1000) return;

        for (Slot slot : menu.slots) {
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            if (!itemFilter.get().isEmpty()
                && !st.getItem().toString().toLowerCase().contains(itemFilter.get().toLowerCase())) continue;
            Double price = lorePrice(st);
            if (price != null && price <= maxPrice.get() && profitOk(st, price)) {
                mc.gameMode.handleContainerInput(menu.containerId, slot.index, 0,
                    net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player);
                last = System.currentTimeMillis();
                dev.noxclient.discord.Webhook.post(
                    System.getenv().getOrDefault("NOX_SNIPER_WEBHOOK", ""),
                    "AHSniper", "bought " + st.getItemName().getString() + " for $" + price);
                return;
            }
        }
    }

    /** Margin gate: known session-average → price must beat it by Min profit %. */
    private boolean profitOk(ItemStack st, double price) {
        double margin = minProfit.get();
        if (margin <= 0) return true;
        Double avg = AutoStonks.avg(st.getHoverName().getString());
        return avg == null || price <= avg * (1 - margin / 100);
    }

    /** Parses the $ price out of an item's name/lore — shared with AutoStonks. */
    public static Double lorePrice(ItemStack st) {
        ItemLore lore = st.get(DataComponents.LORE);
        String text = st.getItemName().getString() + " ";
        if (lore != null)
            for (var line : lore.lines()) text += line.getString() + " ";
        Matcher m = PRICE.matcher(text);
        if (!m.find()) return null;
        try { return Double.parseDouble(m.group(1).replace(",", "")); }
        catch (NumberFormatException e) { return null; }
    }
    @Override public String status() {
        return mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu
            ? "scanning open shop for " + (itemFilter.get().isEmpty() ? "anything" : itemFilter.get())
            : "idle — open an AH/shop UI";
    }

}
