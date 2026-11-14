package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.gui.Toasts;

/** Toasts when gear or held tool durability drops under the threshold. */
public class DurabilityAlert extends Module {
    public final Setting.Num pct = add(new Setting.Num("Under %", 10, 1, 50));
    private final java.util.Set<String> warned = new java.util.HashSet<>();

    public DurabilityAlert() { super("DurabilityAlert", "Warns before gear breaks", Category.MISC); }

    @Override public void onTick() {
        if (mc.player == null) return;
        var inv = mc.player.getInventory();
        check(mc.player.getMainHandItem());
        check(mc.player.getOffhandItem());
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            if (slot.getType() != net.minecraft.world.entity.EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            check(mc.player.getItemBySlot(slot));
        }
    }

    private void check(net.minecraft.world.item.ItemStack st) {
        if (st.isEmpty() || !st.isDamageableItem()) return;
        int left = st.getMaxDamage() - st.getDamageValue();
        double frac = (double) left / st.getMaxDamage();
        String k = st.getItem().toString();
        if (frac * 100 < pct.get()) {
            if (warned.add(k)) {
                Toasts.push("⚠ " + st.getHoverName().getString() + " at " + (int) (frac * 100) + "%", 0xFFFFAA00);
            }
        } else warned.remove(k);
    }
}
