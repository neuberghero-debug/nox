package dev.noxclient.module.misc;

import dev.noxclient.NoxClient;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.module.render.ESP;

/** Streamer/screenshare mode: kills all visual overlays + module ESP instantly. */
public class OpSec extends Module {
    public OpSec() { super("OpSec", "Hides client visuals", Category.MISC); }

    @Override
    protected void onEnable() {
        for (Module m : NoxClient.INSTANCE.modules.by(Category.RENDER)) m.setEnabled(false);
        setEnabled(false); // trigger-style
    }
}
