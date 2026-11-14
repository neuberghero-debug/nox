package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;

/**
 * AutoVoiceConfirm — accepts the DonutSMP voice-chat consent chest GUI on join
 * (jukebox center, lime = accept, red = decline).
 */
public class AutoVoiceConfirm extends Module {
    public final Setting.Bool accept = add(new Setting.Bool("Accept", true));

    private int cooldown;

    public AutoVoiceConfirm() { super("AutoVoiceConfirm", "Auto-accepts the voice chat prompt", Category.DONUTSMP); }

    @Override
    public void onTick() {
        if (--cooldown > 0) return;
        cooldown = 5;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?>)) return;
        if (mc.player == null || mc.gameMode == null) return;
        var menu = mc.player.containerMenu;
        if (menu.slots.size() < 16) return;
        if (menu.getSlot(13).getItem().getItem() != Items.JUKEBOX) return;
        String lime = net.minecraft.core.registries.BuiltInRegistries.ITEM
            .getKey(menu.getSlot(11).getItem().getItem()).getPath();
        String red = net.minecraft.core.registries.BuiltInRegistries.ITEM
            .getKey(menu.getSlot(15).getItem().getItem()).getPath();
        if (!lime.contains("lime") && !lime.contains("green")) return;
        if (!red.contains("red")) return;
        mc.gameMode.handleContainerInput(menu.containerId, accept.on() ? 11 : 15,
            0, ContainerInput.PICKUP, mc.player);
        cooldown = 100;
    }
}
