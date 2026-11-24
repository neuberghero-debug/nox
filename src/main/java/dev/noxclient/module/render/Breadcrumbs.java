package dev.noxclient.module.render;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.render.NoxRender;
import dev.noxclient.setting.Setting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;

/** Records and displays the route the local player has actually travelled. */
public class Breadcrumbs extends Module {
    public final Setting.Num maxPoints = add(new Setting.Num("Max points", 200, 50, 1000));
    public final Setting.Num spacing = add(new Setting.Num("Spacing", 3, 1, 10));
    private final Setting.Bool airborne = add(new Setting.Bool("Record in air", true,
        "Include paths flown with an elytra"));
    private final Setting.Num renderDistance = add(new Setting.Num("Draw distance", 96, 16, 256));
    private final Route route = new Route();
    private final NoxRender.Emitter emitter = this::emit;
    private ClientLevel level;
    private LocalPlayer lastPlayer;

    public Breadcrumbs() { super("Breadcrumbs", "Shows your recent route on foot or in flight", Category.RENDER); }

    @Override protected void onEnable() { NoxRender.add(emitter); }

    @Override protected void onDisable() {
        NoxRender.remove(emitter);
        level = null;
        lastPlayer = null;
        route.clear();
    }

    @Override public void onTick() {
        if (level != mc.level) {
            route.clear();
            level = mc.level;
        }
        var player = mc.player;
        if (lastPlayer != player) {
            route.gap();
            lastPlayer = player;
        }
        if (level == null || player == null || !player.isAlive()) {
            route.gap();
            return;
        }
        route.sample(player.position().add(0, 0.12, 0), airborne.on() || player.onGround(),
            spacing.get(), maxPoints.intVal());
    }

    @Override public String status() { return isEnabled() ? route.points.size() + " route points" : "off"; }

    private void emit() {
        if (level == null || level != mc.level || mc.player == null || mc.gui.hud.isHidden()) return;
        Vec3 eye = mc.gameRenderer.mainCamera().position();
        double distance = renderDistance.get();
        double max2 = distance * distance;
        int i = 0, count = route.points.size();
        Route.Point prev = null;
        for (var point : route.points) {
            if (prev != null && point.connected()
                && Math.min(eye.distanceToSqr(prev.pos()), eye.distanceToSqr(point.pos())) <= max2) {
                int alpha = 64 + 191 * i / (count - 1);
                Gizmos.line(prev.pos(), point.pos(), (alpha << 24) | 0xFFD870, 2f);
            }
            prev = point;
            i++;
        }
    }

    static final class Route {
        private static final double MAX_SEGMENT_SQR = 32 * 32;
        record Point(Vec3 pos, boolean connected) {}
        final Deque<Point> points = new ArrayDeque<>();
        private Vec3 previousSample;
        private boolean breakPending = true;

        void sample(Vec3 pos, boolean record, double spacing, int limit) {
            if (previousSample != null && pos.distanceToSqr(previousSample) > MAX_SEGMENT_SQR)
                breakPending = true;
            previousSample = pos;
            if (record) {
                var last = points.peekLast();
                double d2 = last == null ? Double.POSITIVE_INFINITY : pos.distanceToSqr(last.pos());
                if (breakPending || d2 >= spacing * spacing) {
                    points.addLast(new Point(pos, !breakPending && d2 <= MAX_SEGMENT_SQR));
                    breakPending = false;
                }
            }
            while (points.size() > limit) points.removeFirst();
        }

        void gap() {
            previousSample = null;
            breakPending = true;
        }

        void clear() {
            points.clear();
            gap();
        }
    }
}
