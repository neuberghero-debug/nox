package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Posts deaths + joins to a Discord webhook. */
public class DiscordNotifier extends Module {
    public final Setting.Str webhook = add(new Setting.Str("Webhook URL", ""));
    public final Setting.Bool deaths = add(new Setting.Bool("Deaths", true));
    public final Setting.Bool joins = add(new Setting.Bool("Server joins", true));
    private boolean wasDead, wasOnline;

    public DiscordNotifier() { super("DiscordNotifier", "Webhook on deaths/joins", Category.MISC); }

    @Override public void onTick() {
        if (webhook.get().isEmpty()) return;
        boolean online = mc.player != null && mc.getCurrentServer() != null;
        if (joins.on() && online && !wasOnline)
            dev.noxclient.discord.Webhook.post(webhook.get(), "NOX",
                "joined " + mc.getCurrentServer().ip);
        if (deaths.on() && mc.player != null) {
            boolean dead = !mc.player.isAlive();
            if (dead && !wasDead) {
                var p = mc.player.blockPosition();
                dev.noxclient.discord.Webhook.post(webhook.get(), "NOX",
                    "died at " + p.getX() + " " + p.getY() + " " + p.getZ());
            }
            wasDead = dead;
        }
        wasOnline = online;
    }
}
