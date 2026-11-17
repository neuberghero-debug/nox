package dev.noxclient.module.misc;

import dev.noxclient.NoxClient;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Streamer/screenshare mode — masks identity + location without killing the UI.
 *  NameProtect via mixin, coords/server-IP gating in HUD + Discord RPC. */
public class StreamerMode extends Module {
    public final Setting.Bool hideName = add(new Setting.Bool("Hide name", true));
    public final Setting.Bool allPlayers = add(new Setting.Bool("All player names", false));
    public final Setting.Str fakeName = add(new Setting.Str("Fake name", "Steve"));
    public final Setting.Bool hideCoords = add(new Setting.Bool("Hide coords", true));
    public final Setting.Bool hideServer = add(new Setting.Bool("Hide server IP", true));
    public final Setting.Bool hideSkin = add(new Setting.Bool("Hide skin", false));

    public StreamerMode() { super("StreamerMode", "Masks identity on stream", Category.MISC); }

    public static StreamerMode get() {
        return NoxClient.INSTANCE.modules.get("StreamerMode") instanceof StreamerMode s ? s : null;
    }

    public static boolean active() {
        StreamerMode s = get();
        return s != null && s.isEnabled();
    }

    public static boolean coordsHidden() {
        StreamerMode s = get();
        return s != null && s.isEnabled() && s.hideCoords.on();
    }

    public static boolean serverHidden() {
        StreamerMode s = get();
        return s != null && s.isEnabled() && s.hideServer.on();
    }

    public static String fakeName() {
        StreamerMode s = get();
        return s == null ? "Steve" : s.fakeName.get();
    }
}
