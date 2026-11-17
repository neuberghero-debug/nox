package dev.noxclient.module.misc;

import dev.noxclient.NoxClient;
import dev.noxclient.gui.Toasts;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * StaffNotifier — multi-signal staff/spectate detection, fully passive:
 *   1. Name watch   — known DonutSMP staff names appear in the tab list.
 *   2. Gamemode watch — any tab-listed player in SPECTATOR or CREATIVE.
 *      Survival players never show either on DonutSMP: spectator = a staff
 *      member literally watching you, creative = admin on duty.
 *   3. Vanish watch — a player you could recently SEE (rendered entity
 *      within the last 15 s) drops out of the tab list → probable /v.
 * Any hit alarms + optionally triggers the GhostMode killswitch (kills
 * every flaggable module while passive detectors like this one stay on).
 */
public class StaffNotifier extends Module {
    private final Setting.Bool nameWatch = add(new Setting.Bool("Name watch", true,
        "Known DonutSMP staff names in the tab list"));
    private final Setting.Bool gamemodeWatch = add(new Setting.Bool("Gamemode watch", true,
        "Spectator/creative tab entries — staff surveillance"));
    private final Setting.Bool vanishWatch = add(new Setting.Bool("Vanish watch", true,
        "A recently visible player drops from the tab list"));
    private final Setting.Bool autoPanic = add(new Setting.Bool("Panic on staff", true,
        "GhostMode killswitch on any hit"));
    private final Setting.Bool autoLogout = add(new Setting.Bool("Logout on staff", false,
        "Hard disconnect instead of just disabling modules"));
    private final Setting.Bool quiet = add(new Setting.Bool("Quiet (no sound)", false));

    private static final long VISIBLE_WINDOW_MS = 15_000;

    private final Set<String> staff = new HashSet<>(List.of(
        // DonutSMP staff — synced/updated by the bot; hardcoded seed list:
        "DrDonut", "OnlyVG", "Wemmbu"
    ));
    private final Set<String> alarmed = new HashSet<>();
    private final Set<UUID> prevListed = new HashSet<>();
    /** uuid → name of players rendered within the sliding window. */
    private final Map<UUID, String> visibleNames = new HashMap<>();
    private final Map<UUID, Long> visibleAt = new HashMap<>();
    private boolean primed;

    public StaffNotifier() { super("StaffNotifier", "Alerts when staff are online", Category.DONUTSMP); }

    public void updateStaffList(List<String> names) { staff.clear(); staff.addAll(names); }

    @Override protected void onDisable() {
        prevListed.clear(); visibleNames.clear(); visibleAt.clear(); primed = false;
    }

    @Override public String status() {
        return staff.size() + " names · " + visibleNames.size() + " seen";
    }

    @Override
    public void onTick() {
        if (mc.getConnection() == null || mc.player == null || mc.level == null) return;
        long now = System.currentTimeMillis();

        // sliding window: players the client actually rendered recently
        for (var e : mc.level.entitiesForRendering())
            if (e instanceof Player p && p != mc.player) {
                visibleNames.put(p.getUUID(), p.getName().getString());
                visibleAt.put(p.getUUID(), now);
            }
        visibleAt.entrySet().removeIf(en -> now - en.getValue() > VISIBLE_WINDOW_MS);
        visibleNames.keySet().retainAll(visibleAt.keySet());

        Set<UUID> listed = new HashSet<>();
        for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
            UUID id = info.getProfile().id();
            listed.add(id);
            String name = info.getProfile().name();

            if (nameWatch.on() && staff.contains(name) && alarmed.add("n:" + name))
                hit("STAFF ONLINE: " + name);

            if (gamemodeWatch.on() && !id.equals(mc.player.getUUID())) {
                GameType gm = info.getGameMode();
                if (gm == GameType.SPECTATOR && alarmed.add("s:" + id))
                    hit(name + " is SPECTATING — likely staff watching");
                else if (gm == GameType.CREATIVE && alarmed.add("c:" + id))
                    hit(name + " is in CREATIVE — staff");
            }
        }

        // vanish: was listed AND recently rendered → now gone from tab
        if (vanishWatch.on() && primed)
            for (UUID gone : prevListed)
                if (!listed.contains(gone)) {
                    String seen = visibleNames.get(gone);
                    if (seen != null && alarmed.add("v:" + gone))
                        hit(seen + " VANISHED while in sight — /v suspected");
                }

        prevListed.clear();
        prevListed.addAll(listed);
        primed = true;
    }

    private void hit(String what) {
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§c§l[NOX] " + what));
        Toasts.push("⚠ " + what, 0xFFFF5555);
        if (!quiet.on())
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL, 1.0f));
        if (autoLogout.on()) { mc.disconnectWithProgressScreen(); return; }
        if (autoPanic.on()) panic();
    }

    /** GhostMode kill-switch: kills flaggable modules, passive watchers stay. */
    private void panic() {
        Module ghost = NoxClient.INSTANCE.modules.get("GhostMode");
        if (ghost != null) {
            ghost.setEnabled(true);   // trigger: disables everything not on KEEP
        } else {
            for (Module m : NoxClient.INSTANCE.modules.all())
                if (m != this && (m.category == Category.COMBAT
                    || m.category == Category.MOVEMENT
                    || m.category == Category.DONUTSMP)) m.setEnabled(false);
        }
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(
            "§6[NOX] Panic: flaggable modules disabled (passive detection stays on)."));
    }
}
