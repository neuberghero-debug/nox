package dev.noxclient.module.combat;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Keeps the best armor equipped per slot — upgrades when better pieces land in
 * inventory (protection value via attribute modifiers), human-paced swaps.
 */
public class AutoArmor extends Module {
    private final Setting.Num cps = add(new Setting.Num("Swap CPS", 3, 1, 8));
    private static final EquipmentSlot[] SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public AutoArmor() { super("AutoArmor", "Equips best armor", Category.COMBAT); }

    @Override
    public void onTick() {
        if (mc.player == null || !Legitifier.ready(this, cps.get(), 0.4)) return;
        for (var slot : SLOTS) {
            var worn = mc.player.getItemBySlot(slot);
            int best = bestFor(slot);
            if (best >= 0 && armorValue(mc.player.getInventory().getItem(best)) > armorValue(worn)) {
                var inv = mc.player.getInventory();
                if (best < 9) {
                    // Hold the armor and right-click: server-side Equippable equips it —
                    // no container-0 clicks needed (those get rejected without a screen).
                    int prev = inv.getSelectedSlot();
                    inv.setSelectedSlot(best);
                    mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
                    inv.setSelectedSlot(prev);
                } else {
                    // Main-inventory armor: container clicks only land while a screen
                    // is open — move it to the hotbar first, then useItem it on.
                    int cid = mc.player.inventoryMenu.containerId;
                    var ci = net.minecraft.world.inventory.ContainerInput.PICKUP;
                    int prev = inv.getSelectedSlot();
                    mc.gameMode.handleContainerInput(cid, best, 0, ci, mc.player);
                    mc.gameMode.handleContainerInput(cid, 36 + prev, 0, ci, mc.player);
                }
                return;
            }
        }
    }

    private int bestFor(EquipmentSlot slot) {
        int best = -1; double bv = armorValue(mc.player.getItemBySlot(slot));
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            var st = mc.player.getInventory().getItem(i);
            var eq = st.get(DataComponents.EQUIPPABLE);
            if (eq == null || eq.slot() != slot) continue;
            double v = armorValue(st);
            if (v > bv) { bv = v; best = i; }
        }
        return best;
    }

    private double armorValue(ItemStack st) {
        var m = st.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (m == null) return 0;
        double v = 0;
        for (var e : m.modifiers())
            v += e.modifier().amount();
        return v;
    }
    @Override public String status() {
        if (mc.player == null) return "no player";
        int empty = 0;
        for (var s : new net.minecraft.world.entity.EquipmentSlot[]{
                net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.FEET })
            if (mc.player.getItemBySlot(s).isEmpty()) empty++;
        return empty == 0 ? "fully armored" : empty + " empty armor slots";
    }

}
