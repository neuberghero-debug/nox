package dev.noxclient.module.render;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import org.joml.Vector3f;

/**
 * Custom skies — simple fog palettes (Nebula … Custom) AND full shader skies
 * (Gargantua black hole, DeepSpace nebula, Aurora, RingPlanet) rendered by a
 * custom renderpearl pipeline via NoxShaderSky.
 */
public class CustomSky extends Module {
    public final Setting.Mode sky = add(new Setting.Mode("Sky", "Nebula",
        "Off", "Nebula", "Void", "Sunset", "Bloodmoon", "DeepNight", "Toxic", "Custom",
        "Sakura", "Ocean", "Golden", "Abyss", "Candy", "Inferno",
        "Gargantua", "DeepSpace", "Aurora", "RingPlanet"));
    public final Setting.Mode palette = add(Setting.Mode.of("Shader palette", "NOX",
        "NOX", "Ember", "Frost", "Toxic", "Blood", "Candy", "Abyss", "Golden", "Sakura"));
    public final Setting.Num stars = add(new Setting.Num("Star density", 1.2, 0, 2));
    public final Setting.Num nebula = add(new Setting.Num("Nebula/Aurora", 1.0, 0, 2));
    public final Setting.Num band = add(new Setting.Num("Galaxy band", 1.0, 0, 2));
    public final Setting.Num exposure = add(new Setting.Num("Exposure", 1.0, 0.2, 3));
    public final Setting.Num r = add(new Setting.Num("Custom R", 0.5, 0, 1));
    public final Setting.Num g = add(new Setting.Num("Custom G", 0.1, 0, 1));
    public final Setting.Num b = add(new Setting.Num("Custom B", 0.8, 0, 1));

    public CustomSky() { super("CustomSky", "Custom + shader skies", Category.RENDER); }

    /** -1 = not a shader mode; else 0 DeepSpace, 1 Aurora, 2 RingPlanet, 3 Gargantua. */
    public int shaderMode() {
        return switch (sky.get()) {
            case "DeepSpace" -> 0;
            case "Aurora" -> 1;
            case "RingPlanet" -> 2;
            case "Gargantua" -> 3;
            default -> -1;
        };
    }

    /** {ColorLow, ColorHigh, ColorBand, Zenith, Horizon} as 0xRRGGBB. */
    public int[] palette() {
        return switch (palette.get()) {
            case "Ember" -> new int[]{0xFF5E3A, 0xFFB347, 0xFF8C5A, 0x0C0608, 0x1E0E10};
            case "Frost" -> new int[]{0x3A8FBF, 0xBFE8FF, 0x6FB7E8, 0x060C14, 0x12202E};
            case "Toxic" -> new int[]{0x2BCC71, 0xC8FF3E, 0x39FF88, 0x081008, 0x142414};
            case "Blood" -> new int[]{0xCC2B3C, 0xFF6B5E, 0x8C1F2E, 0x100608, 0x1E0C10};
            case "Candy" -> new int[]{0xFF6EC7, 0xFFB7E8, 0xFF8FD5, 0x120812, 0x241222};
            case "Abyss" -> new int[]{0x0A2E4E, 0x1E7AA8, 0x14506E, 0x020408, 0x060E18};
            case "Golden" -> new int[]{0xE8B23E, 0xFFE97A, 0xC88C2E, 0x0E0A06, 0x201810};
            case "Sakura" -> new int[]{0xE87AB8, 0xFFD5EC, 0xC76FA8, 0x0E060C, 0x1E1018};
            default -> new int[]{0x7A2BE2, 0x5AC8FF, 0x9B4DFF, 0x05050E, 0x141428};
        };
    }

    /** null = don't override (Off / shader modes) — the fog mixin checks for it. */
    public Vector3f skyColor() {
        if (shaderMode() >= 0) return null;
        return switch (sky.get()) {
            case "Off"       -> null;
            case "Void"      -> new Vector3f(0.0f, 0.0f, 0.0f);
            case "Sunset"    -> new Vector3f(0.95f, 0.45f, 0.10f);
            case "Bloodmoon" -> new Vector3f(0.55f, 0.02f, 0.02f);
            case "DeepNight" -> new Vector3f(0.01f, 0.01f, 0.06f);
            case "Toxic"     -> new Vector3f(0.15f, 0.65f, 0.05f);
            case "Sakura"    -> new Vector3f(0.75f, 0.35f, 0.60f);
            case "Ocean"     -> new Vector3f(0.02f, 0.18f, 0.38f);
            case "Golden"    -> new Vector3f(0.85f, 0.60f, 0.12f);
            case "Abyss"     -> new Vector3f(0.01f, 0.04f, 0.10f);
            case "Candy"     -> new Vector3f(0.90f, 0.35f, 0.70f);
            case "Inferno"   -> new Vector3f(0.80f, 0.18f, 0.02f);
            case "Custom"    -> new Vector3f(r.get().floatValue(), g.get().floatValue(), b.get().floatValue());
            default          -> new Vector3f(0.28f, 0.05f, 0.55f); // Nebula
        };
    }
}
