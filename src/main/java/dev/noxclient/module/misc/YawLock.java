package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Snaps your yaw to fixed angles — clean highways/bridges. */
public class YawLock extends Module {
    public final Setting.Num step = add(new Setting.Num("Snap", 45, 15, 90));

    public YawLock() { super("YawLock", "Snap yaw to angles", Category.MISC); }

    @Override public void onTick() {
        if (mc.player == null) return;
        float s = step.get().floatValue();
        mc.player.setYRot(Math.round(mc.player.getYRot() / s) * s);
    }
}
