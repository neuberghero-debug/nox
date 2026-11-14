package dev.noxclient.module.render;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Optifine-style zoom — CameraFovMixin narrows rendered FOV while active (bind to Hold). */
public class Zoom extends Module {
    public final Setting.Num factor = add(new Setting.Num("Factor", 4, 2, 10));
    public final Setting.Bool smooth = add(new Setting.Bool("Smooth cam", true));
    private boolean origSmooth;

    public Zoom() { super("Zoom", "FOV zoom (bind to Hold)", Category.RENDER); }

    @Override public void onEnable() {
        origSmooth = mc.options.smoothCamera;
    }

    @Override public void onTick() {
        if (smooth.on()) mc.options.smoothCamera = true;
    }

    @Override public void onDisable() {
        mc.options.smoothCamera = origSmooth;
    }
    @Override public String status() {
        return "FOV /" + factor.get().intValue() + " (visual only, options untouched)";
    }

}
