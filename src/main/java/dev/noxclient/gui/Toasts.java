package dev.noxclient.gui;

import dev.noxclient.gui.ds.DS;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * HUD toast notifications (Modul 16.8) — card with accent strip, icon dot,
 * title + message, lifetime progress bar. Slides in from the right edge
 * (250ms), holds ~3.2s, slides out. Rendered by NoxOverlay.
 */
public final class Toasts {
    private record Toast(String title, String msg, int color, long born) {}
    private static final List<Toast> LIST = new CopyOnWriteArrayList<>();
    private static final java.util.Map<String, Long> KEY_GAP = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long LIFE = 3200;
    private static final long EXIT = 320;

    /** Rate-limited push — finders call this with a STABLE key (e.g.
     *  "dig", "chest") so noisy kinds can't flood the stack by varying
     *  the text (distance suffixes used to make every toast unique). */
    public static void pushKeyed(String key, String text, int color, long minGapMs) {
        long now = System.currentTimeMillis();
        Long last = KEY_GAP.get(key);
        if (last != null && now - last < minGapMs) return;
        KEY_GAP.put(key, now);
        push(text, color);
    }

    /** Glyphs the "inter" font doesn't ship — they render as "NO GLYPH"
     *  boxes on the card. Strip the icon prefix (the accent dot already
     *  marks the toast) and downgrade arrows/multiply to ASCII. */
    private static String clean(String s) {
        return s.replace("◉ ", "").replace("◈ ", "").replace("◆ ", "")
            .replace("⚠ ", "! ").replace("×", "x")
            .replace("↓", "v").replace("↑", "^");
    }

    public static void push(String text, int color) {
        final String t = clean(text);
        LIST.removeIf(x -> x.msg.equals(t));
        LIST.add(new Toast("", t, color, System.currentTimeMillis()));
        while (LIST.size() > 5) LIST.remove(0);
    }
    /** Card toast: bold title line + secondary message line. */
    public static void push(String title, String msg, int color) {
        final String t = clean(title), m = clean(msg);
        LIST.removeIf(x -> x.title.equals(t) && x.msg.equals(m));
        LIST.add(new Toast(t, m, color, System.currentTimeMillis()));
        while (LIST.size() > 5) LIST.remove(0);
    }
    public static void info(String text)  { push(text, 0xFF5AC8FF); }
    public static void ok(String text)    { push(text, 0xFF66FF99); }
    public static void warn(String text)  { push(text, 0xFFFFCC55); }
    public static void error(String text) { push(text, 0xFFFF6666); }

    /** Top-right card stack — 250ms slide-in, progress bar, slide-out.
     *  Coordinates + font scale with the MC gui scale (ts): Gui.* ops
     *  capture raw px so pose().scale() can't touch them — the layout
     *  itself must produce scaled coords. */
    public static void render(GuiGraphicsExtractor g, Font font, int w, int startY) {
        double gs = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
        float ts = (float) Math.min(1.0, Math.max(0.55, 2.0 / gs));
        long now = System.currentTimeMillis();
        LIST.removeIf(x -> now - x.born > LIFE + EXIT);
        float y = startY;
        for (Toast x : LIST) {
            float age = now - x.born;
            float a = age < 250 ? age / 250f
                : age > LIFE - 400 ? Math.max(0, (LIFE - age) / 400f) : 1f;
            int slide = (int) ((1f - easeOut(Math.min(1, age / 250f))) * 60);
            if (age > LIFE) slide += (int) (((age - LIFE) / (float) EXIT) * 80);

            boolean titled = !x.title.isEmpty();
            float th = (titled ? 28 : 18) * ts;
            float tw = Math.max(Gui.width(font, x.title), Gui.width(font, x.msg)) * ts + 22 * ts;
            tw = Math.max(tw, 92 * ts);
            tw = Math.min(tw, w - 24);   // long messages used to run off-screen left
            float x1 = w - 12 + slide, x0 = x1 - tw;
            int alpha = (int) (a * 0xF2) << 24;

            // card: surface + left accent strip + icon dot
            Gui.round(g, x0, y, tw, th, 7 * ts, (DS.surface() & 0xFFFFFF) | alpha);
            Gui.roundBorder(g, x0, y, tw, th, 7 * ts,
                (x.color & 0x00FFFFFF) | (alpha / 2), 0);
            Gui.round(g, x0, y + 3 * ts, 2 * ts, th - 6 * ts, 1,
                (x.color & 0xFFFFFF) | alpha);
            Gui.circle(g, x0 + 11 * ts, y + (titled ? 9 : th / 2 / ts) * ts, 2.5f * ts,
                (x.color & 0xFFFFFF) | alpha);
            float fs = 9.5f * ts, tx = x0 + 17 * ts, fy = y;
            String msg = font.plainSubstrByWidth(x.msg, (int) ((tw - 20 * ts) / ts));
            if (titled) {
                String title = font.plainSubstrByWidth(x.title, (int) ((tw - 20 * ts) / ts));
                dev.noxclient.gui.nvg.NvgOverlay.op((c, st) ->
                    dev.noxclient.gui.nvg.Nvg.text(c, st, "inter_b", fs, tx, fy + 4 * ts,
                        title, alpha | 0xFFFFFF));
                dev.noxclient.gui.nvg.NvgOverlay.op((c, st) ->
                    dev.noxclient.gui.nvg.Nvg.text(c, st, "inter", fs, tx, fy + 15 * ts,
                        msg, ((int) (a * 0xFF) << 24) | (DS.secondary() & 0xFFFFFF)));
            } else {
                dev.noxclient.gui.nvg.NvgOverlay.op((c, st) ->
                    dev.noxclient.gui.nvg.Nvg.text(c, st, "inter", fs, tx, fy + (th - 8 * ts) / 2,
                        msg, alpha | 0xFFFFFF));
            }
            // progress bar (fills over LIFE)
            float prog = Math.min(1, age / (float) LIFE);
            int pw = (int) ((tw - 6 * ts) * prog);
            if (pw > 0)
                Gui.round(g, x0 + 3 * ts, y + th - 2 * ts, pw, 1, 1,
                    (x.color & 0xFFFFFF) | (alpha / 2));
            y += th + 4 * ts;
        }
    }

    private static float easeOut(float t) { return 1f - (1 - t) * (1 - t) * (1 - t); }
}
