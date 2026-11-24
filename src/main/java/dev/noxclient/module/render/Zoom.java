package dev.noxclient.module.render;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Optifine-style zoom — CameraFovMixin narrows rendered FOV while C held. */
public class Zoom extends Module {
    public final Setting.Num factor = add(new Setting.Num("Factor", 4, 2, 10, "Zoom strength — lower zooms further"));
    public final Setting.Bool smooth = add(new Setting.Bool("Smooth cam", true, "Cinematic-style smoothed camera"));
    private boolean origSmooth;

    public Zoom() {
        super("Zoom", "FOV zoom (hold C)", Category.RENDER);
        defaults(6, "Hold");   // SDL_SCANCODE_C — the standard zoom key
    }

    @Override public void onEnable() {
        origSmooth = mc.options.smoothCamera;
    }

    @Override public void onTick() {
        // release the option immediately if it gets unticked mid-zoom
        mc.options.smoothCamera = smooth.on() || origSmooth;
    }

    @Override public void onDisable() {
        mc.options.smoothCamera = origSmooth;
    }
    @Override public String status() {
        return "FOV /" + factor.get().intValue();
    }

}
