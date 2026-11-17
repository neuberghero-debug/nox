package dev.noxclient.gui;

import dev.noxclient.gui.theme.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Seasonal ambient particles inside NOX windows — Winter snow, Spring petals,
 * Summer fireflies, Autumn leaves. Driven by wall-clock phase; positions are
 * seeded per particle so there are no per-frame allocations. Respects
 * UICustom.reducedMotion and the Interface "Seasonal FX" toggle.
 */
public final class SeasonFx {
    private static final int N = 26;
    private SeasonFx() {}

    /** Effective season name ("Auto" resolves via the calendar). */
    private static String season() {
        return switch (UICustom.season) {
            case "Winter", "Spring", "Summer", "Autumn" -> UICustom.season;
            default -> Theme.seasonName().isEmpty()
                ? switch (java.time.LocalDate.now().getMonthValue()) {
                    case 12, 1, 2 -> "Winter"; case 3, 4, 5 -> "Spring";
                    case 6, 7, 8 -> "Summer"; default -> "Autumn"; }
                : Theme.seasonName();
        };
    }

    /** Draw ambient particles clipped to a window rect. Call inside scissor. */
    public static void render(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        if (UICustom.reducedMotion || !UICustom.seasonFx) return;
        String s = season();
        if (!(UICustom.season.equals("Auto") || s.equals(UICustom.season))) return;
        double t = System.currentTimeMillis() / 1000.0;
        int col = switch (s) {
            case "Winter" -> 0xFFDFF2FF; case "Spring" -> 0xFFFFB7D5;
            case "Summer" -> 0xFFFFD870; default -> 0xFFE8863C;
        };
        boolean rise = "Summer".equals(s);
        for (int i = 0; i < N; i++) {
            // deterministic pseudo-random per-particle seeds
            double sx = frac(i * 0.6180339887 + 0.31);
            double sy = frac(i * 0.7548776662 + 0.13);
            double sp = 0.25 + frac(i * 0.971) * 0.55;          // speed factor
            double ph = i * 1.7;                                 // sway phase
            double travel = rise ? -1 : 1;
            double py = frac(sy + t * sp * 0.06 * travel) * (h + 20) - 10;
            double px = frac(sx + Math.sin(t * 0.6 + ph) * 0.03) * w;
            int a = 40 + (int) (60 * (0.5 + 0.5 * Math.sin(t * 1.3 + ph)));
            if (rise) a = 30 + (int) (90 * (0.5 + 0.5 * Math.sin(t * 2.2 + ph)));
            int c = (a << 24) | (col & 0xFFFFFF);
            int r = switch (s) {
                case "Winter" -> 1 + i % 2;     // snowflakes vary a bit
                case "Autumn" -> 2;             // leaf chunks
                default -> 1;
            };
            Gui.circle(g, x + (int) px, y + (int) py, r, c);
        }
    }

    private static double frac(double v) { return v - Math.floor(v); }
}
