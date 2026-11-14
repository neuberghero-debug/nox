package dev.noxclient.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * HUD toast notifications — module toggles, config events. Slides in from the
 * right, holds ~2.6s, fades out. Rendered by NoxOverlay.
 */
public final class Toasts {
    private record Toast(String text, int color, long born) {}
    private static final List<Toast> LIST = new CopyOnWriteArrayList<>();
    private static final long LIFE = 2600;

    public static void push(String text, int color) {
        LIST.add(new Toast(text, color, System.currentTimeMillis()));
        while (LIST.size() > 4) LIST.remove(0);
    }
    public static void info(String text)  { push(text, 0xFF5AC8FF); }
    public static void ok(String text)    { push(text, 0xFF66FF99); }
    public static void warn(String text)  { push(text, 0xFFFFCC55); }
    public static void error(String text) { push(text, 0xFFFF6666); }

    /** Top-center stack — slides down in, fades out. */
    public static void render(GuiGraphicsExtractor g, Font font, int w, int startY) {
        long now = System.currentTimeMillis();
        LIST.removeIf(x -> now - x.born > LIFE);
        int y = startY;
        for (Toast x : LIST) {
            float age = now - x.born;
            float a = age < 140 ? age / 140f
                : age > LIFE - 400 ? Math.max(0, (LIFE - age) / 400f) : 1f;
            int slide = (int) ((1f - Math.min(1, a * 2)) * -10);
            int tw = Gui.width(font, x.text) + 26;
            int x0 = w / 2 - tw / 2;
            int alpha = (int) (a * 0xE8) << 24;
            Gui.roundBorder(g, x0, y + slide, tw, 16, 7,
                (x.color & 0x00FFFFFF) | alpha, (0xE014141E & 0x00FFFFFF) | alpha);
            Gui.circle(g, x0 + 9, y + slide + 8, 3, x.color);
            Gui.text(g, font, x.text, x0 + 16, y + slide + 4,
                (alpha & 0xFF000000) | 0x00FFFFFF);
            y += 19;
        }
    }
}
