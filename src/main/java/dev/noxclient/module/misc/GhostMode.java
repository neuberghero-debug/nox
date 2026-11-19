package dev.noxclient.module.misc;

import dev.noxclient.NoxClient;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;

/** One-key killswitch: instantly disables every flaggable module. */
public class GhostMode extends Module {
    /** Pure client-side / passive modules that stay on through a panic. */
    private static final java.util.Set<String> KEEP = java.util.Set.of(
        "GhostMode", "Panic", "StaffNotifier", "DiscordRPCModule", "DiscordNotifier",
        "Friends", "DeathWaypoint", "DurabilityAlert",
        "MobAlert", "AntiBlind", "AntiFire", "NoHurtCam", "NoWeather", "TimeChanger",
        "Hitboxes", "HudWidgets", "CustomSky", "Capes", "Cosmetics",
        "SpotifyHud", "LyricsHud", "Zoom",
        // passive detectors — send nothing, undetectable, keep watching
        "SoundFind", "StashLogger", "AntiVanish", "VisualRange", "Tracers");

    public GhostMode() { super("GhostMode", "Instant legit profile", Category.MISC); }

    @Override
    protected void onEnable() {
        for (Module m : NoxClient.INSTANCE.modules.all())
            if (m != this && !KEEP.contains(m.name)) m.setEnabled(false);
        setEnabled(false); // it's a trigger, not a persistent module
    }
}
