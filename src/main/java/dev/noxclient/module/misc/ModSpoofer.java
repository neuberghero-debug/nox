package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/**
 * ModSpoofer — defeats server-side client fingerprinting (DonutSMP probes
 * minecraft:brand and other custom payloads to detect modded clients).
 *   Brand     → replies "vanilla" instead of "fabric"/"noxclient"
 *   Strip all → additionally drops every non-brand custom payload
 *               (Fabric handshake, mod channels) — aggressive, toggle-aware
 */
public class ModSpoofer extends Module {
    public final Setting.Bool stripAll = add(new Setting.Bool("Strip all payloads", false));
    public static volatile ModSpoofer INSTANCE;

    public ModSpoofer() { super("ModSpoofer", "Hides the modded client", Category.MISC); INSTANCE = this; }

    public static boolean active() { return INSTANCE != null && INSTANCE.isEnabled(); }
}
