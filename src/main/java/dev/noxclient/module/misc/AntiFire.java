package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Extinguishes yourself instantly when you catch fire. */
public class AntiFire extends Module {
    public final Setting.Mode mode = add(new Setting.Mode("Mode", "Extinguish",
        "Extinguish", "Hide overlay", "Both"));

    public AntiFire() { super("AntiFire", "Puts out fire instantly", Category.MISC); }

    @Override public void onTick() {
        if (mc.player == null) return;
        if (!mode.get().equals("Hide overlay") && mc.player.getRemainingFireTicks() > 0)
            mc.player.setRemainingFireTicks(0);
    }
}
