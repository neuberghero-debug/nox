package dev.noxclient.module.movement;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.item.Items;

/** Controlled elytra glide: auto-starts flight when falling, holds pitch/speed. */
public class ElytraGlide extends Module {
    private final Setting.Num pitch = add(new Setting.Num("Glide pitch", -3, -30, 30));
    private final Setting.Bool autoSwap = add(new Setting.Bool("Auto equip elytra", true));
    private int prevSlot = -1;
    private long lastJump;

    public ElytraGlide() { super("ElytraGlide", "Smooth elytra control + swap", Category.MOVEMENT); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.getConnection() == null) return;
        if (autoSwap.on() && !mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            int slot = findElytra();
            // Held elytra + right-click equips to CHEST server-side; container-0
            // clicks get reverted without an open screen (same fix as AutoArmor).
            if (slot >= 0 && slot < 9) {
                prevSlot = mc.player.getInventory().getSelectedSlot();
                mc.player.getInventory().setSelectedSlot(slot);
                mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
                mc.player.getInventory().setSelectedSlot(prevSlot);
            }
        }
        if (mc.player.isFallFlying()) {
            mc.player.setXRot(pitch.get().floatValue());
        } else if (mc.player.fallDistance > 1.5 && !mc.player.onGround()
            && mc.player.tickCount > 60
            && mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(Items.ELYTRA)
            && System.currentTimeMillis() - lastJump > 500) {
            // needs a worn elytra, the join grace and a 500ms floor — the old
            // code spammed START_FALL_FLYING 20x/s with no elytra at all
            lastJump = System.currentTimeMillis();
            mc.getConnection().send(new ServerboundPlayerCommandPacket(
                mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        }
    }

    private int findElytra() {
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++)
            if (mc.player.getInventory().getItem(i).is(Items.ELYTRA)) return i;
        return -1;
    }
}
