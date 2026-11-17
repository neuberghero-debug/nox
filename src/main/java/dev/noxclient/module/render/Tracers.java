package dev.noxclient.module.render;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.module.misc.Friends;
import dev.noxclient.render.NoxRender;
import dev.noxclient.setting.Setting;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Tracers — through-wall lines/arrows from the camera to entities.
 * Per-type colors, friend highlighting, distance fade, optional name tags.
 * All gizmo-based: zero custom pipeline, zero packets.
 */
public class Tracers extends Module {
    public final Setting.Bool players = add(new Setting.Bool("Players", true));
    private final Setting.Bool hostiles = add(new Setting.Bool("Hostiles", false));
    private final Setting.Bool passives = add(new Setting.Bool("Passives", false));
    private final Setting.Bool items = add(new Setting.Bool("Items", false));
    private final Setting.Bool friends = add(new Setting.Bool("Friends", true,
        "Green lines to friended players"));
    private final Setting.Mode style = add(Setting.Mode.of("Style", "Arrow",
        "Line shape", "Arrow", "Line", "Both"));
    private final Setting.Bool nameTags = add(new Setting.Bool("Name tags", false));
    private final Setting.Bool fade = add(new Setting.Bool("Distance fade", true));
    public final Setting.Num maxDist = add(new Setting.Num("Max distance", 256, 16, 1024));
    private final Setting.Num width = add(new Setting.Num("Width", 1.5, 0.5, 6));
    public final Setting.Col playerCol = add(new Setting.Col("Player color", 0xFFFFAA00));
    private final Setting.Col hostileCol = add(new Setting.Col("Hostile color", 0xFFFF5555));
    private final Setting.Col passiveCol = add(new Setting.Col("Passive color", 0xFF5AC8FF));
    private final Setting.Col itemCol = add(new Setting.Col("Item color", 0xFF55E0FF));
    private final Setting.Col friendCol = add(new Setting.Col("Friend color", 0xFF4ADE80));

    private NoxRender.Emitter emitter;

    public Tracers() { super("Tracers", "Lines to entities", Category.RENDER); }

    @Override protected void onEnable() { emitter = this::emit; NoxRender.add(emitter); }
    @Override protected void onDisable() { NoxRender.remove(emitter); }
    @Override public String status() { return style.get() + " · " + maxDist.intVal() + "m"; }

    private void emit() {
        if (mc.level == null || mc.player == null) return;
        Vec3 from = mc.gameRenderer.mainCamera().position();
        double max2 = maxDist.get() * maxDist.get();
        float w = width.floatVal();
        boolean arrows = !"Line".equals(style.get());
        boolean lines = !"Arrow".equals(style.get());

        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player || !e.isAlive()) continue;
            int col = colorFor(e);
            if (col == 0) continue;
            Vec3 to = e.getBoundingBox().getCenter();
            double d2 = from.distanceToSqr(to);
            if (d2 > max2) continue;

            if (fade.on()) {
                double t = Math.min(1.0, Math.sqrt(d2) / maxDist.get());
                int a = (int) (0xFF - t * (0xFF - 0x38));
                col = (col & 0xFFFFFF) | (a << 24);
            }
            if (lines) Gizmos.line(from, to, col, w).setAlwaysOnTop();
            if (arrows) Gizmos.arrow(from, to, col, w).setAlwaysOnTop();
            if (nameTags.on())
                Gizmos.billboardText(e.getName().getString(),
                        to.add(0, e.getBbHeight() / 2 + 0.6, 0),
                        TextGizmo.Style.forColorAndCentered(col).withScale(1.1f))
                    .setAlwaysOnTop();
        }
    }

    private int colorFor(Entity e) {
        if (e instanceof Player p) {
            if (friends.on() && Friends.isFriend(p)) return friendCol.get();
            return players.on() ? playerCol.get() : 0;
        }
        if (e instanceof Enemy) return hostiles.on() ? hostileCol.get() : 0;
        if (e instanceof ItemEntity) return items.on() ? itemCol.get() : 0;
        if (e instanceof Animal || e instanceof AmbientCreature)
            return passives.on() ? passiveCol.get() : 0;
        return 0;
    }
}
