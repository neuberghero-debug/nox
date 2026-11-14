package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;

/** Fires /rtp (or a custom command) on a timer — passive base scouting. */
public class AutoRTP extends Module {
    public final Setting.Str command = add(new Setting.Str("Command", "/rtp"));
    public final Setting.Num interval = add(new Setting.Num("Interval s", 60, 10, 600));
    public final Setting.Bool onlyAFK = add(new Setting.Bool("Only when idle", false));
    private long lastMove;

    public AutoRTP() { super("AutoRTP", "Periodic /rtp for scouting", Category.DONUTSMP); }

    @Override public void onTick() {
        if (mc.getConnection() == null || mc.player == null) return;
        if (mc.player.getDeltaMovement().horizontalDistanceSqr() > 0.0004)
            lastMove = System.currentTimeMillis();
        if (onlyAFK.on() && System.currentTimeMillis() - lastMove < 30000) return;
        if (!Legitifier.ready(this, 1.0 / interval.get(), 0.5)) return;
        String c = command.get();
        if (c.startsWith("/")) mc.getConnection().sendCommand(c.substring(1));
    }
}
