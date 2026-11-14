package dev.noxclient.module.movement;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

/** Swaps chestplate/elytra by context: airborne wants elytra, grounded wants armor. */
public class ElytraSwap extends Module {
    public final Setting.Num fallTicks = add(new Setting.Num("Fall ticks", 6, 2, 20));
    private int falling;

    public ElytraSwap() { super("ElytraSwap", "Auto elytra/chestplate by context", Category.MOVEMENT); }

    @Override public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        if (mc.player.onGround()) falling = 0;
        else if (mc.player.getDeltaMovement().y < -0.1) falling++;
        // keep wanting the elytra while actually gliding — the old "!isFallFlying"
        // swapped back to a chestplate mid-glide and dropped you
        boolean wantElytra = mc.player.isFallFlying() || falling > fallTicks.intVal();
        var chest = mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
        boolean wearingElytra = chest.is(Items.ELYTRA);
        if (wantElytra == wearingElytra) return;
        if (!Legitifier.ready(this, 2, 0.3)) return;
        var inv = mc.player.getInventory();
        var want = wantElytra ? Items.ELYTRA : Items.NETHERITE_CHESTPLATE;
        for (int i = 0; i < 9; i++) {
            var st = inv.getItem(i);
            boolean match = wantElytra ? st.is(Items.ELYTRA)
                : st.toString().contains("chestplate") && !st.is(Items.ELYTRA);
            if (!match) continue;
            inv.setSelectedSlot(i);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            return;
        }
    }
}
