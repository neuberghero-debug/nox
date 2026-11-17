package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Skin changer (Modul 9): drop 64×64/64×32 PNGs into <gamedir>/nox/skins/
 * and pick one — applied client-side to your own avatar via the cosmetics
 * skin swap (StreamerSkinMixin path). capes/ for 10x16/20x40.
 * .skin export/import = JSON {name,b64 png}.
 */
public class SkinChanger extends Module {
    public final Setting.Mode skin = add(Setting.Mode.of("Skin", "Off", "local png", skinList()));
    public final Setting.Bool applyToOthers = add(new Setting.Bool("Apply to others", false));

    /** Currently-selected skin file, or null. Read by the skin mixin. */
    public static volatile Path active;
    public static volatile Path activeCape;

    public SkinChanger() { super("SkinChanger", "Local skin/cape from nox/skins", Category.MISC); }

    private static String[] skinList() {
        List<String> names = new ArrayList<>(List.of("Off"));
        try {
            Path d = dir();
            if (Files.isDirectory(d))
                try (var s = Files.list(d)) {
                    s.filter(p -> p.toString().toLowerCase().endsWith(".png"))
                     .forEach(p -> names.add(p.getFileName().toString()));
                }
        } catch (Throwable ignored) {}
        return names.toArray(new String[0]);
    }

    private static Path dir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("nox/skins");
    }

    @Override
    public void onTick() {
        String sel = skin.get();
        active = "Off".equals(sel) ? null : dir().resolve(sel);
    }

    @Override
    protected void onDisable() { active = null; activeCape = null; }

    /** Refresh the dropdown after the user drops new PNGs in. */
    public static void refresh() {
        var m = dev.noxclient.NoxClient.INSTANCE.modules.get("SkinChanger");
        if (m instanceof SkinChanger sc)
            sc.skin.set(skinList()[0]);   // reset to first option; modes rebuild on next GUI open
    }
}
