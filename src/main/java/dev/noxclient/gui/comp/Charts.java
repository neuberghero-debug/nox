package dev.noxclient.gui.comp;

import com.mojang.blaze3d.platform.NativeImage;
import dev.noxclient.NoxClient;
import dev.noxclient.gui.Gui;
import dev.noxclient.gui.ds.DS;
import dev.noxclient.module.Module;
import dev.noxclient.module.donutsmp.SessionStats;
import dev.noxclient.stats.Stats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Live-stats chart widgets (Live-Stats column of the click GUI):
 * sparkline with grid + mono axis labels, 270° radial gauge (textured arc,
 * AA via supersampled bitmap), bottom-aligned bar chart.
 *
 * Data binding: sampled every 5 s while the GUI is open.
 *  - Kills/Min  → session+lifetime kills delta → per-minute rate (60-sample ring)
 *  - K/D Ratio  → kills/deaths, gauge fills pct = kd / 4.0
 *  - Money/Hour → SessionStats sidebar delta extrapolated to $/h (12-sample ring)
 */
public final class Charts {
    private Charts() {}

    /* ============ sampling rings ============ */
    private static final float[] KPM = new float[60];
    private static final float[] MPH = new float[12];
    private static int kpmPos, mphPos, lastKills = -1;
    private static long lastSample;

    /** Called every render frame; internally rate-limits to one sample / 5 s. */
    public static void tickSample() {
        long now = System.currentTimeMillis();
        if (now - lastSample < 5000) return;
        lastSample = now;
        int kills = kills();
        if (lastKills >= 0)
            KPM[kpmPos++ % KPM.length] = Math.max(0, (kills - lastKills) * 12f); // 5s → /min
        lastKills = kills;
        MPH[mphPos++ % MPH.length] = (float) moneyPerHour();
    }

    private static int kills() {
        try { return Integer.parseInt(Stats.sessionView().get("Kills")); }
        catch (Exception e) { return 0; }
    }
    public static double kd() {
        try { return Double.parseDouble(Stats.sessionView().get("K/D")); }
        catch (Exception e) { return 0; }
    }
    private static double moneyPerHour() {
        Module m = NoxClient.INSTANCE.modules.get("SessionStats");
        if (!(m instanceof SessionStats ss)) return 0;
        double el = ss.elapsedSeconds();
        return el < 60 ? 0 : ss.delta("Money") * 3600.0 / el;
    }

    private static float[] ring(float[] r, int pos) {
        float[] out = new float[r.length];
        for (int i = 0; i < r.length; i++) out[i] = r[(pos + i) % r.length];
        return out;
    }
    public static float[] kpmSeries() { return ring(KPM, kpmPos); }
    public static float[] mphSeries() { return ring(MPH, mphPos); }

    /* ============ sparkline ============ */

    /** Sparkline + subtle horizontal grid + mono y-labels (0 / mid / max). */
    public static void sparkline(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h,
                                 float[] v, int color) {
        float max = 1;
        for (float a : v) if (a > max) max = a;
        // grid: 3 horizontal lines
        for (int i = 0; i <= 2; i++) {
            int gy = y + h - 1 - i * (h - 1) / 2;
            g.fill(x, gy, x + w - 24, gy + 1, DS.fade(DS.border(), 0.5f));
            String lbl = i == 0 ? "0" : String.valueOf((int) (max * i / 2));
            Gui.mono(g, f, lbl, x + w - 20, gy - 4, DS.muted());
        }
        if (v.length < 2) return;
        float sx = (w - 26f) / (v.length - 1);
        float px = x, py = y + h - 2 - v[0] / max * (h - 8);
        for (int i = 1; i < v.length; i++) {
            float nx = x + i * sx, ny = y + h - 2 - v[i] / max * (h - 8);
            line(g, px, py, nx, ny, color);
            px = nx; py = ny;
        }
        Gui.circle(g, px, py, 2, color);   // live tip
    }

    /** Straight 1.4px line between two points (axis-aligned steps). */
    private static void line(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int c) {
        int steps = Math.max(1, (int) (Math.abs(x1 - x0) + Math.abs(y1 - y0)));
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            g.fill((int) (x0 + (x1 - x0) * t), (int) (y0 + (y1 - y0) * t),
                (int) (x0 + (x1 - x0) * t) + 2, (int) (y0 + (y1 - y0) * t) + 1, c);
        }
    }

    /* ============ radial gauge ============ */

    /** One DynamicTexture per gauge — re-rasterized only when the displayed
     *  bucket changes (pct quantized to 0..300), so per-frame cost ≈ 1 blit. */
    private static NativeImage gaugeImg;
    private static DynamicTexture gaugeTex;
    private static Identifier gaugeId;
    private static int gaugeBucket = -1, gaugeRgb = -1;

    /**
     * 270° gauge arc (gap at bottom, starts at 135°). pct = 0..1 fill.
     * Center text is drawn by the caller (mono value + %).
     */
    public static void gauge(GuiGraphicsExtractor g, int cx, int cy, int r,
                             float pct, int color, int track) {
        pct = Math.max(0, Math.min(1, pct));
        int bucket = (int) (pct * 300);
        int rgb = color & 0xFFFFFF;
        if (gaugeTex == null || bucket != gaugeBucket || rgb != gaugeRgb) {
            rasterGauge(pct, color, track);
            gaugeBucket = bucket; gaugeRgb = rgb;
        }
        int s = r * 2 + 4;
        g.blit(gaugeId, cx - r - 2, cy - r - 2, cx + r + 2, cy + r + 2, 0, 1, 0, 1);
    }

    private static void rasterGauge(float pct, int color, int track) {
        int T = 96;                                    // texture px
        if (gaugeImg == null) gaugeImg = new NativeImage(T, T, true);
        else gaugeImg.fillRect(0, 0, T, T, 0);
        double c = T / 2.0, ro = T / 2.0 - 2, ri = ro - 11;   // 11px ring band
        double sweep = pct * 270.0;
        int SS = 4;
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            double cov = 0, covT = 0;
            for (int sy = 0; sy < SS; sy++) for (int sx = 0; sx < SS; sx++) {
                double px = x + (sx + 0.5) / SS - c, py = y + (sy + 0.5) / SS - c;
                double rr = Math.sqrt(px * px + py * py);
                if (rr < ri || rr > ro) continue;
                // angle: 0° = bottom-left start (135°), clockwise →
                double a = Math.toDegrees(Math.atan2(px, -py));   // 0 = top, cw
                double rel = ((a - 135) % 360 + 360) % 360;        // deg from start
                if (rel > 270) continue;
                covT++;
                if (rel <= sweep) cov++;
            }
            int col = cov > 0 ? color : track;
            int a = (int) ((cov > 0 ? cov : covT) / (SS * SS) * 255);
            if (a > 0) gaugeImg.setPixel(x, y, (a << 24) | (col & 0xFFFFFF));
        }
        if (gaugeTex == null) {
            gaugeTex = new DynamicTexture(() -> "nox_gauge", gaugeImg);
            gaugeId = Identifier.fromNamespaceAndPath("noxclient", "gauge_dyn");
            Minecraft.getInstance().getTextureManager().register(gaugeId, gaugeTex);
        }
        gaugeTex.upload();
    }

    /* ============ bar chart ============ */

    /** Bottom-aligned bars + baseline grid; `v` oldest→newest. Mono x-labels
     *  are the caller's job (values vary). */
    public static void bars(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h,
                            float[] v, int color) {
        float max = 1;
        for (float a : v) if (a > max) max = a;
        // grid lines: baseline + 2 above
        for (int i = 0; i <= 2; i++) {
            int gy = y + h - 1 - i * (h - 12) / 2;
            g.fill(x, gy, x + w, gy + 1, DS.fade(DS.border(), i == 0 ? 1f : 0.5f));
        }
        int n = v.length, bw = Math.max(4, (w - (n - 1) * 4) / n);
        int bx = x;
        for (int i = 0; i < n; i++) {
            int bh = (int) (v[i] / max * (h - 14));
            Gui.round(g, bx, y + h - 1 - Math.max(2, bh), bw, Math.max(2, bh), 2,
                i == n - 1 ? color : DS.fade(color, 0.45f));
            bx += bw + 4;
        }
    }

    /** Formats 12000 → "12k", 1_200_000 → "1.2m". */
    public static String kfmt(double v) {
        if (v >= 1e6) return String.format("%.1fm", v / 1e6);
        if (v >= 1e3) return String.format("%.0fk", v / 1e3);
        return String.valueOf((int) v);
    }

    /* ============ section header ============ */
    public static void sectionLabel(GuiGraphicsExtractor g, Font f, String s, int x, int y) {
        Gui.text(g, f, s.toUpperCase(java.util.Locale.ROOT), x, y, DS.secondary());
    }
}
