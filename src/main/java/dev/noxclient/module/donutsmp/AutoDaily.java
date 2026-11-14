package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Claims daily rewards after joining — /daily, /claim, /kits as configured. */
public class AutoDaily extends Module {
    public final Setting.Str commands = add(new Setting.Str("Commands", "daily,claim"));
    public final Setting.Num delaySec = add(new Setting.Num("Delay (s)", 8, 2, 60));
    private long joinedAt = -1; private int idx;

    public AutoDaily() { super("AutoDaily", "Auto-claim daily rewards", Category.DONUTSMP); }

    @Override public void onEnable() {
        joinedAt = System.currentTimeMillis(); idx = 0;
    }

    @Override public void onTick() {
        if (mc.player == null || mc.getConnection() == null || joinedAt < 0) return;
        String[] cmds = commands.get().split(",");
        if (idx >= cmds.length) { joinedAt = -1; return; }
        long wait = (long) (delaySec.get() * 1000) + idx * 3000;
        if (System.currentTimeMillis() - joinedAt < wait) return;
        mc.getConnection().sendCommand(cmds[idx].trim());
        idx++;
    }
}
