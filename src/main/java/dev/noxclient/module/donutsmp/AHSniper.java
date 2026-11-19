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
    private final Setting.Bool cycle = add(new Setting.Bool("Cycle pages", true,
        "Clicks the next-page arrow when nothing bought for a while"));
    private final Setting.Num pageSec = add(new Setting.Num("Page every (s)", 9, 3, 60));
    private long last;

    /** Items we just bought — AutoFlip picks these up to relist them. */
    public static final java.util.ArrayDeque<String[]> FLIP = new java.util.ArrayDeque<>();

    public AHSniper() { super("AHSniper", "Snipes underpriced AH items", Category.DONUTSMP); premium = true; }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == mc.player.inventoryMenu) return; // no shop UI open
        if (System.currentTimeMillis() - last < cooldown.get() * 1000 * (0.8 + Math.random() * 0.7)) return;

        for (Slot slot : menu.slots) {
            // skip our own 36 inventory slots — their lore prices made the bot
            // "buy" items out of our own inventory onto the cursor
            if (slot.container == mc.player.getInventory()) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            if (!itemFilter.get().isEmpty()
                && !st.getItem().toString().toLowerCase().contains(itemFilter.get().toLowerCase())) continue;
            Double price = lorePrice(st);
            if (price != null && price <= maxPrice.get() && profitOk(st, price)) {
                mc.gameMode.handleContainerInput(menu.containerId, slot.index, 0,
                    net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player);
                last = System.currentTimeMillis();
                if (FLIP.size() < 8)
                    FLIP.add(new String[]{st.getHoverName().getString(), String.valueOf(price)});
                Double ref = AutoStonks.min(st.getHoverName().getString());
                String deal = ref != null
                    ? String.format(" — book min $%.0f (%.0f%% under)", ref, (1 - price / ref) * 100)
                    : "";
                dev.noxclient.discord.Webhook.post(
                    System.getenv().getOrDefault("NOX_SNIPER_WEBHOOK", ""),
                    "AHSniper", "bought " + st.getItemName().getString() + " for $" + price + deal);
                return;
            }
        }
        // nothing bought — keep listings fresh by paging through the shop
        if (cycle.on()
            && dev.noxclient.util.Legitifier.ready(this, 1.0 / pageSec.get(), 0.5)) {
            for (Slot slot : menu.slots) {
                if (slot.container == mc.player.getInventory()) continue;
                ItemStack st = slot.getItem();
                if (st.isEmpty() || !nextPage(st)) continue;
                mc.gameMode.handleContainerInput(menu.containerId, slot.index, 0,
                    net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player);
                return;
            }
        }
    }

    /** Arrow/head whose name reads as a 'next page' control. */
    private static boolean nextPage(ItemStack st) {
        boolean arrowish = st.is(net.minecraft.world.item.Items.ARROW)
            || st.is(net.minecraft.world.item.Items.SPECTRAL_ARROW)
            || st.is(net.minecraft.world.item.Items.PLAYER_HEAD);
        if (!arrowish) return false;
        String n = st.getHoverName().getString().toLowerCase();
        return n.contains("next") || n.contains("page") || n.contains("→") || n.contains(">");
    }

    /** Margin gate: needs a known book entry — an unknown price with a margin
     *  set is a blind buy (renamed junk snipes), so it skips instead. */
    private boolean profitOk(ItemStack st, double price) {
        double margin = minProfit.get();
        if (margin <= 0) return true;
        Double ref = AutoStonks.min(st.getHoverName().getString());
        return ref != null && price <= ref * (1 - margin / 100);
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
