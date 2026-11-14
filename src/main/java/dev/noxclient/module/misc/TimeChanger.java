package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Overrides the client-side world time (visual only). */
public class TimeChanger extends Module {
    public final Setting.Mode time = add(new Setting.Mode("Time", "Day",
        "Day", "Sunset", "Night", "Midnight"));

    public TimeChanger() { super("TimeChanger", "Client-side world time", Category.MISC); }

    @Override public void onTick() {
        if (mc.level == null) return;
        long t = switch (time.get()) {
            case "Sunset" -> 12500L;
            case "Night" -> 13500L;
            case "Midnight" -> 18000L;
            default -> 1000L;
        };
        mc.level.setTimeFromServer(t);
    }
}
