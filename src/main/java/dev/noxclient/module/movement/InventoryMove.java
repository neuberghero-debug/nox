package dev.noxclient.module.movement;

import com.mojang.blaze3d.platform.InputConstants;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import net.minecraft.world.entity.player.Input;

/**
 * InventoryMove — keeps WASD/jump/sprint movement flowing while a screen is
 * open by writing directly into player.input.keyPresses. Pairs with the
 * AntiESPBypass stash-scanning workflow where you browse menus mid-farm.
 */
public class InventoryMove extends Module {
    private final dev.noxclient.setting.Setting.Bool anyScreen =
        add(new dev.noxclient.setting.Setting.Bool("Any screen", true));

    public InventoryMove() { super("InventoryMove", "Move with screens open", Category.MOVEMENT); }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (anyScreen.on() && mc.gui.screen() == null) return;
        var in = mc.player.input;
        in.keyPresses = new Input(
            InputConstants.isKeyDown(InputConstants.KEY_W),
            InputConstants.isKeyDown(InputConstants.KEY_S),
            InputConstants.isKeyDown(InputConstants.KEY_A),
            InputConstants.isKeyDown(InputConstants.KEY_D),
            InputConstants.isKeyDown(InputConstants.KEY_SPACE),
            InputConstants.isKeyDown(InputConstants.KEY_LSHIFT),
            InputConstants.isKeyDown(InputConstants.KEY_LCONTROL));
    }
}
