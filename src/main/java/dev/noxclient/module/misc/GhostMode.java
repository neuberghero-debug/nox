package dev.noxclient.module.misc;

import dev.noxclient.NoxClient;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;

/** One-key killswitch: instantly disables every flaggable module. */
public class GhostMode extends Module {
    public GhostMode() { super("GhostMode", "Instant legit profile", Category.MISC); }

    @Override
    protected void onEnable() {
        for (Module m : NoxClient.INSTANCE.modules.all()) {
            if (m != this && m.category != Category.MISC) m.setEnabled(false);
        }
        setEnabled(false); // it's a trigger, not a persistent module
    }
}
