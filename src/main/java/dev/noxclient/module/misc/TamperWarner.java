package dev.noxclient.module.misc;

import dev.noxclient.gui.Toasts;
import dev.noxclient.gui.UiSounds;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.premium.LicenseManager;
import dev.noxclient.security.WebhookSec;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.TamperGuard;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * TamperWarner (Modul 11) — user-facing integrity monitor, replaces the old
 * passive TamperWarning path.
 *
 * FREE:  jar-hash verification at enable + every 60 s (catches hot-swapped
 *        jars), unauthorized-module check (module class loaded outside the
 *        signed jar), chat/banner/toast warnings.
 * PREMIUM: process/injection detection (javaagent args, post-launch attach,
 *        native ptrace check) + Discord webhook alert via WebhookSec
 *        SECURITY-BLOCK + lock mode (auto-disables modules + opens the
 *        warning screen until restart).
 */
public class TamperWarner extends Module {
    private final Setting.Bool watchJar = add(new Setting.Bool("Jar integrity", true,
        "Re-verify jar signature every 60 s"));
    private final Setting.Bool watchInject = add(new Setting.Bool("Injection watch", true,
        "Java agents / debugger attach / module loaded outside jar (premium)"));
    private final Setting.Bool lockMode = add(new Setting.Bool("Lock mode", true,
        "On compromise: disable all modules + show warning screen (premium)"));
    private final Setting.Bool discordAlert = add(new Setting.Bool("Discord alert", false,
        "Report compromises to the security channel webhook (premium)"));

    private int tick;
    private boolean locked;

    public TamperWarner() {
        super("TamperWarner", "Integrity + injection monitor", Category.MISC);
    }

    @Override
    public void onTick() {
        if (++tick % (20 * 60) != 0) return;
        if (watchJar.on()) {
            TamperGuard.tick();
            if (TamperGuard.compromised()) { trip("jar signature mismatch (hot-patched?)"); return; }
        }
        if (watchInject.on()) {
            if (!LicenseManager.assertPremium()) return;
            String why = TamperGuard.injectionDetected();
            if (why != null) trip("injection: " + why);
        }
    }

    /** Escalate — banner + sound + toast + (premium) lock + webhook. */
    private void trip(String why) {
        Minecraft mc = Minecraft.getInstance();
        UiSounds.error();
        Toasts.push("TamperWarner: " + why, 0xFFFF4444);
        if (mc.player != null)
            mc.player.sendSystemMessage(Component.literal(
                "§4[NOX] §cTamperWarner: " + why + " — do not enter accounts "
                + "until you reinstall a clean jar."));

        if (discordAlert.on() && LicenseManager.premium())
            WebhookSec.alert("TamperWarner compromise",
                why + "\nactor=" + WebhookSec.anonId()
                    + "\nversion=" + dev.noxclient.NoxClient.VERSION, why);

        if (lockMode.on() && LicenseManager.premium()) {
            locked = true;
            for (Module m : dev.noxclient.NoxClient.INSTANCE.modules.all())
                if (m != this && m.isEnabled()) m.setEnabled(false);
            TamperGuard.maybeShowScreen(mc);
        }
    }

    /** FailSafe level-3 hook — force the lock path regardless of module state. */
    public static void engageLock() {
        var m = dev.noxclient.NoxClient.INSTANCE.modules.get("TamperWarner");
        if (m instanceof TamperWarner t) {
            t.locked = true;
            for (var o : dev.noxclient.NoxClient.INSTANCE.modules.all())
                if (o != t && o.isEnabled()) o.setEnabled(false);
            TamperGuard.maybeShowScreen(Minecraft.getInstance());
        }
    }

    /** Lock state — the warning screen stays until restart once tripped. */
    public static boolean locked() {
        var m = dev.noxclient.NoxClient.INSTANCE.modules.get("TamperWarner");
        return m instanceof TamperWarner t && t.locked;
    }
}
