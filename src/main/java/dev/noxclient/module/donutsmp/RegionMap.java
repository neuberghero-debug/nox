package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

import java.util.HashMap;
import java.util.Map;

/**
 * RegionMap — DonutSMP shard grid HUD. The world is a cols×rows grid of
 * ~50k cells, each owned by a server cluster (EU Central / EU West /
 * NA West / NA East / Asia / Oceania). Cells show their region id and are
 * colored by cluster; the player's current cell is highlighted.
 * Cluster assignment is a zone approximation — paste the real layout via
 * the "clusters" overrides ("cx,cz:Cluster" pairs, e.g. "-2,-3:NA East").
 */
public class RegionMap extends Module {
    public final Setting.Num cols = add(new Setting.Num("Columns", 9, 3, 20));
    public final Setting.Num rows = add(new Setting.Num("Rows", 9, 3, 20));
    public final Setting.Num cellSize = add(new Setting.Num("Cell size (k)", 50, 10, 200));
    public final Setting.Num cellPx = add(new Setting.Num("Cell px", 11, 6, 20));
    public final Setting.Num idBase = add(new Setting.Num("First region id", 1, 0, 500));
    public final Setting.Str overrides = add(new Setting.Str("Cluster overrides", "",
        "cx,cz:Cluster per cell, e.g. \"-2,-3:NA East|1,2:Oceania\""));

    private static final String[] NAMES =
        {"EU Central", "NA West", "EU West", "Asia", "NA East", "Oceania"};
    private static final int[] COLS =
        {0xFFFF8C42, 0xFF5DD66E, 0xFF4ADED0, 0xFFFFD870, 0xFF4A90E2, 0xFF3A4A6B};
    private Map<String, Integer> ovCache; private String ovSrc = null;

    public RegionMap() { super("RegionMap", "DonutSMP shard grid map", Category.DONUTSMP); }

    /** Region id for grid cell (cx,cz relative to center); -1 outside. */
    public int regionId(int cx, int cz) {
        int c = cx + cols.intVal() / 2, r = cz + rows.intVal() / 2;
        if (c < 0 || c >= cols.intVal() || r < 0 || r >= rows.intVal()) return -1;
        return idBase.intVal() + r * cols.intVal() + c;
    }

    private int clusterIdx(int cx, int cz) {
        parseOv();
        Integer ov = ovCache.get(cx + "," + cz);
        if (ov != null) return ov;
        // zone approximation of the live cluster layout:
        // EU Central occupies the north-west quadrant, Asia north-east,
        // NA East south-west corner, Oceania south-east corner,
        // EU West the west band, NA West the remainder.
        int hc = cols.intVal() / 2, hr = rows.intVal() / 2;
        if (cz <= -hr + 2 && cx <= 0) return 0;                       // EU Central
        if (cz <= -hr + 2 && cx > 0) return 3;                        // Asia
        if (cz >= hr - 2 && cx <= -hc + 2) return 4;                  // NA East
        if (cz >= hr - 2 && cx >= hc - 2) return 5;                   // Oceania
        if (cx <= -1) return 2;                                       // EU West
        return 1;                                                     // NA West
    }

    public String clusterName(int cx, int cz) {
        if (regionId(cx, cz) < 0) return "—";
        return NAMES[clusterIdx(cx, cz)];
    }

    public int clusterColor(int cx, int cz) {
        if (regionId(cx, cz) < 0) return 0xFF22222E;
        return COLS[clusterIdx(cx, cz)];
    }

    private void parseOv() {
        String s = overrides.get();
        if (s.equals(ovSrc)) return;
        ovSrc = s;
        ovCache = new HashMap<>();
        for (String pair : s.split("\\|")) {
            int colon = pair.indexOf(':'); if (colon < 0) continue;
            String name = pair.substring(colon + 1).trim();
            int idx = -1;
            for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equalsIgnoreCase(name)) idx = i;
            if (idx < 0) continue;
            String[] xy = pair.substring(0, colon).split(",");
            if (xy.length != 2) continue;
            try { ovCache.put(Integer.parseInt(xy[0].trim()) + "," + Integer.parseInt(xy[1].trim()), idx); }
            catch (NumberFormatException ignored) {}
        }
    }
}
