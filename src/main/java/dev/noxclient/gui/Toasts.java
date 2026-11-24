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

    public static void push(String text, int color) {
        LIST.removeIf(x -> x.msg.equals(text));
        LIST.add(new Toast("", text, color, System.currentTimeMillis()));
        while (LIST.size() > 5) LIST.remove(0);
    }
    /** Card toast: bold title line + secondary message line. */
    public static void push(String title, String msg, int color) {
        LIST.removeIf(x -> x.title.equals(title) && x.msg.equals(msg));
        LIST.add(new Toast(title, msg, color, System.currentTimeMillis()));
        while (LIST.size() > 5) LIST.remove(0);
    }
    public static void info(String text)  { push(text, 0xFF5AC8FF); }
    public static void ok(String text)    { push(text, 0xFF66FF99); }
    public static void warn(String text)  { push(text, 0xFFFFCC55); }
    public static void error(String text) { push(text, 0xFFFF6666); }

    /** Top-right card stack — 250ms slide-in, progress bar, slide-out. */
    public static void render(GuiGraphicsExtractor g, Font font, int w, int startY) {
        long now = System.currentTimeMillis();
        LIST.removeIf(x -> now - x.born > LIFE + EXIT);
        int y = startY;
        for (Toast x : LIST) {
            float age = now - x.born;
            float a = age < 250 ? age / 250f
                : age > LIFE - 400 ? Math.max(0, (LIFE - age) / 400f) : 1f;
            int slide = (int) ((1f - easeOut(Math.min(1, age / 250f))) * 60);
            if (age > LIFE) slide += (int) (((age - LIFE) / (float) EXIT) * 80);

            boolean titled = !x.title.isEmpty();
            int th = titled ? 28 : 18;
            int tw = Math.max(Gui.width(font, x.title), Gui.width(font, x.msg)) + 22;
            tw = Math.max(tw, 92);
            tw = Math.min(tw, w - 24);   // long messages used to run off-screen left
            int x1 = w - 12 + slide;          // right edge, slide in
            int x0 = x1 - tw;
            int alpha = (int) (a * 0xF2) << 24;

            // card: surface + left accent strip + icon dot
            Gui.round(g, x0, y, tw, th, 7, (DS.surface() & 0xFFFFFF) | alpha);
            Gui.roundBorder(g, x0, y, tw, th, 7,
                (x.color & 0x00FFFFFF) | (alpha / 2), 0);
            Gui.round(g, x0, y + 3, 2, th - 6, 1, (x.color & 0xFFFFFF) | alpha);
            Gui.circle(g, x0 + 11, y + (titled ? 9 : th / 2), 2.5f,
                (x.color & 0xFFFFFF) | alpha);
            String msg = font.plainSubstrByWidth(x.msg, tw - 20);
            if (titled) {
                Gui.textBold(g, font, font.plainSubstrByWidth(x.title, tw - 20),
                    x0 + 17, y + 4, (alpha) | 0xFFFFFF);
                Gui.text(g, font, msg, x0 + 17, y + 15,
                    ((int) (a * 0xFF) << 24) | (DS.secondary() & 0xFFFFFF));
            } else {
                Gui.text(g, font, msg, x0 + 17, y + (th - 8) / 2, (alpha) | 0xFFFFFF);
            }
            // progress bar (fills over LIFE)
            float prog = Math.min(1, age / (float) LIFE);
            int pw = (int) ((tw - 6) * prog);
            if (pw > 0)
                Gui.round(g, x0 + 3, y + th - 2, pw, 1, 1,
                    (x.color & 0xFFFFFF) | (alpha / 2));
            y += th + 4;
        }
    }

    private static float easeOut(float t) { return 1f - (1 - t) * (1 - t) * (1 - t); }
}
