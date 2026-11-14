package dev.noxclient.module.movement;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Auto-jumps while moving — keeps sprint momentum. */
public class BunnyHop extends Module {
    public final Setting.Bool sprintOnly = add(new Setting.Bool("Only while sprinting", true));
    public final Setting.Bool forwardOnly = add(new Setting.Bool("Only moving forward", true));

    public BunnyHop() { super("BunnyHop", "Auto-jump while moving", Category.MOVEMENT); }

    @Override public void onTick() {
        if (mc.player == null || !mc.player.onGround()) return;
        if (sprintOnly.on() && !mc.player.isSprinting()) return;
        if (forwardOnly.on() && mc.player.input.getMoveVector().y <= 0) return;
        mc.player.jumpFromGround();
    }
}
