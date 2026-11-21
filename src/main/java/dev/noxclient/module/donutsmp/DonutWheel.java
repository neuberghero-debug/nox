package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * DonutWheel — radial action menu (bind it to a Hold-mode key):
 * hold the key to open the wheel, point with the mouse, release to run.
 * Slots: "Name|action" — action is
 *   toggle:Module    (flip a module)
 *   cmd:/sell hand   (run a server command)
 *   say:text         (chat message)
 *   toggle: off      (empty slot skipped)
 */
public class DonutWheel extends Module {
    public final Setting.Str s1 = add(new Setting.Str("Slot 1", "Sell hand|cmd:/sell hand"));
    public final Setting.Str s2 = add(new Setting.Str("Slot 2", "Sell all|cmd:/sell all"));
    public final Setting.Str s3 = add(new Setting.Str("Slot 3", "Panic|toggle:Panic"));
    public final Setting.Str s4 = add(new Setting.Str("Slot 4", "AutoSell|toggle:AutoSell"));
    public final Setting.Str s5 = add(new Setting.Str("Slot 5", "AutoScout|toggle:AutoScout"));
    public final Setting.Str s6 = add(new Setting.Str("Slot 6", "BaseFinder|toggle:BaseFinder"));
    public final Setting.Str s7 = add(new Setting.Str("Slot 7", "StorageESP|toggle:StorageESP"));
    public final Setting.Str s8 = add(new Setting.Str("Slot 8", "Ghost|toggle:GhostMode"));

    public long openedAt;
    /** HUD-scale-space dims the overlay drew the wheel in — set each frame. */
    public int vw = -1, vh = -1;

    public record Act(String name, String kind, String arg) {}

    public DonutWheel() { super("DonutWheel", "Radial quick-action wheel", Category.DONUTSMP); }

    public List<Act> actions() {
        var out = new ArrayList<Act>();
        for (var s : new Setting.Str[]{s1, s2, s3, s4, s5, s6, s7, s8}) {
            String v = s.get().trim();
            int bar = v.indexOf('|');
            if (bar < 0) continue;
            String name = v.substring(0, bar).trim();
            String spec = v.substring(bar + 1).trim();
            int col = spec.indexOf(':');
            if (col < 0 || name.isEmpty()) continue;
            out.add(new Act(name, spec.substring(0, col).trim().toLowerCase(),
                spec.substring(col + 1).trim()));
        }
        return out;
    }

    /** Sector under the cursor — angle from screen center (w,h = overlay space). */
    public int selected(Minecraft mc, int w, int h) {
        double gw = mc.getWindow().getGuiScaledWidth();
        double gh = mc.getWindow().getGuiScaledHeight();
        // mouse px → unscaled gui → overlay space (matches the draw transform)
        double mx = mc.mouseHandler.xpos() / mc.getWindow().getGuiScale() * w / gw;
        double my = mc.mouseHandler.ypos() / mc.getWindow().getGuiScale() * h / gh;
        double dx = mx - w / 2.0, dy = my - h / 2.0;
        if (dx * dx + dy * dy < 26 * 26) return -1;   // dead zone = no pick
        var acts = actions();
        if (acts.isEmpty()) return -1;
        double a = Math.atan2(dy, dx) + Math.PI / 2;
        int n = acts.size();
        int i = (int) Math.floor(((a + Math.PI * 2) % (Math.PI * 2)) / (Math.PI * 2 / n));
        return Math.min(i, n - 1);
    }

    @Override protected void onEnable() { openedAt = System.currentTimeMillis(); }

    @Override protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        var acts = actions();
        int sel = selected(mc, vw > 0 ? vw : mc.getWindow().getGuiScaledWidth(),
            vh > 0 ? vh : mc.getWindow().getGuiScaledHeight());
        if (sel < 0 || sel >= acts.size()) return;
        var a = acts.get(sel);
        switch (a.kind()) {
            case "toggle" -> {
                var m = dev.noxclient.NoxClient.INSTANCE.modules.get(a.arg());
                if (m != null) m.setEnabled(!m.isEnabled());
                else if (mc.player != null) mc.player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("§7[NOX] §cno module " + a.arg()));
            }
            case "cmd" -> {
                if (mc.getConnection() != null)
                    mc.getConnection().sendCommand(
                        a.arg().startsWith("/") ? a.arg().substring(1) : a.arg());
            }
            case "say" -> {
                if (mc.getConnection() != null) mc.getConnection().sendChat(a.arg());
            }
        }
    }
}
