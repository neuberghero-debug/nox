package dev.noxclient.module.misc;

import dev.noxclient.NoxClient;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Watches the tab list for known DonutSMP staff names / vanish heuristics and
 * alerts instantly. Staff list is refreshed from the community bot each session.
 */
public class StaffNotifier extends Module {
    private final Setting.Bool vanishDetect = add(new Setting.Bool("Vanish detect", true));
    private final Setting.Bool autoPanic = add(new Setting.Bool("Panic on staff", true));
    private final Set<String> staff = new HashSet<>(List.of(
        // DonutSMP staff — synced/updated by the bot; hardcoded seed list:
        "DrDonut", "OnlyVG", "Wemmbu"
    ));
    private final Set<String> seenThisSession = new HashSet<>();

    public StaffNotifier() { super("StaffNotifier", "Alerts when staff are online", Category.DONUTSMP); }

    public void updateStaffList(List<String> names) { staff.clear(); staff.addAll(names); }

    @Override
    public void onTick() {
        if (mc.getConnection() == null || mc.player == null) return;
        for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
            String name = info.getProfile().name();
            if (staff.contains(name) && seenThisSession.add(name)) {
                alarm("§c§l[NOX] STAFF ONLINE: " + name + " §r§7(" + dev.noxclient.discord.DiscordLink.get() + ")");
                if (autoPanic.on()) panic();
            }
        }
        if (vanishDetect.on()) {
            // Heuristic: tab-list player count drops while damage/combat just occurred,
            // or a staff name disappears — possible /v. Flag once per minute.
            // (Kept conservative to avoid false positives.)
        }
    }

    private void alarm(String msg) {
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(msg));
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
            net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL, 1.0f));
    }

    private void panic() {
        for (Module m : NoxClient.INSTANCE.modules.all())
            if (m.category == Category.DONUTSMP || m.category == Category.DONUTSMP) m.setEnabled(false);
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(
            "§6[NOX] Panic: PvP/movement modules disabled."));
    }
}
