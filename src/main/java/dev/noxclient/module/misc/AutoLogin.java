package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;

/** Sends /login + /register automatically on cracked/auth servers. */
public class AutoLogin extends Module {
    public final Setting.Str password = add(new Setting.Str("Password", ""));
    public final Setting.Num delayS = add(new Setting.Num("Delay s", 1.5, 0, 10));
    private boolean sent;
    private long joined;
    private Object lastConn;

    public AutoLogin() { super("AutoLogin", "Auto /login on join", Category.MISC); }

    @Override public void onTick() {
        var conn = mc.getConnection();
        // a new connection means a fresh join — re-arm even without a disable
        if (conn != lastConn) { lastConn = conn; sent = false; joined = 0; }
        if (conn == null || mc.player == null || password.get().isEmpty()) return;
        if (sent) return;
        if (joined == 0) { joined = System.currentTimeMillis(); return; }
        if (System.currentTimeMillis() - joined < delayS.get() * 1000) return;
        if (!Legitifier.ready(this, 1, 0)) return;
        mc.getConnection().sendCommand("login " + password.get());
        sent = true;
    }

    @Override public void onDisable() { sent = false; joined = 0; }
}
