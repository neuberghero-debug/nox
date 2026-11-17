package dev.noxclient.gui;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;

/**
 * ModuleIcons — filled vector glyphs for every module, drawn from rounded
 * primitives (pill/circle/round + pose rotation). No dingbats, no pixel art.
 *
 * Usage: ModuleIcons.draw(g, ModuleIcons.forModule(m), cx, cy, size, rgb, t, on, hover)
 *   t    = seconds (drives per-icon idle animation; pass 0 for reducedMotion)
 *   on   = module enabled (icons animate subtly when live)
 *   hover= card hover (icons react — tilt, blink, sweep)
 */
public final class ModuleIcons {
    private ModuleIcons() {}

    public enum Kind {
        SWORD, BOW, SHIELD, TOTEM, CRYSTAL, ANCHOR, BED, PEARL,
        ELYTRA, ROCKET, BOOTS, WIND, STEP,
        EYE, BOX, SCAN, CHIP, TRACER,
        CHEST, COIN, CART, CHART, CLOCK, HEART, FOOD, POTION,
        WEB, TRAP, SKULL, GHOST, PIN, CHAT, NOTE, WRENCH, ZAP, STAR,
        FISH, HOE, PICK, BUCKET, FIRE, DROP, GRID, DONUT, GEAR, POWER,
        AURA, LOCK, BOOK, SHOP, PAUSE, BELL
    }

    /** Keyword → kind map. First hit wins; falls back to the category icon. */
    public static Kind forModule(Module m) {
        String n = m.name.toLowerCase(Locale.ROOT);
        Kind k =
            hit(n, "killaura", "triggerbot", "autosword", "combat") ? Kind.SWORD :
            hit(n, "totem") ? Kind.TOTEM :
            hit(n, "crystal", "anchor") ? Kind.CRYSTAL :
            hit(n, "shield") ? Kind.SHIELD :
            hit(n, "wtap", "sprint", "speed", "bhop", "bunnyhop", "autojump", "step", "strafe") ? Kind.WIND :
            hit(n, "flight", "fly", "elytra", "rocket", "glide") ? Kind.ELYTRA :
            hit(n, "nofall", "antivoid", "safewalk", "mlg", "scaffold", "fastladder") ? Kind.BOOTS :
            hit(n, "noslow", "inventorymove", "walk", "sneak") ? Kind.BOOTS :
            hit(n, "esp", "nametag", "playeresp", "storageesp", "oreesp", "holeesp", "hitboxes") ? Kind.EYE :
            hit(n, "tracer", "breadcrumb") ? Kind.TRACER :
            hit(n, "fullbright", "light") ? Kind.ZAP :
            hit(n, "sky", "weather", "time") ? Kind.DROP :
            hit(n, "zoom", "camera", "fov", "freecam") ? Kind.EYE :
            hit(n, "finder", "oresim", "chunk", "scan", "stash", "soundfind", "antiesp", "rtpb") ? Kind.SCAN :
            hit(n, "ah", "auction", "sell", "buy", "stonks", "trade", "order") ? Kind.COIN :
            hit(n, "autosell", "shop") ? Kind.CART :
            hit(n, "rtp", "tp", "teleport", "waypoint", "death") ? Kind.PIN :
            hit(n, "staff", "vanish", "radar", "visualrange", "anticheat", "opsec") ? Kind.SCAN :
            hit(n, "log", "panic", "ghost") ? Kind.GHOST :
            hit(n, "web", "trap", "surround", "burrow") ? Kind.WEB :
            hit(n, "eat", "food", "gap", "soup") ? Kind.FOOD :
            hit(n, "pot", "potion") ? Kind.POTION :
            hit(n, "farm", "harvest", "breed", "shear", "crop") ? Kind.HOE :
            hit(n, "mine", "nuker", "break", "tool", "place") ? Kind.PICK :
            hit(n, "fish") ? Kind.FISH :
            hit(n, "xp", "enchant") ? Kind.BOOK :
            hit(n, "chat", "spam", "twitch", "voice", "commander", "login") ? Kind.CHAT :
            hit(n, "spotify", "lyric", "music", "media") ? Kind.NOTE :
            hit(n, "timer", "delay", "afk", "reconnect", "daily", "sleep") ? Kind.CLOCK :
            hit(n, "armor", "durab", "alert", "notify", "alarm") ? Kind.BELL :
            hit(n, "health", "heal") ? Kind.HEART :
            hit(n, "friend") ? Kind.STAR :
            hit(n, "interface", "hud", "gui", "theme", "click") ? Kind.GRID :
            hit(n, "cosmetic", "cape", "skin") ? Kind.AURA :
            hit(n, "key", "lock", "premium", "license") ? Kind.LOCK :
            hit(n, "blink", "velocity", "aim", "reach") ? Kind.ZAP :
            hit(n, "chest", "steal", "refill", "kit") ? Kind.CHEST :
            hit(n, "pearl") ? Kind.PEARL :
            hit(n, "bed", "respawn") ? Kind.BED :
            hit(n, "drop", "bucket") ? Kind.BUCKET :
            hit(n, "fire", "burn") ? Kind.FIRE :
            hit(n, "kill", "aura") ? Kind.SWORD :
            hit(n, "target") ? Kind.TRACER :
            hit(n, "record", "stats", "session") ? Kind.CHART :
            null;
        if (k != null) return k;
        return forCategory(m.category);
    }

    private static boolean hit(String n, String... keys) {
        for (String k : keys) if (n.contains(k)) return true;
        return false;
    }

    public static Kind forCategory(Category c) {
        return switch (c) {
            case COMBAT -> Kind.SWORD;
            case MOVEMENT -> Kind.WIND;
            case RENDER -> Kind.EYE;
            case DONUTSMP -> Kind.DONUT;
            case MISC -> Kind.GEAR;
        };
    }

    /** Sidebar topic icons (All / Enabled / Favorites / Recent). */
    public static Kind forTopic(String topic) {
        return switch (topic) {
            case "All" -> Kind.GRID;
            case "Enabled" -> Kind.POWER;
            case "Favorites" -> Kind.STAR;
            case "Recent" -> Kind.CLOCK;
            default -> Kind.GRID;
        };
    }

    // ================= drawing =================

    /**
     * Draw glyph `kind` centered at (cx,cy), `s` = icon size in px (12–16 sweet).
     * All shapes are filled silhouettes — reads clean at small sizes.
     */
    public static void draw(GuiGraphicsExtractor g, Kind k, int cx, int cy,
                            int s, int rgb, float t, boolean on, boolean hover) {
        float u = s / 16f;                     // unit scale — icons authored on 16px grid
        int col = rgb | 0xFF000000;
        int dim = (rgb & 0xFEFEFE) >>> 1 | 0xFF000000;   // 50% shade for depth bits
        switch (k) {
            case SWORD -> {
                seg(g, cx - u, cy - u, 11 * u, 3 * u, -0.785f, col);           // blade
                seg(g, cx - 5 * u, cy + 5 * u, 5 * u, 2 * u, 0.785f, col);     // guard
                Gui.circle(g, cx - 6 * u, cy + 6 * u, 1.6f * u, dim);          // pommel
            }
            case BOW -> {
                arc(g, cx + 2 * u, cy, 5.5f * u, -1.4f, 1.4f, 1.6f, col);
                seg(g, cx + 2 * u, cy, 11 * u, 0.9f, 0f, dim);                 // string
                seg(g, cx - 2 * u, cy, 7 * u, 1.1f, 0f, col);                  // arrow
            }
            case SHIELD -> {
                path(g, cx, cy, s, col, new float[][]{
                    {0, -7}, {6, -4}, {6, 1}, {3, 5}, {0, 7}, {-3, 5}, {-6, 1}, {-6, -4}});
            }
            case TOTEM -> {
                Gui.circle(g, cx, cy - 4 * u, 3 * u, col);
                path(g, cx, cy + 3 * u, s, col, new float[][]{
                    {-4, -1}, {-2, -3}, {2, -3}, {4, -1}, {3, 3}, {-3, 3}});
                Gui.circle(g, cx - u, cy - 4.5f * u, 0.8f * u, dim);
            }
            case CRYSTAL -> {
                path(g, cx, cy, s, col, new float[][]{
                    {0, -7}, {5, -1}, {0, 7}, {-5, -1}});
                path(g, cx, cy - u, s, dim, new float[][]{
                    {0, -3}, {2.2f, -0.5f}, {0, 3}, {-2.2f, -0.5f}});
            }
            case ANCHOR -> {
                Gui.round(g, cx - 4 * u, cy - 6 * u, 8 * u, 9 * u, 2 * u, col);
                Gui.circle(g, cx, cy - 3 * u, 1.2f * u, dim);
                Gui.circle(g, cx, cy + 5 * u, 1.4f * u, col);
            }
            case BED -> {
                Gui.round(g, cx - 7 * u, cy - u, 14 * u, 6 * u, 2 * u, col);
                Gui.round(g, cx - 7 * u, cy - 4 * u, 4 * u, 3 * u, 1.5f * u, col);
                Gui.pill(g, cx - 7 * u, cy + 5 * u, 2 * u, 2.5f * u, dim);
                Gui.pill(g, cx + 5 * u, cy + 5 * u, 2 * u, 2.5f * u, dim);
            }
            case PEARL -> Gui.circle(g, cx, cy, 5.5f * u, col);
            case ELYTRA -> {
                float flap = on && !UICustom.reducedMotion
                    ? 0.15f * (float) Math.sin(t * 6f) : 0f;
                wing(g, cx, cy, u, col, -1, flap);
                wing(g, cx, cy, u, col, 1, -flap);
            }
            case ROCKET -> {
                path(g, cx, cy - u, s, col, new float[][]{
                    {0, -6}, {3, -1}, {3, 3}, {0, 1}, {-3, 3}, {-3, -1}});
                Gui.circle(g, cx, cy - 2 * u, 1.1f * u, dim);
                path(g, cx, cy + 4 * u, s, dim, new float[][]{{0, 0}, {1.6f, 3}, {0, 2}, {-1.6f, 3}});
            }
            case BOOTS -> {
                path(g, cx - 3 * u, cy, s, col, new float[][]{
                    {-1.5f, -6}, {0.5f, -6}, {0.5f, 1}, {4, 1}, {4, 4}, {-1.5f, 4}});
            }
            case WIND -> {
                float ph = UICustom.reducedMotion ? 0 : t * 2.6f;
                streak(g, cx - 6 * u, cy - 4 * u, 9 * u, ph, col);
                streak(g, cx - 4 * u, cy, 11 * u, ph + 2, col);
                streak(g, cx - 6 * u, cy + 4 * u, 8 * u, ph + 4, col);
            }
            case STEP -> {
                Gui.round(g, cx - 7 * u, cy + 2 * u, 6 * u, 5 * u, 1.5f * u, col);
                Gui.round(g, cx + 1 * u, cy - 4 * u, 6 * u, 11 * u, 1.5f * u, col);
            }
            case EYE -> {
                float blink = hover ? 0.35f :
                    (!UICustom.reducedMotion && (t % 3.4f) < 0.12f ? 0.35f : 1f);
                path(g, cx, cy, s, col, new float[][]{
                    {-7, 0}, {-3, -4.5f * blink}, {0, -5.5f * blink},
                    {3, -4.5f * blink}, {7, 0}, {3, 4.5f * blink},
                    {0, 5.5f * blink}, {-3, 4.5f * blink}});
                if (blink > 0.8f) Gui.circle(g, cx, cy, 2.4f * u, dim);
            }
            case BOX -> {
                Gui.roundBorder(g, cx - 6 * u, cy - 6 * u, 12 * u, 12 * u, 2 * u, col, 0);
                Gui.circle(g, cx, cy, 1.5f * u, col);
            }
            case SCAN -> {
                Gui.roundBorder(g, cx - 6 * u, cy - 6 * u, 12 * u, 12 * u, 3 * u, dim, 0);
                Gui.circle(g, cx, cy, 1.3f * u, col);
                float sweep = UICustom.reducedMotion ? 0.6f : (t * 1.8f) % 6.283f;
                seg(g, cx, cy, 8.5f * u, 1.4f, sweep, col);
            }
            case CHIP -> {
                Gui.round(g, cx - 5 * u, cy - 5 * u, 10 * u, 10 * u, 2 * u, col);
                Gui.round(g, cx - 2 * u, cy - 2 * u, 4 * u, 4 * u, 1 * u, dim);
            }
            case TRACER -> {
                Gui.circle(g, cx - 4 * u, cy + 4 * u, 2 * u, col);
                seg(g, cx - 2 * u, cy + 2 * u, 8 * u, 1.4f, -0.785f, col);
                arrowHead(g, cx + 4 * u, cy - 4 * u, u, col);
            }
            case CHEST -> {
                Gui.round(g, cx - 6 * u, cy - 4 * u, 12 * u, 9 * u, 1.5f * u, col);
                Gui.round(g, cx - 6 * u, cy - 4 * u, 12 * u, 4 * u, 1.5f * u, dim);
                Gui.round(g, cx - u, cy - u, 2 * u, 3 * u, 1 * u, col);
            }
            case COIN -> {
                Gui.circle(g, cx, cy, 6 * u, col);
                Gui.pill(g, cx - 2.5f * u, cy - 3 * u, 5 * u, 1.4f * u, dim);
                Gui.pill(g, cx - 2.5f * u, cy - 0.7f * u, 5 * u, 1.4f * u, dim);
                Gui.pill(g, cx - 2.5f * u, cy + 1.6f * u, 5 * u, 1.4f * u, dim);
            }
            case CART -> {
                path(g, cx, cy, s, col, new float[][]{
                    {-6, -4}, {4, -4}, {3, 1}, {-5, 1}});
                Gui.circle(g, cx - 3 * u, cy + 4 * u, 1.5f * u, col);
                Gui.circle(g, cx + 2.5f * u, cy + 4 * u, 1.5f * u, col);
                Gui.pill(g, cx - 7 * u, cy - 6 * u, 2 * u, 1.6f * u, col);
            }
            case CHART -> {
                Gui.pill(g, cx - 6 * u, cy - u, 2.5f * u, 7 * u, col);
                Gui.pill(g, cx - u, cy - 4 * u, 2.5f * u, 10 * u, col);
                Gui.pill(g, cx + 4 * u, cy - 6 * u, 2.5f * u, 12 * u, col);
            }
            case CLOCK -> {
                Gui.circle(g, cx, cy, 6 * u, col);
                Gui.pill(g, cx - 0.7f * u, cy - 4 * u, 1.4f * u, 4.5f * u, dim);
                float a = -0.785f + (UICustom.reducedMotion ? 0 : t * 0.8f);
                seg(g, cx, cy, 3.4f * u, 1.2f, a, dim);
            }
            case HEART -> {
                Gui.circle(g, cx - 2.6f * u, cy - 1.6f * u, 3.2f * u, col);
                Gui.circle(g, cx + 2.6f * u, cy - 1.6f * u, 3.2f * u, col);
                path(g, cx, cy + 3 * u, s, col, new float[][]{
                    {-5, -2}, {0, 4}, {5, -2}, {0, 0.6f}});
            }
            case FOOD -> {
                path(g, cx, cy, s, col, new float[][]{
                    {-4, -6}, {4, -4}, {4, 2}, {1, 6}, {-2, 6}, {-4, 2}});
                Gui.circle(g, cx + u, cy - 2 * u, 1 * u, dim);
            }
            case POTION -> {
                Gui.round(g, cx - 4 * u, cy - u, 8 * u, 8 * u, 3 * u, col);
                Gui.pill(g, cx - 1.5f * u, cy - 6 * u, 3 * u, 5 * u, col);
                Gui.circle(g, cx - u, cy + 3 * u, 1 * u, dim);
                Gui.circle(g, cx + 1.6f * u, cy + 2 * u, 0.8f * u, dim);
            }
            case WEB -> {
                for (int i = 0; i < 3; i++) {
                    float a = i * 1.047f;
                    seg(g, cx, cy, 13 * u, 1f, a, col);
                }
                arc(g, cx, cy, 3.6f * u, 0, 6.283f, 1.3f, col);
                arc(g, cx, cy, 2f * u, 0, 6.283f, 1.1f, dim);
            }
            case TRAP -> {
                Gui.round(g, cx - 6 * u, cy - 5 * u, 12 * u, 10 * u, 2 * u, col);
                Gui.pill(g, cx - 1 * u, cy - 8 * u, 2 * u, 4 * u, col);
            }
            case SKULL -> {
                Gui.circle(g, cx, cy - u, 5 * u, col);
                Gui.round(g, cx - 3 * u, cy + 2 * u, 6 * u, 4 * u, 1.5f * u, col);
                Gui.circle(g, cx - 2 * u, cy - u, 1.2f * u, dim);
                Gui.circle(g, cx + 2 * u, cy - u, 1.2f * u, dim);
            }
            case GHOST -> {
                float bob = UICustom.reducedMotion ? 0 : 1f * (float) Math.sin(t * 3f);
                path(g, cx, cy + bob * 0.5f, s, col, new float[][]{
                    {-5, 5}, {-5, -1}, {-3, -5}, {0, -7}, {3, -5}, {5, -1}, {5, 5},
                    {3, 3.5f}, {1.6f, 5}, {0, 3.5f}, {-1.6f, 5}, {-3, 3.5f}});
                Gui.circle(g, cx - 2 * u, cy - 2 * u + bob * 0.5f, 1 * u, dim);
                Gui.circle(g, cx + 2 * u, cy - 2 * u + bob * 0.5f, 1 * u, dim);
            }
            case PIN -> {
                Gui.circle(g, cx, cy - 2 * u, 4.5f * u, col);
                path(g, cx, cy + 4 * u, s, col, new float[][]{{0, 3}, {2.6f, -1}, {-2.6f, -1}});
                Gui.circle(g, cx, cy - 2 * u, 1.6f * u, dim);
            }
            case CHAT -> {
                Gui.round(g, cx - 6 * u, cy - 5 * u, 12 * u, 9 * u, 3 * u, col);
                path(g, cx - 3 * u, cy + 4 * u, s, col, new float[][]{{0, 2.5f}, {2, 0}, {-1, 0}});
            }
            case NOTE -> {
                Gui.pill(g, cx + u, cy - 6 * u, 1.8f * u, 10 * u, col);
                Gui.circle(g, cx - 1.5f * u, cy + 3.6f * u, 2.6f * u, col);
                seg(g, cx + u, cy - 6 * u, 5 * u, 1.6f * u, -0.5f, col);
            }
            case WRENCH -> {
                seg(g, cx + 2 * u, cy + 2 * u, 8 * u, 2.4f * u, -0.785f, col);
                Gui.circle(g, cx - 3.5f * u, cy - 3.5f * u, 3 * u, col);
                Gui.circle(g, cx - 3.5f * u, cy - 3.5f * u, 1.6f * u, dim);
            }
            case ZAP -> {
                float fl = (!UICustom.reducedMotion && on && (t % 1.7f) < 0.09f) ? 0.55f : 1f;
                path(g, cx, cy, s, fl < 1f ? dim : col, new float[][]{
                    {2, -7}, {-4, 1}, {-0.5f, 1}, {-2, 7}, {4, -1}, {0.5f, -1}});
            }
            case STAR -> {
                for (int i = 0; i < 5; i++) {
                    float a = -1.571f + i * 1.257f;
                    seg(g, cx + (float) Math.cos(a) * 2 * u,
                        cy + (float) Math.sin(a) * 2 * u, 5.2f * u, 1.8f * u, a, col);
                }
            }
            case FISH -> {
                path(g, cx - u, cy, s, col, new float[][]{
                    {-5, 0}, {-1.5f, -3}, {4, -3}, {5.5f, 0}, {4, 3}, {-1.5f, 3}});
                path(g, cx + 5 * u, cy, s, col, new float[][]{{1, 0}, {3, -2.5f}, {3, 2.5f}});
                Gui.circle(g, cx - 2.5f * u, cy - u, 0.8f * u, dim);
            }
            case HOE -> {
                seg(g, cx - 1 * u, cy + 2 * u, 11 * u, 1.6f * u, -0.785f, col);
                seg(g, cx + 3.5f * u, cy - 3.5f * u, 5 * u, 1.6f * u, 0.3f, col);
            }
            case PICK -> {
                seg(g, cx - 1 * u, cy + 3 * u, 10 * u, 1.7f * u, -0.785f, col);
                arc(g, cx + 1 * u, cy - 2 * u, 5 * u, -2.6f, 0.4f, 1.8f, col);
            }
            case BUCKET -> {
                path(g, cx, cy, s, col, new float[][]{
                    {-4, -5}, {4, -5}, {3, 6}, {-3, 6}});
                Gui.pill(g, cx - 2.5f * u, cy - 6 * u, 5 * u, 1.5f * u, dim);
            }
            case FIRE -> {
                path(g, cx, cy, s, col, new float[][]{
                    {0, 7}, {-4, 4}, {-5, 0}, {-3, -3}, {-1, -7}, {0, -4},
                    {2, -6}, {4, -2}, {4, 1}, {3, 4}});
            }
            case DROP -> {
                path(g, cx, cy, s, col, new float[][]{
                    {0, -7}, {4, -1}, {4, 2}, {2, 5}, {0, 6}, {-2, 5}, {-4, 2}, {-4, -1}});
                Gui.circle(g, cx - u, cy + 2 * u, 1 * u, dim);
            }
            case GRID -> {
                for (int yy = -1; yy <= 1; yy++)
                    for (int xx = -1; xx <= 1; xx++)
                        Gui.round(g, cx + xx * 4.6f * u - 1.6f * u,
                            cy + yy * 4.6f * u - 1.6f * u, 3.2f * u, 3.2f * u, 1 * u, col);
            }
            case DONUT -> {
                Gui.circle(g, cx, cy, 6 * u, col);
                Gui.circle(g, cx, cy, 2.2f * u, dim);
                Gui.circle(g, cx - 3 * u, cy - 2 * u, 0.8f * u, dim);
                Gui.circle(g, cx + 2.4f * u, cy - 3 * u, 0.8f * u, dim);
                Gui.circle(g, cx + 2 * u, cy + 2.6f * u, 0.8f * u, dim);
            }
            case GEAR -> {
                float rot = (!UICustom.reducedMotion && on) ? t * 0.9f : 0.35f;
                for (int i = 0; i < 8; i++) {
                    float a = rot + i * 0.785f;
                    Gui.circle(g, cx + (float) Math.cos(a) * 5.2f * u,
                        cy + (float) Math.sin(a) * 5.2f * u, 1.5f * u, col);
                }
                Gui.circle(g, cx, cy, 3.4f * u, col);
                Gui.circle(g, cx, cy, 1.4f * u, dim);
            }
            case POWER -> {
                arc(g, cx, cy + u, 5 * u, -2.4f, 0.9f, 1.8f, col);
                Gui.pill(g, cx - 0.9f * u, cy - 7 * u, 1.8f * u, 6 * u, col);
            }
            case AURA -> {
                Gui.circle(g, cx, cy, 2.6f * u, col);
                arc(g, cx, cy, 5 * u, -2.2f, -0.9f, 1.3f, col);
                arc(g, cx, cy, 5 * u, 0.9f, 2.2f, 1.3f, col);
            }
            case LOCK -> {
                Gui.round(g, cx - 4.5f * u, cy - u, 9 * u, 7 * u, 2 * u, col);
                arc(g, cx, cy - 3 * u, 2.8f * u, 3.3f, 6.1f, 1.6f, col);
                Gui.circle(g, cx, cy + 2 * u, 1.2f * u, dim);
            }
            case BOOK -> {
                Gui.round(g, cx - 6 * u, cy - 5 * u, 12 * u, 10 * u, 1.5f * u, col);
                Gui.pill(g, cx - 0.7f * u, cy - 5 * u, 1.4f * u, 10 * u, dim);
            }
            case SHOP -> {
                Gui.round(g, cx - 6 * u, cy - 3 * u, 12 * u, 8 * u, 1.5f * u, col);
                path(g, cx, cy - 5 * u, s, col, new float[][]{{-6, 0}, {0, -2.5f}, {6, 0}});
            }
            case BELL -> {
                float swing = hover && !UICustom.reducedMotion
                    ? 0.25f * (float) Math.sin(t * 8f) : 0f;
                path(g, cx + swing * 3, cy, s, col, new float[][]{
                    {-4.5f, 3}, {4.5f, 3}, {4, 0}, {3, -3}, {1.5f, -5}, {-1.5f, -5}, {-3, -3}, {-4, 0}});
                Gui.circle(g, cx + swing * 3, cy + 4.5f * u, 1.5f * u, col);
            }
            case PAUSE -> {
                Gui.pill(g, cx - 3.4f * u, cy - 5 * u, 2.4f * u, 10 * u, col);
                Gui.pill(g, cx + 1 * u, cy - 5 * u, 2.4f * u, 10 * u, col);
            }
        }
    }

    // ---- primitives ----

    /** Rounded stroke segment centered on (cx,cy), rotated by `ang` rad. */
    private static void seg(GuiGraphicsExtractor g, float cx, float cy,
                            float len, float thick, float ang, int col) {
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().rotate(ang);
        Gui.pill(g, -len / 2, -thick / 2, len, thick, col);
        g.pose().popMatrix();
    }

    /** Convex polygon from unit vertices (16px grid), filled via fan blits. */
    private static void path(GuiGraphicsExtractor g, float cx, float cy, int s,
                             int col, float[][] pts) {
        float u = s / 16f;
        // simple triangulated fill: bounding fan from centroid — convex shapes only
        float mx = 0, my = 0;
        for (float[] p : pts) { mx += p[0]; my += p[1]; }
        mx /= pts.length; my /= pts.length;
        for (int i = 0; i < pts.length; i++) {
            float[] a = pts[i], b = pts[(i + 1) % pts.length];
            tri(g, cx + mx * u, cy + my * u, cx + a[0] * u, cy + a[1] * u,
                cx + b[0] * u, cy + b[1] * u, col);
        }
    }

    /** Filled triangle via GuiGraphicsExtractor's submitBlit-less polygon path —
     * falls back to thin pills along the edges plus a centroid disc, which at
     * icon size (12–16 px) reads as a solid silhouette. */
    private static void tri(GuiGraphicsExtractor g, float x0, float y0,
                            float x1, float y1, float x2, float y2, int col) {
        // rasterize: fill the triangle with horizontal scanline pills
        float ymin = Math.min(y0, Math.min(y1, y2)), ymax = Math.max(y0, Math.max(y1, y2));
        for (float yy = ymin; yy <= ymax; yy += 0.9f) {
            float xa = xAt(y0, x0, y1, x1, yy), xb = xAt(y1, x1, y2, x2, yy),
                  xc = xAt(y2, x2, y0, x0, yy);
            float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (float v : new float[]{xa, xb, xc})
                if (!Float.isNaN(v)) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
            if (hi > lo)
                g.fill((int) lo, (int) yy, (int) Math.ceil(hi), (int) Math.ceil(yy + 0.9f), col);
        }
    }

    private static float xAt(float ya, float xa, float yb, float xb, float y) {
        if (yb == ya) return Float.NaN;
        if (y >= Math.min(ya, yb) && y <= Math.max(ya, yb))
            return xa + (y - ya) * (xb - xa) / (yb - ya);
        return Float.NaN;
    }

    /** Arc approximated by small circles along the path — cheap, AA-clean. */
    private static void arc(GuiGraphicsExtractor g, float cx, float cy, float r,
                            float a0, float a1, float thick, int col) {
        int steps = Math.max(6, (int) ((a1 - a0) * r / 1.4f));
        for (int i = 0; i <= steps; i++) {
            float a = a0 + (a1 - a0) * i / steps;
            Gui.circle(g, cx + (float) Math.cos(a) * r,
                cy + (float) Math.sin(a) * r, thick / 2, col);
        }
    }

    private static void streak(GuiGraphicsExtractor g, float x, float y,
                               float len, float phase, int col) {
        float slide = ((phase % 4f) / 4f) * len * 0.4f;
        Gui.pill(g, x + slide, y - 0.8f, len * 0.72f, 1.8f, col);
    }

    private static void arrowHead(GuiGraphicsExtractor g, float cx, float cy,
                                  float u, int col) {
        seg(g, cx, cy, 3.4f * u, 1.2f, -0.785f + 0.7f, col);
        seg(g, cx, cy, 3.4f * u, 1.2f, -0.785f - 0.7f, col);
    }

    private static void wing(GuiGraphicsExtractor g, float cx, float cy,
                             float u, int col, int side, float flap) {
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().rotate(side * (0.35f + flap));
        path(g, side * 4 * u, 0, (int) (16 * u), col, new float[][]{
            {0, -4}, {side * 7, -2}, {side * 7, 2}, {side * 1, 4}, {0, 2}});
        g.pose().popMatrix();
    }
}
