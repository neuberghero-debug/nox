package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.util.Opsec;

/**
 * ModSpoofer — status wrapper for the always-on baseline spoof.
 * Brand is ALWAYS answered "vanilla" and non-minecraft custom payloads are
 * ALWAYS stripped (util/Opsec) — baseline can't be toggled off, it isn't a
 * module setting anymore. The extra minecraft-payload hardening toggle
 * lives in the Multiplexer screen.
 */
public class ModSpoofer extends Module {
    public static volatile ModSpoofer INSTANCE;

    public ModSpoofer() { super("ModSpoofer", "Client fingerprint hidden (always on)", Category.MISC); INSTANCE = this; }

    /** Kept for callers that asked "is the spoofer on" — it always is. */
    public static boolean active() { return true; }

    @Override protected void onEnable() { setEnabled(false); }   // status-only

    @Override public String status() {
        return "brand → vanilla · channels stripped" + (Opsec.stripAll ? " · full strip" : "");
    }
}
