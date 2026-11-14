package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;

/** Warns when elytra/armor durability gets low — before it breaks mid-fight. */
public class ArmorAlert extends Module {
    private final Setting.Num percent = add(new Setting.Num("Warn at %", 10, 1, 50));
    private long last;

    public ArmorAlert() { super("ArmorAlert", "Durability warnings", Category.DONUTSMP); }

    @Override
    public void onTick() {
        if (mc.player == null || System.currentTimeMillis() - last < 5000) return;
        for (var slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND}) {
            var st = mc.player.getItemBySlot(slot);
            if (st.isEmpty() || !st.isDamageableItem()) continue;
            int pct = 100 * st.getDamageValue() / st.getMaxDamage();
            if (pct >= 100 - percent.intVal()) {
                last = System.currentTimeMillis();
                mc.player.sendSystemMessage(Component.literal(
                    "§c[NOX] " + st.getItemName().getString() + " at " + (100 - pct) + "%!"));
            }
        }
    }
}
