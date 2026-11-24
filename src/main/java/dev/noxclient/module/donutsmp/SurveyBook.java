package dev.noxclient.module.donutsmp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;

/** Bounded session notebook of first-hit block observations, not a world scanner. */
final class SurveyBook {
    enum Family { STORAGE, MACHINERY, WORKSTATION, LIGHT }
    record Point(int x, int y, int z) {
        double distance2(Point other) {
            double dx = (double) x - other.x, dy = (double) y - other.y, dz = (double) z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }
    record Sign(Family family, boolean distinctive) {}
    record Finding(Point anchor, int blocks, List<Family> families, long latest) {}
    private record Seen(Sign sign, long tick) {}
    private static final int MAX_POINTS = 256;
    private static final Pattern HOST = Pattern.compile(
        "(?i)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)*donutsmp\\.net\\.?(?::([0-9]{1,5}))?");
    private final LinkedHashMap<Point, Seen> seen = new LinkedHashMap<>();

    static boolean acceptsServer(String address) {
        if (address == null) return false;
        var match = HOST.matcher(address.strip());
        if (!match.matches()) return false;
        String port = match.group(1);
        return port == null || (Integer.parseInt(port) > 0 && Integer.parseInt(port) <= 65535);
    }

    void clear() { seen.clear(); }

    void observe(Point point, Sign sign, long tick) {
        if (sign == null) { seen.remove(point); return; }
        seen.put(point, new Seen(sign, tick));
        if (seen.size() > MAX_POINTS) {
            var oldest = seen.entrySet().stream().min(Comparator.comparingLong(e -> e.getValue().tick()));
            oldest.ifPresent(e -> seen.remove(e.getKey()));
        }
    }

    private static final class Site {
        final Point anchor;
        final EnumSet<Family> kinds = EnumSet.noneOf(Family.class);
        int count;
        boolean distinctive;
        long latest;
        Site(Point anchor) { this.anchor = anchor; }
    }

    List<Finding> candidates(long tick, int lifetime, int radius, EnumSet<Family> enabled,
                             int minBlocks, int minFamilies, boolean requireDistinctive) {
        seen.values().removeIf(s -> tick - s.tick() >= lifetime);
        var sites = new ArrayList<Site>();
        for (var entry : seen.entrySet()) {
            var point = entry.getKey();
            var s = entry.getValue();
            if (!enabled.contains(s.sign().family())) continue;
            // Group by distance to the original anchor, not to the last member. A row
            // of torches must not bridge unrelated sites; chunk boundaries don't matter.
            Site nearest = null;
            double best = (double) radius * radius;
            for (var site : sites) {
                double d = point.distance2(site.anchor);
                if (d <= best && (nearest == null || d < best)) { nearest = site; best = d; }
            }
            if (nearest == null) { nearest = new Site(point); sites.add(nearest); }
            nearest.count++;
            nearest.kinds.add(s.sign().family());
            nearest.distinctive |= s.sign().distinctive();
            nearest.latest = Math.max(nearest.latest, s.tick());
        }
        var results = new ArrayList<Finding>();
        for (var site : sites) {
            if (site.count >= minBlocks && site.kinds.size() >= minFamilies
                    && (!requireDistinctive || site.distinctive)) {
                results.add(new Finding(site.anchor, site.count, List.copyOf(site.kinds), site.latest));
            }
        }
        results.sort(Comparator.comparingLong(Finding::latest).reversed());
        return results;
    }
}
