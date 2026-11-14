package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Rejoins the last server after a kick, on a randomized delay. */
public class AutoReconnect extends Module {
    private final Setting.Num delay = add(new Setting.Num("Delay (s)", 15, 5, 120));
    private long dcAt = -1;
    private net.minecraft.client.multiplayer.ServerData lastServer;

    public AutoReconnect() { super("AutoReconnect", "Rejoins after kicks", Category.MISC); }

    @Override
    public void onTick() {
        var server = mc.getCurrentServer();
        if (server != null) { lastServer = server; dcAt = -1; return; }
        if (lastServer == null) return;
        // only reconnect on a REAL disconnect — getCurrentServer() is also null
        // on the title screen and in singleplayer (yanked players out of SP worlds)
        if (!(mc.gui.screen() instanceof net.minecraft.client.gui.screens.DisconnectedScreen)
            || mc.player != null) { dcAt = -1; return; }
        if (dcAt < 0) { dcAt = System.currentTimeMillis(); return; }
        if (System.currentTimeMillis() - dcAt < delay.get() * 1000) return;
        dcAt = -1;
        try {
            var addr = net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(lastServer.ip);
            net.minecraft.client.gui.screens.ConnectScreen.startConnecting(
                new net.minecraft.client.gui.screens.TitleScreen(), mc, addr, lastServer, false, null);
        } catch (Exception ignored) {}
    }
}
