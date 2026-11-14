package dev.noxclient.gui;

import dev.noxclient.NoxClient;
import dev.noxclient.media.LyricsService;
import dev.noxclient.media.MediaService;
import dev.noxclient.module.render.LyricsSky;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Karaoke lyric strip — current line bright/bold, neighbors dimmed.
 * Rendered over the title sky (TitleScreenMixin) and in-game (NoxOverlay).
 */
public final class LyricsHud {

    /** Draw the strip centered at cx, vertically centered around cy. */
    public static void render(GuiGraphicsExtractor g, int cx, int cy, int context) {
        var m = NoxClient.INSTANCE.modules.get("LyricsSky");
        if (!(m instanceof LyricsSky ls) || !ls.isEnabled()) return;
        if (!MediaService.active() || !LyricsService.found()) return;

        Font font = Minecraft.getInstance().font;
        String prev = LyricsService.prev(), cur = LyricsService.current(),
            next = LyricsService.next();
        var t = dev.noxclient.gui.theme.Theme.current();

        int dy = cy - 14;
        if (context > 0 && !prev.isEmpty()) {
            Gui.textCenter(g, font, prev, cx, dy, 0x60FFFFFF);
            dy += 12;
        }
        if (!cur.isEmpty()) {
            // glow: soft shadow pass then bold accent
            Gui.textCenterBold(g, font, cur, cx, dy + 1, 0x40000000);
            Gui.textCenterBold(g, font, cur, cx, dy, t.accent() | 0xFF000000);
            dy += 13;
        }
        if (context > 0 && !next.isEmpty())
            Gui.textCenter(g, font, next, cx, dy, 0x60FFFFFF);
    }
}
