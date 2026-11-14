package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Periodic chat advertiser — DonutSMP shop promos on autopilot. */
public class Spammer extends Module {
    public final Setting.Str message = add(new Setting.Str("Message", "buy my stuff at /visit"));
    public final Setting.Num interval = add(new Setting.Num("Seconds", 30, 5, 300));
    public final Setting.Bool randomSuffix = add(new Setting.Bool("Random suffix", true));
    private long next;

    public Spammer() { super("Spammer", "Auto chat messages", Category.MISC); }

    @Override public void onTick() {
        if (mc.player == null || mc.getConnection() == null) return;
        long now = System.currentTimeMillis();
        if (now < next) return;
        next = now + (long) (interval.get() * 1000);
        String msg = message.get();
        if (randomSuffix.on()) msg += " #" + (int) (Math.random() * 10000);
        mc.getConnection().sendChat(msg);
    }
}
