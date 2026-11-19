package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.gui.Toasts;

/** VoteReminder — nags you to /vote on a human-paced schedule; crate keys are free money. */
public class VoteReminder extends Module {
    private final Setting.Num hours = add(new Setting.Num("Every (h)", 6, 1, 24));
    private final Setting.Bool autoOpen = add(new Setting.Bool("Auto-run /vote", false,
        "Sends /vote instead of just reminding — you still pick the sites"));

    public VoteReminder() { super("VoteReminder", "Reminds you to /vote for crate keys",
        Category.DONUTSMP); }

    @Override public void onTick() {
        if (mc.player == null || mc.getConnection() == null) return;
        if (mc.player.tickCount < 60) return;
        if (!dev.noxclient.util.Legitifier.ready(this, 1.0 / (hours.get() * 3600), 0.35)) return;
        if (autoOpen.on()) mc.getConnection().sendCommand("vote");
        else Toasts.push("Vote for crate keys — /vote", 0xFFFFD870);
    }

    @Override public String status() { return isEnabled() ? "watching the clock" : "off"; }
}
