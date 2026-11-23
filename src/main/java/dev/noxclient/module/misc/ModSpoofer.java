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
    // DonutSMP fingerprints mod channels + config-phase query answers — a
    // vanilla client sends none of either, so stripping is the default.
    // Tradeoff: Simple Voice Chat / PlasmoVoice die — toggle off if needed.
    public final Setting.Bool stripAll = add(new Setting.Bool("Strip all payloads (breaks voicechat)", true));
    public static volatile ModSpoofer INSTANCE;

    public ModSpoofer() {
        super("ModSpoofer", "Hides the modded client", Category.MISC);
        INSTANCE = this;
        restore(true); // on by default — a visible "fabric" brand is a free ban
    }

    public static boolean active() { return INSTANCE != null && INSTANCE.isEnabled(); }
}
