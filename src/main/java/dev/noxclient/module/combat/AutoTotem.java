package dev.noxclient.module.combat;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

/** Moves a totem into the offhand on a humanized delay when one is missing. */
public class AutoTotem extends Module {
    private final Setting.Num delayCps = add(new Setting.Num("Action CPS", 6, 1, 15));
    private final Setting.Bool hotbarOnly = add(new Setting.Bool("Hotbar only", false));
    private final Setting.Num belowHp = add(new Setting.Num("Only below HP (0=always)", 0, 0, 20));
    private final Setting.Bool preferEnchantedGaps = add(new Setting.Bool("Keep golden apple offhand", false));

    public AutoTotem() { super("AutoTotem", "Restocks offhand totem", Category.COMBAT); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        if (mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;
        if (preferEnchantedGaps.on()
            && mc.player.getOffhandItem().is(Items.ENCHANTED_GOLDEN_APPLE)) return;
        if (belowHp.get() > 0 && mc.player.getHealth() > belowHp.get()) return;
        if (!Legitifier.ready(this, delayCps.get(), 0.4)) return;

        int slots = hotbarOnly.on() ? 9 : 36;
        for (int i = 0; i < slots; i++) {
            var stack = mc.player.getInventory().getItem(i);
            if (!stack.is(Items.TOTEM_OF_UNDYING)) continue;
            if (i < 9) {
                // hotbar → one clean SWAP packet (same as the vanilla swap-hands key)
                var inv = mc.player.getInventory();
                int prev = inv.getSelectedSlot();
                inv.setSelectedSlot(i);
                mc.player.connection.send(new net.minecraft.network.protocol.game
                    .ServerboundPlayerActionPacket(
                        net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
                            .Action.SWAP_ITEM_WITH_OFFHAND,
                        net.minecraft.core.BlockPos.ZERO, net.minecraft.core.Direction.DOWN));
                inv.setSelectedSlot(prev);
            } else {
                // main inventory → pick up, place into offhand (container slot 45)
                mc.gameMode.handleContainerInput(
                    mc.player.inventoryMenu.containerId, i, 0,
                    net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player);
                mc.gameMode.handleContainerInput(
                    mc.player.inventoryMenu.containerId, 45, 0,
                    net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player);
            }
            break;
        }
    }
    @Override public String status() {
        if (mc.player == null) return "no player";
        boolean totem = mc.player.getOffhandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING);
        return totem ? "totem in offhand" : "offhand empty — grabbing next totem";
    }

}
