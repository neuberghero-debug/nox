package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/**
 * ShulkerPeek — ported from polardlc ShulkerPreview. Hover a shulker box in
 * ANY inventory and get a live 9×3 preview of its contents — the stash-hunt
 * essential: check boxed loot without placing or opening anything.
 * Drawn by ContainerScreenMixin at extractRenderState TAIL.
 */
public class ShulkerPeek extends Module {
    public final Setting.Bool enabled_ = add(new Setting.Bool("Preview on hover", true));
    public final Setting.Num scale = add(new Setting.Num("Preview scale", 1.0, 0.5, 2.0));

    private static ShulkerPeek inst;
    public ShulkerPeek() { super("ShulkerPeek", "Hover a shulker → see its contents",
        Category.DONUTSMP); inst = this; }
    public static boolean active() { return inst != null && inst.enabled_.on() && inst.isEnabled(); }
    public static float previewScale() { return inst == null ? 1f : inst.scale.floatVal(); }
}
