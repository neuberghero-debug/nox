package dev.noxclient.module.render;

import net.minecraft.world.phys.Vec3;

public final class BreadcrumbsTest {
    public static void main(String[] args) {
        var route = new Breadcrumbs.Route();
        route.sample(pos(0), true, 3, 2);
        route.sample(pos(2), true, 3, 2);
        check(route.points.size() == 1, "spacing rejects nearby sample");
        route.sample(pos(3), true, 3, 2);
        check(route.points.getLast().connected(), "ordinary movement connects");
        route.sample(pos(100), false, 3, 2);
        check(route.points.getLast().pos().equals(pos(3)), "skipped airborne sample not retained");
        route.sample(pos(4), false, 3, 2);
        route.sample(pos(4), true, 3, 2);
        check(route.points.size() == 2 && route.points.getFirst().pos().equals(pos(3)), "bounded trail evicts oldest point");
        check(!route.points.getLast().connected(), "jump in skipped samples breaks the line even near the old point");
        route.gap();
        route.sample(pos(5), true, 3, 2);
        check(route.points.getLast().pos().equals(pos(5)) && !route.points.getLast().connected(), "explicit gap starts a new segment");
        route.clear();
        check(route.points.isEmpty(), "world switch clears old points");
        route.sample(pos(6), true, 3, 2);
        check(!route.points.getFirst().connected(), "first point in a world has no incoming segment");
        System.out.println("Breadcrumb checks passed: spacing, capacity, discontinuities, and reset.");
    }

    private static Vec3 pos(double x) { return new Vec3(x, 64, 0); }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}
