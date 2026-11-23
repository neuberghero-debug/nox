package dev.noxclient.module.misc;

import dev.noxclient.gui.Toasts;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;

/** Toasts when gear or held tool durability drops under the threshold. */
public class DurabilityAlert extends Module {
    private static final EquipmentSlot[] SLOTS = EquipmentSlot.values();

    public final Setting.Num pct = add(new Setting.Num("Under %", 10, 1, 50));
    private final EnumMap<EquipmentSlot, Item> warned = new EnumMap<>(EquipmentSlot.class);
    private LocalPlayer trackedPlayer;

    public DurabilityAlert() { super("DurabilityAlert", "Warns before gear breaks", Category.MISC); }

    @Override public void onTick() {
        LocalPlayer player = mc.player;
        if (player != trackedPlayer) {
            warned.clear();
            trackedPlayer = player;
        }
        if (player == null) return;
        double threshold = pct.get();
        for (EquipmentSlot slot : SLOTS) {
            if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND
                || slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                check(slot, player.getItemBySlot(slot), threshold);
            }
        }
    }

    private void check(EquipmentSlot slot, ItemStack stack, double threshold) {
        if (stack.isEmpty() || !stack.isDamageableItem()) {
            warned.remove(slot);
            return;
        }
        int max = stack.getMaxDamage();
        double remaining = 100.0 * (max - stack.getDamageValue()) / max;
        if (remaining >= threshold) {
            warned.remove(slot);
        } else if (warned.put(slot, stack.getItem()) != stack.getItem()) {
            Toasts.push("⚠ " + stack.getHoverName().getString() + " at " + (int) remaining + "%", 0xFFFFAA00);
        }
    }

    @Override protected void onDisable() {
        warned.clear();
        trackedPlayer = null;
    }
}
