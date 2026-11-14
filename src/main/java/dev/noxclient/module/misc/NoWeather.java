package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Clears rain/thunder client-side — clean skies always. */
public class NoWeather extends Module {
    public final Setting.Bool thunder = add(new Setting.Bool("Also kill thunder", true));

    public NoWeather() { super("NoWeather", "Removes rain + thunder", Category.MISC); }

    @Override public void onTick() {
        if (mc.level == null) return;
        mc.level.setRainLevel(0f);
        if (thunder.on()) mc.level.setThunderLevel(0f);
    }
}
