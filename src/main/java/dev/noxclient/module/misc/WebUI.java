package dev.noxclient.module.misc;

import dev.noxclient.gui.Toasts;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/**
 * WebUI — embedded web console + REST API on 127.0.0.1. While enabled it
 * boots with the client (module state persists via config). Dashboard at
 * http://127.0.0.1:PORT — modules, live state, finder marks. API:
 * /api/state /api/modules /api/toggle /api/set /api/marks /api/stashes /api/chat.
 */
public class WebUI extends Module {
    private final Setting.Num port = add(new Setting.Num("Port", 7331, 1024, 65535));

    public WebUI() { super("WebUI", "Web console + REST API on localhost", Category.MISC); }

    @Override protected void onEnable() {
        String r = dev.noxclient.web.NoxWeb.start(port.intVal());
        if (r.startsWith("bind failed")) {
            Toasts.push("⚠ WebUI: " + r, 0xFFFF6666);
            return;
        }
        Toasts.push("◈ WebUI → " + r, 0xFF66D9A0);
    }

    @Override protected void onDisable() {
        dev.noxclient.web.NoxWeb.stop();
    }

    @Override public String status() {
        return dev.noxclient.web.NoxWeb.running()
            ? ":" + dev.noxclient.web.NoxWeb.port() : "stopped";
    }
}
