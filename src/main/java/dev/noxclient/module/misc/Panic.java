package dev.noxclient.module.misc;

import dev.noxclient.NoxClient;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import java.util.ArrayList;
import java.util.List;

/**
 * Panic — one keypress kills every active module instantly (screenshare safety).
 * With "Profile swap" on, the module snapshot is remembered: pressing Panic a
 * second time restores the exact set that was running before (full-legit ->
 * back to your setup). Bind a key to it like any other module; it never stays
 * on, so it can fire again on the next press.
 */
public class Panic extends Module {
    /** Names that stay enabled through a panic (whitelisted safety rails). */
    public final Setting.Str keep = add(new Setting.Str("Keep", "AntiAFK,StaffNotifier"));
    public final Setting.Bool closeGui = add(new Setting.Bool("Close NOX window", true));
    public final Setting.Bool toast = add(new Setting.Bool("Toast", true));
    public final Setting.Bool profileSwap = add(new Setting.Bool("Profile swap", true));

    /** Names of the modules the last panic disabled — null when nothing to restore. */
    private static List<String> snapshot;

    public Panic() { super("Panic", "Disables all modules instantly", Category.MISC); }

    @Override
    public void onEnable() {
        if (profileSwap.on() && snapshot != null) {
            int restored = 0;
            for (String n : snapshot) {
                var m = NoxClient.INSTANCE.modules.get(n);
                if (m != null && !m.isEnabled()) { m.setEnabled(true); restored++; }
            }
            snapshot = null;
            if (toast.on()) dev.noxclient.gui.Toasts.push(
                "PANIC restore — " + restored + " modules back on", 0xFF66FF99);
            setEnabled(false);
            return;
        }
        var keepSet = new java.util.HashSet<String>();
        for (String n : keep.get().split(",")) keepSet.add(n.trim().toLowerCase());
        var kill = new ArrayList<Module>();
        for (var m : NoxClient.INSTANCE.modules.all())
            if (m != this && m.isEnabled() && !keepSet.contains(m.name.toLowerCase())) kill.add(m);
        for (var m : kill) m.setEnabled(false);
        snapshot = kill.stream().map(m -> m.name).toList();
        if (closeGui.on()) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            // any NOX screen counts — the card GUI flag misses the NVG ClickGUI
            if (dev.noxclient.gui.NoxWindowScreen.open
                || mc.gui.screen() instanceof dev.noxclient.gui.NoxClickGui
                || mc.gui.screen() instanceof dev.noxclient.gui.NoxWindowScreen)
                mc.setScreenAndShow(null);
        }
        if (toast.on()) dev.noxclient.gui.Toasts.push("PANIC — " + kill.size() + " modules off", 0xFFFF6666);
        setEnabled(false);
    }
    @Override public String status() {
        return "trigger — kills everything, second press restores";
    }

}
