package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auto Totem Order [PREMIUM] — buys underpriced totems off /ah and resells via
 * /sell or relists at margin. Tracks profit this session.
 */
public class AutoTotemOrder extends Module {
    private static final Pattern PRICE = Pattern.compile("(?:price|\\$)[:\\s]*([0-9,.]+)", Pattern.CASE_INSENSITIVE);
    private final Setting.Num buyUnder = add(new Setting.Num("Buy under", 8000, 500, 500_000));
    private final Setting.Bool autoResell = add(new Setting.Bool("Auto /sell", false));
    private double spent, earned;
    private long last;

    public AutoTotemOrder() { super("AutoTotemOrder", "Flips totems for profit", Category.DONUTSMP); premium = true; }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        if (System.currentTimeMillis() - last < 4000) return;
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == mc.player.inventoryMenu) return;

        for (Slot slot : menu.slots) {
            ItemStack st = slot.getItem();
            if (!st.is(Items.TOTEM_OF_UNDYING)) continue;
            Double price = price(st);
            if (price != null && price <= buyUnder.get()) {
                mc.gameMode.handleContainerInput(menu.containerId, slot.index, 0,
                    net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, mc.player);
                spent += price;
                last = System.currentTimeMillis();
                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§a[NOX] Bought totem $" + price + " §8(session -$" + (int) spent + " / +$" + (int) earned + ")"));
                if (autoResell.on()) mc.getConnection().sendCommand("sellhand");
                return;
            }
        }
    }

    private Double price(ItemStack st) {
        ItemLore lore = st.get(DataComponents.LORE);
        if (lore == null) return null;
        String text = "";
        for (var line : lore.lines()) text += line.getString() + " ";
        Matcher m = PRICE.matcher(text);
        if (!m.find()) return null;
        try { return Double.parseDouble(m.group(1).replace(",", "")); }
        catch (NumberFormatException e) { return null; }
    }
}
