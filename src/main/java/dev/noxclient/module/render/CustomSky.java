package dev.noxclient.module.render;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import org.joml.Vector3f;

/**
 * Custom skies — premade palettes + fully custom RGB.
 * Premades: Nebula, Void, Sunset, Bloodmoon, DeepNight, Toxic.
 * Custom uses the R/G/B sliders. Applied via fog-environment mixin.
 */
public class CustomSky extends Module {
    public final Setting.Mode sky = add(new Setting.Mode("Sky", "Nebula",
        "Off", "Nebula", "Void", "Sunset", "Bloodmoon", "DeepNight", "Toxic", "Custom"));
    public final Setting.Num r = add(new Setting.Num("Custom R", 0.5, 0, 1));
    public final Setting.Num g = add(new Setting.Num("Custom G", 0.1, 0, 1));
    public final Setting.Num b = add(new Setting.Num("Custom B", 0.8, 0, 1));

    public CustomSky() { super("CustomSky", "Custom sky colors", Category.RENDER); }

    /** null = don't override (Off) — the mixin checks for it. */
    public Vector3f skyColor() {
        return switch (sky.get()) {
            case "Off"       -> null;
            case "Void"      -> new Vector3f(0.0f, 0.0f, 0.0f);
            case "Sunset"    -> new Vector3f(0.95f, 0.45f, 0.10f);
            case "Bloodmoon" -> new Vector3f(0.55f, 0.02f, 0.02f);
            case "DeepNight" -> new Vector3f(0.01f, 0.01f, 0.06f);
            case "Toxic"     -> new Vector3f(0.15f, 0.65f, 0.05f);
            case "Custom"    -> new Vector3f(r.get().floatValue(), g.get().floatValue(), b.get().floatValue());
            case "Nebula"    -> new Vector3f(0.28f, 0.05f, 0.55f);
            default          -> new Vector3f(0.47f, 0.65f, 1.0f); // vanilla-ish
        };
    }
}
