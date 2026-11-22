package dev.noxclient.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the $ price out of an item's name/lore — shared by AH modules
 *  on both sides of the premium split. */
public final class LorePrice {
    private static final Pattern PRICE =
        Pattern.compile("(?:price|\\$)[:\\s]*([0-9,.]+)", Pattern.CASE_INSENSITIVE);

    public static Double of(ItemStack st) {
        ItemLore lore = st.get(DataComponents.LORE);
        String text = st.getItemName().getString() + " ";
        if (lore != null)
            for (var line : lore.lines()) text += line.getString() + " ";
        Matcher m = PRICE.matcher(text);
        if (!m.find()) return null;
        try { return Double.parseDouble(m.group(1).replace(",", "")); }
        catch (NumberFormatException e) { return null; }
    }

    private LorePrice() {}
}
