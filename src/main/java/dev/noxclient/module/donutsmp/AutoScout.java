package dev.noxclient.module.donutsmp;

import dev.noxclient.NoxClient;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.gui.Toasts;

import java.util.ArrayList;
import java.util.List;

/**
 * AutoScout — hands-free base hunting. Arms the whole finder stack
 * (SusChunkFinder/BaseRadar, SoundFind, StorageESP, ArtificialLightFinder,
 * ChunkFinder, StashLogger) and cycles /rtp on a human-paced interval, so
 * each teleport lands somewhere new for the finders to sweep. The finders
 * score, notify and persist hits to the stash index on their own — this
 * just keeps you moving and the sensors running. Settles after each RTP
 * before the next one so chunks actually load.
 */
public class AutoScout extends Module {
    private final Setting.Num hopSec = add(new Setting.Num("RTP every (s)", 150, 45, 900,
        "Base delay — gaussian-jittered ±50% plus fatigue pauses"));
    private final Setting.Str rtpCmd = add(new Setting.Str("RTP command", "rtp"));
    private final Setting.Bool finders = add(new Setting.Bool("Arm finders", true,
        "Toggle the BaseRadar + sound + storage finder stack"));

    private final List<Module> armed = new ArrayList<>();
    private long lastHop;
    private int hops;

    private static final String[] STACK = {
        "SusChunkFinder", "SoundFind", "StorageESP",
        "ArtificialLightFinder", "ChunkFinder", "StashLogger"
    };

    public AutoScout() { super("AutoScout", "Automated base hunting — RTP + finder stack",
        Category.DONUTSMP); }

    @Override protected void onEnable() {
        armed.clear(); hops = 0; lastHop = 0;
        if (finders.on()) {
            var mm = NoxClient.INSTANCE.modules;
            for (String n : STACK) {
                Module m = mm.get(n);
                if (m != null && !m.isEnabled()) { m.setEnabled(true); armed.add(m); }
            }
        }
        Toasts.push("AutoScout armed — " + armed.size() + " finders up", 0xFF66D9A0);
    }

    @Override public void onTick() {
        if (mc.player == null || mc.getConnection() == null) return;
        if (mc.player.tickCount < 60) return;               // join window
        // hop on a jittered schedule — Legitifier handles fatigue pauses
        if (!dev.noxclient.util.Legitifier.ready(this, 1.0 / hopSec.get(), 0.5)) return;
        mc.getConnection().sendCommand(rtpCmd.get());
        lastHop = System.currentTimeMillis(); hops++;
        mark("rtp #" + hops);
    }

    @Override protected void onDisable() {
        for (Module m : armed) if (m.isEnabled()) m.setEnabled(false);
        armed.clear();
    }

    @Override public String status() {
        return isEnabled() ? "hopping — " + hops + " rtps, "
            + (lastHop == 0 ? "first soon" : "last " + (System.currentTimeMillis() - lastHop) / 1000 + "s ago")
            : "off";
    }
}
