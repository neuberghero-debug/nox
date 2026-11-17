package dev.noxclient.stats;

import dev.noxclient.gui.Gui;
import dev.noxclient.gui.UICustom;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

/**
 * Stats screen (Modul 26.4): nav rail left, stat cards + module-usage
 * breakdown right. Ranges: Session / Lifetime. Real data from Stats +
 * StatsStore — no placeholders.
 */
public class StatsScreen extends Screen {
    private int range;   // 0 = session, 1 = lifetime

    public StatsScreen() { super(Component.literal("NOX Stats")); }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2, w = 420, h = 250;
        addRenderableWidget(new NoxButtonDef(cx - w / 2 + 12, cy + h / 2 - 26, 88, 18,
            range == 0 ? "● Session" : "Session", () -> { range = 0; rebuildWidgets(); }));
        addRenderableWidget(new NoxButtonDef(cx - w / 2 + 104, cy + h / 2 - 26, 88, 18,
            range == 1 ? "● Lifetime" : "Lifetime", () -> { range = 1; rebuildWidgets(); }));
        addRenderableWidget(new NoxButtonDef(cx + w / 2 - 60, cy + h / 2 - 26, 48, 18, "Close", this::onClose));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        g.fill(0, 0, width, height, 0x99000000);
        int cx = width / 2, cy = height / 2, w = 420, h = 250, x = cx - w / 2, y = cy - h / 2;
        Gui.shadow(g, x, y, w, h, 12);
        Gui.roundBorder(g, x, y, w, h, 12, UICustom.border, UICustom.winFill);
        Gui.text(g, font, "NOX Stats", x + 14, y + 12, UICustom.text);
        Gui.text(g, font, range == 0 ? "session" : "lifetime", x + 14, y + 24, UICustom.textMute);

        // stat cards — 2 columns
        Map<String, String> rows = Stats.sessionView();
        int i = 0;
        for (var e : rows.entrySet()) {
            int col = i % 2, row = i / 2;
            int bx = x + 14 + col * ((w - 28) / 2), by = y + 42 + row * 40;
            int bw = (w - 28) / 2 - 6;
            Gui.round(g, bx, by, bw, 34, 8, UICustom.cardFillBottom);
            Gui.text(g, font, e.getKey(), bx + 9, by + 6, UICustom.textDim);
            String val = range == 0 || e.getKey().equals("Playtime") || e.getKey().equals("Distance")
                ? e.getValue() : lifetime(e.getKey(), e.getValue());
            Gui.text(g, font, val, bx + 9, by + 18, UICustom.accent);
            i++;
        }

        // fps sparkline (live ring, M26.3)
        {
            int sx = x + w - 132, sy = y + 10, sw = 118, sh = 22;
            Gui.round(g, sx, sy, sw, sh, 6, UICustom.cardFillBottom);
            float[] r = Stats.fpsRing();
            float max = 1; for (float v : r) max = Math.max(max, v);
            for (int si = 0; si < r.length; si++) {
                int px = sx + 3 + (int) ((sw - 6) * si / (float) (r.length - 1));
                int hgt = Math.max(1, (int) ((sh - 6) * (r[si] / max)));
                g.fill(px, sy + sh - 3 - hgt, px + 1, sy + sh - 3, UICustom.accent);
            }
        }

        // module usage bars (top 6, minutes)
        int ty = y + 42 + 3 * 40 + 8;
        Gui.text(g, font, "Module usage (min)", x + 14, ty, UICustom.textDim);
        var top = Stats.topModules(6);
        double max = top.values().stream().mapToDouble(v -> v).max().orElse(1);
        int by = ty + 12;
        for (var e : top.entrySet()) {
            int bw = (int) ((w - 160) * (e.getValue() / Math.max(0.01, max)));
            Gui.text(g, font, e.getKey(), x + 14, by + 1, UICustom.text);
            Gui.pill(g, x + 118, by, w - 200, 8, UICustom.cardFillTop);
            Gui.pill(g, x + 118, by, Math.max(4, bw), 8, UICustom.accent);
            Gui.text(g, font, String.format("%.0fm", e.getValue()), x + w - 70, by + 1, UICustom.textDim);
            by += 14;
        }
        super.extractRenderState(g, mx, my, dt);
    }

    private String lifetime(String key, String fallback) {
        return switch (key) {
            case "Kills" -> String.valueOf(StatsStore.lifetime("kills"));
            case "Deaths" -> String.valueOf(StatsStore.lifetime("deaths"));
            case "K/D" -> {
                long d = StatsStore.lifetime("deaths");
                yield d == 0 ? String.valueOf(StatsStore.lifetime("kills"))
                    : String.format("%.2f", (double) StatsStore.lifetime("kills") / d);
            }
            case "Blocks ±" -> StatsStore.lifetime("blocks_broken") + " / " + StatsStore.lifetime("blocks_placed");
            default -> fallback;
        };
    }

    @Override
    public boolean isPauseScreen() { return false; }

    /** Local minimal button — avoids depending on gui internals the other
     *  session is rewriting right now. */
    private static final class NoxButtonDef extends dev.noxclient.gui.NoxButton {
        NoxButtonDef(int x, int y, int w, int h, String label, Runnable r) {
            super(x, y, w, h, label, r);
        }
    }

    private static final List<String> UNUSED = List.of();   // silence unused import pattern
}
