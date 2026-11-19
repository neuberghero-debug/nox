package dev.noxclient.module.donutsmp;

import dev.noxclient.NoxClient;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.gui.Toasts;

import java.util.ArrayList;
import java.util.List;

/**
 * NightGrind — one toggle for the overnight AFK stack. Arming it turns on
 * every passive money/safety module (AntiAFK, AutoReconnect, AutoRespawn,
 * AutoLog, StaffNotifier with panic-on-staff, AutoSell, AutoDaily,
 * SessionStats) and turns OFF anything flagged `risky` so nothing can flag
 * while you sleep. On disable it only unwinds what it enabled — modules you
 * had running stay running.
 */
public class NightGrind extends Module {
    private final Setting.Bool sell = add(new Setting.Bool("Auto-sell", true,
        "Arm AutoSell too (needs your filter configured)"));
    private final Setting.Bool claims = add(new Setting.Bool("Daily claims", true,
        "Arm AutoDaily + AutoKit for login rewards"));
    private final Setting.Num sellInterval = add(new Setting.Num("Sell pacing (s)", 45, 20, 300,
        "Bump AutoSell's interval — slower sells read more human overnight"));

    /** Modules NightGrind turned on — unwound on disable. */
    private final List<Module> armed = new ArrayList<>();

    private static final String[] SAFE = {
        "AntiAFK", "AutoReconnect", "AutoRespawn", "AutoLog",
        "StaffNotifier", "SessionStats", "VisualRange"
    };

    public NightGrind() { super("NightGrind", "Overnight AFK stack — safe money while you sleep",
        Category.DONUTSMP); }

    @Override protected void onEnable() {
        armed.clear();
        var mm = NoxClient.INSTANCE.modules;
        for (String n : SAFE) arm(mm.get(n));
        if (sell.on()) {
            Module as = mm.get("AutoSell");
            if (as != null) {
                // pace sells slower overnight — 20s spam on an empty inv
                // looks scripted to any staff watching replay
                for (var s : as.settings)
                    if (s.name.equals("Interval (s)") && s instanceof Setting.Num num
                        && num.get() < sellInterval.get())
                        num.set(sellInterval.get());
                arm(as);
            }
        }
        if (claims.on()) { arm(mm.get("AutoDaily")); arm(mm.get("AutoKit")); }
        // kill anything ban-bait that was left on
        int killed = 0;
        for (Module m : mm.all())
            if (m.isEnabled() && m.risky) { m.setEnabled(false); killed++; }
        Toasts.push("NightGrind armed — " + armed.size() + " modules up"
            + (killed > 0 ? ", " + killed + " risky killed" : ""), 0xFF66D9A0);
    }

    private void arm(Module m) {
        if (m != null && !m.isEnabled()) { m.setEnabled(true); armed.add(m); }
    }

    @Override protected void onDisable() {
        for (Module m : armed) if (m.isEnabled()) m.setEnabled(false);
        armed.clear();
    }

    @Override public String status() {
        return isEnabled() ? "afk stack armed (" + armed.size() + " modules)" : "off";
    }
}
