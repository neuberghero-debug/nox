package dev.noxclient.gui.comp;

import dev.noxclient.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Embedded vector icon set (Modul 14.6) — Lucide/Phosphor-style glyphs drawn
 * as fill primitives on a 12-unit grid, scaled to any size. No texture deps.
 */
public final class Icons {
    private Icons() {}

    public enum Icon {
        SEARCH, CHEVRON, CLOSE, CHECK, PLUS, MINUS, GEAR, STAR, STAR_FILL,
        HEART, BOLT, EYE, PLAY, PAUSE, ARROW_L, ARROW_R, ARROW_U, ARROW_D,
        BOX, USER, CHAT, CLIP, WARN, INFO, LOCK, DOWNLOAD, UPLOAD, SWORD,
        SHIELD, PIN, MOON, SUN, TRASH, COPY, EXTERNAL, GRID
    }

    /** Draw an icon at (x,y) scaled to `size` px. */
    public static void draw(GuiGraphicsExtractor g, Icon i, float x, float y, float size, int color) {
        float s = size / 12f;
        switch (i) {
            case SEARCH -> {
                ring(g, x + 5 * s, y + 5 * s, 3.2f * s, 1.4f * s, color);
                line(g, x + 7.6f * s, y + 7.6f * s, x + 10.5f * s, y + 10.5f * s, 1.6f * s, color);
            }
            case CHEVRON -> {
                line(g, x + 3 * s, y + 4.5f * s, x + 6 * s, y + 7.5f * s, 1.6f * s, color);
                line(g, x + 6 * s, y + 7.5f * s, x + 9 * s, y + 4.5f * s, 1.6f * s, color);
            }
            case CLOSE -> {
                line(g, x + 3 * s, y + 3 * s, x + 9 * s, y + 9 * s, 1.6f * s, color);
                line(g, x + 9 * s, y + 3 * s, x + 3 * s, y + 9 * s, 1.6f * s, color);
            }
            case CHECK -> {
                line(g, x + 2.5f * s, y + 6.5f * s, x + 5 * s, y + 9 * s, 1.6f * s, color);
                line(g, x + 5 * s, y + 9 * s, x + 9.5f * s, y + 3.5f * s, 1.6f * s, color);
            }
            case PLUS -> {
                fill(g, x + 5.2f * s, y + 2.5f * s, 1.6f * s, 7 * s, color);
                fill(g, x + 2.5f * s, y + 5.2f * s, 7 * s, 1.6f * s, color);
            }
            case MINUS -> fill(g, x + 2.5f * s, y + 5.2f * s, 7 * s, 1.6f * s, color);
            case GEAR -> {
                ring(g, x + 6 * s, y + 6 * s, 2.2f * s, 1.3f * s, color);
                for (int k = 0; k < 8; k++) {
                    double a = k * Math.PI / 4;
                    float cx = x + 6 * s + (float) Math.cos(a) * 4 * s;
                    float cy = y + 6 * s + (float) Math.sin(a) * 4 * s;
                    Gui.circle(g, cx, cy, Math.max(1f, 1.1f * s), color);
                }
            }
            case STAR, STAR_FILL -> {
                // 5-point star approximated: center + 5 spokes (filled = bigger)
                float rr = i == Icon.STAR_FILL ? 2.6f * s : 1.4f * s;
                Gui.circle(g, x + 6 * s, y + 6 * s, rr, color);
                for (int k = 0; k < 5; k++) {
                    double a = -Math.PI / 2 + k * 2 * Math.PI / 5;
                    line(g, x + 6 * s, y + 6 * s,
                        x + 6 * s + (float) Math.cos(a) * 4.6f * s,
                        y + 6 * s + (float) Math.sin(a) * 4.6f * s, 1.4f * s, color);
                }
            }
            case HEART -> {
                Gui.circle(g, x + 4.2f * s, y + 4.4f * s, 2.1f * s, color);
                Gui.circle(g, x + 7.8f * s, y + 4.4f * s, 2.1f * s, color);
                line(g, x + 2.6f * s, y + 5.6f * s, x + 6 * s, y + 9.6f * s, 1.8f * s, color);
                line(g, x + 9.4f * s, y + 5.6f * s, x + 6 * s, y + 9.6f * s, 1.8f * s, color);
            }
            case BOLT -> {
                line(g, x + 7 * s, y + 2 * s, x + 4 * s, y + 7 * s, 1.7f * s, color);
                line(g, x + 4 * s, y + 7 * s, x + 8 * s, y + 7 * s, 1.7f * s, color);
                line(g, x + 8 * s, y + 7 * s, x + 5 * s, y + 10.5f * s, 1.7f * s, color);
            }
            case EYE -> {
                ring(g, x + 6 * s, y + 6 * s, 1.8f * s, 1.2f * s, color);
                Gui.circle(g, x + 6 * s, y + 6 * s, 0.9f * s, color);
                line(g, x + 2 * s, y + 6 * s, x + 3.6f * s, y + 4.4f * s, 1.3f * s, color);
                line(g, x + 10 * s, y + 6 * s, x + 8.4f * s, y + 4.4f * s, 1.3f * s, color);
            }
            case PLAY -> {
                line(g, x + 4 * s, y + 3 * s, x + 9.5f * s, y + 6 * s, 1.7f * s, color);
                line(g, x + 9.5f * s, y + 6 * s, x + 4 * s, y + 9 * s, 1.7f * s, color);
                line(g, x + 4 * s, y + 9 * s, x + 4 * s, y + 3 * s, 1.7f * s, color);
            }
            case PAUSE -> {
                fill(g, x + 3.8f * s, y + 3 * s, 1.7f * s, 6.4f * s, color);
                fill(g, x + 6.6f * s, y + 3 * s, 1.7f * s, 6.4f * s, color);
            }
            case ARROW_L -> {
                line(g, x + 8 * s, y + 3 * s, x + 4 * s, y + 6 * s, 1.6f * s, color);
                line(g, x + 4 * s, y + 6 * s, x + 8 * s, y + 9 * s, 1.6f * s, color);
            }
            case ARROW_R -> {
                line(g, x + 4 * s, y + 3 * s, x + 8 * s, y + 6 * s, 1.6f * s, color);
                line(g, x + 8 * s, y + 6 * s, x + 4 * s, y + 9 * s, 1.6f * s, color);
            }
            case ARROW_U -> {
                line(g, x + 3 * s, y + 8 * s, x + 6 * s, y + 4 * s, 1.6f * s, color);
                line(g, x + 6 * s, y + 4 * s, x + 9 * s, y + 8 * s, 1.6f * s, color);
            }
            case ARROW_D -> {
                line(g, x + 3 * s, y + 4 * s, x + 6 * s, y + 8 * s, 1.6f * s, color);
                line(g, x + 6 * s, y + 8 * s, x + 9 * s, y + 4 * s, 1.6f * s, color);
            }
            case BOX -> {
                Gui.roundBorder(g, Math.round(x + 2.5f * s), Math.round(y + 3 * s),
                    Math.round(7 * s), Math.round(6.5f * s), Math.round(1.2f * s), color, 0);
                line(g, x + 2.5f * s, y + 5 * s, x + 9.5f * s, y + 5 * s, 1.1f * s, color);
                line(g, x + 6 * s, y + 5 * s, x + 6 * s, y + 9.5f * s, 1.1f * s, color);
            }
            case USER -> {
                Gui.circle(g, x + 6 * s, y + 4 * s, 2 * s, color);
                fill(g, x + 3 * s, y + 7.6f * s, 6 * s, 2.6f * s, color);
            }
            case CHAT -> {
                Gui.roundBorder(g, Math.round(x + 2 * s), Math.round(y + 3 * s),
                    Math.round(8 * s), Math.round(5.5f * s), Math.round(1.6f * s), color, 0);
                line(g, x + 4.5f * s, y + 8.5f * s, x + 4 * s, y + 10.5f * s, 1.3f * s, color);
            }
            case CLIP -> {
                line(g, x + 8.5f * s, y + 3 * s, x + 3.5f * s, y + 9.5f * s, 1.5f * s, color);
                Gui.circle(g, x + 8.8f * s, y + 2.8f * s, 1.4f * s, color);
            }
            case WARN -> {
                line(g, x + 6 * s, y + 2.5f * s, x + 10 * s, y + 9.5f * s, 1.5f * s, color);
                line(g, x + 10 * s, y + 9.5f * s, x + 2 * s, y + 9.5f * s, 1.5f * s, color);
                line(g, x + 2 * s, y + 9.5f * s, x + 6 * s, y + 2.5f * s, 1.5f * s, color);
                fill(g, x + 5.4f * s, y + 5 * s, 1.2f * s, 2.4f * s, color);
            }
            case INFO -> {
                ring(g, x + 6 * s, y + 6 * s, 4 * s, 1.4f * s, color);
                fill(g, x + 5.4f * s, y + 5.4f * s, 1.2f * s, 3 * s, color);
                Gui.circle(g, x + 6 * s, y + 3.4f * s, 0.7f * s, color);
            }
            case LOCK -> {
                Gui.round(g, Math.round(x + 3 * s), Math.round(y + 5.5f * s),
                    Math.round(6 * s), Math.round(4.5f * s), Math.round(1 * s), color);
                ring(g, x + 6 * s, y + 4.6f * s, 1.9f * s, 1.3f * s, color);
            }
            case DOWNLOAD -> {
                line(g, x + 6 * s, y + 2.5f * s, x + 6 * s, y + 8 * s, 1.5f * s, color);
                line(g, x + 3.5f * s, y + 6 * s, x + 6 * s, y + 8.5f * s, 1.5f * s, color);
                line(g, x + 8.5f * s, y + 6 * s, x + 6 * s, y + 8.5f * s, 1.5f * s, color);
                fill(g, x + 3 * s, y + 10 * s, 6 * s, 1.3f * s, color);
            }
            case UPLOAD -> {
                line(g, x + 6 * s, y + 8.5f * s, x + 6 * s, y + 3 * s, 1.5f * s, color);
                line(g, x + 3.5f * s, y + 5.5f * s, x + 6 * s, y + 3 * s, 1.5f * s, color);
                line(g, x + 8.5f * s, y + 5.5f * s, x + 6 * s, y + 3 * s, 1.5f * s, color);
                fill(g, x + 3 * s, y + 10 * s, 6 * s, 1.3f * s, color);
            }
            case SWORD -> {
                line(g, x + 3 * s, y + 9 * s, x + 9.5f * s, y + 2.5f * s, 1.5f * s, color);
                line(g, x + 3.6f * s, y + 6.4f * s, x + 5.6f * s, y + 8.4f * s, 1.4f * s, color);
            }
            case SHIELD -> {
                line(g, x + 6 * s, y + 2.4f * s, x + 9.5f * s, y + 4 * s, 1.4f * s, color);
                line(g, x + 9.5f * s, y + 4 * s, x + 9 * s, y + 7.5f * s, 1.4f * s, color);
                line(g, x + 9 * s, y + 7.5f * s, x + 6 * s, y + 10 * s, 1.4f * s, color);
                line(g, x + 6 * s, y + 10 * s, x + 3 * s, y + 7.5f * s, 1.4f * s, color);
                line(g, x + 3 * s, y + 7.5f * s, x + 2.5f * s, y + 4 * s, 1.4f * s, color);
                line(g, x + 2.5f * s, y + 4 * s, x + 6 * s, y + 2.4f * s, 1.4f * s, color);
            }
            case PIN -> {
                ring(g, x + 6 * s, y + 5 * s, 2.6f * s, 1.4f * s, color);
                line(g, x + 3.8f * s, y + 6.6f * s, x + 6 * s, y + 10.4f * s, 1.4f * s, color);
                line(g, x + 8.2f * s, y + 6.6f * s, x + 6 * s, y + 10.4f * s, 1.4f * s, color);
            }
            case MOON -> {
                Gui.circle(g, x + 6.4f * s, y + 5.6f * s, 3.4f * s, color);
                Gui.circle(g, x + 8.6f * s, y + 4 * s, 2.8f * s, 0);   // cutout via bg? use thin crescent
            }
            case SUN -> {
                Gui.circle(g, x + 6 * s, y + 6 * s, 2.6f * s, color);
                for (int k = 0; k < 8; k++) {
                    double a = k * Math.PI / 4;
                    line(g, x + 6 * s + (float) Math.cos(a) * 3.8f * s,
                        y + 6 * s + (float) Math.sin(a) * 3.8f * s,
                        x + 6 * s + (float) Math.cos(a) * 4.8f * s,
                        y + 6 * s + (float) Math.sin(a) * 4.8f * s, 1.1f * s, color);
                }
            }
            case TRASH -> {
                Gui.roundBorder(g, Math.round(x + 3.4f * s), Math.round(y + 4 * s),
                    Math.round(5.2f * s), Math.round(6 * s), Math.round(0.8f * s), color, 0);
                fill(g, x + 2.6f * s, y + 3.2f * s, 6.8f * s, 1.2f * s, color);
                fill(g, x + 4.6f * s, y + 1.9f * s, 2.8f * s, 1.2f * s, color);
            }
            case COPY -> {
                Gui.roundBorder(g, Math.round(x + 4.6f * s), Math.round(y + 4.6f * s),
                    Math.round(5 * s), Math.round(5 * s), Math.round(0.9f * s), color, 0);
                Gui.roundBorder(g, Math.round(x + 2.4f * s), Math.round(y + 2.4f * s),
                    Math.round(5 * s), Math.round(5 * s), Math.round(0.9f * s), color, 0);
            }
            case EXTERNAL -> {
                Gui.roundBorder(g, Math.round(x + 2.6f * s), Math.round(y + 3.4f * s),
                    Math.round(6.8f * s), Math.round(6 * s), Math.round(1 * s), color, 0);
                line(g, x + 5.5f * s, y + 7 * s, x + 9.5f * s, y + 3 * s, 1.4f * s, color);
                fill(g, x + 7 * s, y + 2.6f * s, 2.6f * s, 1.3f * s, color);
                fill(g, x + 8.2f * s, y + 2.6f * s, 1.3f * s, 2.6f * s, color);
            }
            case GRID -> {
                for (int ry = 0; ry < 2; ry++) for (int rx = 0; rx < 2; rx++)
                    Gui.round(g, Math.round(x + (2.6f + rx * 4) * s),
                        Math.round(y + (2.6f + ry * 4) * s),
                        Math.round(3 * s), Math.round(3 * s), Math.round(0.6f * s), color);
            }
        }
    }

    /* ---- primitives ---- */

    private static void fill(GuiGraphicsExtractor g, float x, float y, float w, float h, int c) {
        g.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), c);
    }

    /** Bresenham-thick line via filled circles — smooth at any scale. */
    private static void line(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1,
                             float t, int c) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        int steps = Math.max(1, (int) (len / Math.max(0.5f, t * 0.45f)));
        float r = t / 2f;
        for (int i = 0; i <= steps; i++) {
            float f = i / (float) steps;
            g.fill(Math.round(x0 + dx * f - r), Math.round(y0 + dy * f - r),
                Math.round(x0 + dx * f + r + 0.5f), Math.round(y0 + dy * f + r + 0.5f), c);
        }
    }

    /** ring outline via circle stamps along the perimeter. */
    private static void ring(GuiGraphicsExtractor g, float cx, float cy, float r, float t, int c) {
        int steps = Math.max(12, (int) (r * 10));
        float h = t / 2f;
        for (int i = 0; i < steps; i++) {
            double a = i * 2 * Math.PI / steps;
            float px = cx + (float) Math.cos(a) * r, py = cy + (float) Math.sin(a) * r;
            g.fill(Math.round(px - h), Math.round(py - h),
                Math.round(px + h + 0.5f), Math.round(py + h + 0.5f), c);
        }
    }
}
