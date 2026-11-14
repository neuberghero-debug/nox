package dev.noxclient.module.combat;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

/**
 * AutoPearl — panics a pearl throw when HP drops under the threshold or when
 * trapped (AntiTrap trigger). Throws toward your look direction; "sky" mode
 * throws straight up as a last-resort escape.
 */
public class AutoPearl extends Module {
    private final Setting.Num hp = add(new Setting.Num("Trigger HP", 8, 1, 19));
    private final Setting.Mode dir = add(new Setting.Mode("Direction", "Look", "Look", "Sky"));
    private final Setting.Num cooldown = add(new Setting.Num("Cooldown (s)", 10, 1, 60));
    private long last;

    public AutoPearl() { super("AutoPearl", "Emergency pearl escape", Category.COMBAT); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        if (mc.player.getHealth() > hp.get()) return;
        if (System.currentTimeMillis() - last < cooldown.get() * 1000) return;
        int slot = pearl();
        if (slot < 0) return;
        if (!Legitifier.ready(this, 8, 0.3)) return;

        int prev = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
        float oldPitch = mc.player.getXRot();
        if (dir.get().equals("Sky")) mc.player.setXRot(-90f);
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        mc.player.setXRot(oldPitch);
        mc.player.getInventory().setSelectedSlot(prev);
        last = System.currentTimeMillis();
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§d[NOX] Pearl!"));
    }

    private int pearl() {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getItem(i).is(Items.ENDER_PEARL)) return i;
        return -1;
    }
}
