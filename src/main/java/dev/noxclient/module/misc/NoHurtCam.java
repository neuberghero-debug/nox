package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Kills the hurt screen-shake + optional red flash. */
public class NoHurtCam extends Module {
    public final Setting.Bool noFlash = add(new Setting.Bool("No red flash", false));

    public NoHurtCam() { super("NoHurtCam", "Removes hurt cam", Category.MISC); }

    @Override public void onTick() {
        if (mc.player == null) return;
        mc.player.hurtTime = 0;
        if (noFlash.on()) mc.player.hurtDuration = 0;
    }
}
