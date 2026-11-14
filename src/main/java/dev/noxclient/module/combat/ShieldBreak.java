package dev.noxclient.module.combat;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * ShieldBreak — when the enemy in crosshair is blocking with a shield,
 * swaps to an axe, lands the shield-disabling hit, swaps back.
 */
public class ShieldBreak extends Module {
    private final Setting.Bool swapBack = add(new Setting.Bool("Swap back", true));
    private int prev = -1;

    public ShieldBreak() { super("ShieldBreak", "Axe-swaps blocking enemies", Category.COMBAT); }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        var e = mc.crosshairPickEntity;
        boolean blocking = e instanceof LivingEntity le && le.isBlocking();
        if (!blocking) {
            if (prev >= 0 && swapBack.on()) {
                mc.player.getInventory().setSelectedSlot(prev); prev = -1;
            }
            return;
        }
        int axe = axeSlot();
        if (axe >= 0 && axe != mc.player.getInventory().getSelectedSlot()
            && Legitifier.ready(this, 10, 0.3)) {
            prev = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(axe);
        }
    }

    private int axeSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack st = mc.player.getInventory().getItem(i);
            if (st.getItem().toString().endsWith("_axe")) return i;
        }
        return -1;
    }
}
