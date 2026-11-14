package dev.noxclient.module.render;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Max brightness via nightVisionScale mixin — survives option resets. */
public class Fullbright extends Module {
    public final Setting.Num brightness = add(new Setting.Num("Brightness %", 100, 10, 100));
    public final Setting.Bool fadeIn = add(new Setting.Bool("Fade in", true));
    private long enabledAt;

    public Fullbright() { super("Fullbright", "Max brightness", Category.RENDER); }

    @Override public void onEnable() { enabledAt = System.currentTimeMillis(); }

    /** 0..1 factor read by FullbrightMixin. */
    public float scale() {
        float b = (float) (brightness.get() / 100.0);
        if (!fadeIn.on()) return b;
        float t = Math.min(1f, (System.currentTimeMillis() - enabledAt) / 600f);
        return b * (0.2f + 0.8f * t);
    }
}
