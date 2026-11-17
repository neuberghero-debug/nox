package dev.noxclient.module.render;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Scoreboard — restyles or hides the vanilla sidebar. "Styled" renders the
 * same data through the NOX HUD (glass card, accent edge, animated values,
 * free position via the HUD editor). "Hidden" suppresses it entirely.
 */
public class Scoreboard extends Module {
    public final Setting.Mode mode = add(new Setting.Mode("Mode", "Styled", "Vanilla", "Styled", "Hidden"));
    public final Setting.Bool title = add(new Setting.Bool("Title", true));
    public final Setting.Bool numbers = add(new Setting.Bool("Numbers", true));
    public final Setting.Num maxLines = add(new Setting.Num("Max Lines", 15, 3, 30));

    public Scoreboard() { super("Scoreboard", "Restyle or hide the sidebar", Category.RENDER); }

    /** True when the vanilla sidebar should be cancelled (Styled or Hidden). */
    public boolean replaces() { return isEnabled() && !mode.get().equals("Vanilla"); }

    public record Line(String text, String value) {}

    /** Current sidebar content as label/value pairs (empty when absent). */
    public List<Line> lines() {
        List<Line> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return out;
        var sb = mc.level.getScoreboard();
        Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (obj == null) return out;
        int max = (int) (double) maxLines.get();
        for (PlayerScoreEntry e : sb.listPlayerScores(obj)) {
            if (e.isHidden() || out.size() >= max) continue;
            // DonutSMP packs the visible text into the team prefix
            String text = null;
            var team = sb.getPlayersTeam(e.owner());
            if (team != null) text = team.getPlayerPrefix().getString();
            if (text == null || text.isBlank()) text = e.ownerName().getString();
            text = text.replaceAll("§.", "").trim();
            String val = numbers.on() ? String.valueOf(e.value()) : "";
            out.add(new Line(text, val));
        }
        return out;
    }

    /** Sidebar title text, or "" when the title setting is off/none. */
    public String title() {
        if (!title.on()) return "";
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return "";
        var obj = mc.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        return obj == null ? "" : obj.getDisplayName().getString().replaceAll("§.", "").trim();
    }
}
