package dev.noxclient.module.render;

import dev.noxclient.media.LyricsService;
import dev.noxclient.media.MediaService;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.render.NoxRender;
import dev.noxclient.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.Vec3;

/**
 * LyricsSky — synced lyrics floated over the NOX sky (title screen), as a
 * karaoke strip in-game, and anchored IN THE WORLD: each new lyric line is
 * placed on a fresh spot in the sky in your current view direction, so it
 * stays put like a nametag instead of being glued to your crosshair.
 * Powered by lrclib.net + MediaService.
 */
public class LyricsSky extends Module {
    public final Setting.Bool inGame = add(new Setting.Bool("In-game strip", true));
    public final Setting.Bool titleScreen = add(new Setting.Bool("On title sky", true));
    public final Setting.Bool world = add(new Setting.Bool("World sky", true,
        "Anchor each new line at a fixed spot in the sky ahead of you"));
    public final Setting.Num distance = add(new Setting.Num("Sky distance", 12, 4, 40,
        "How far ahead of the view direction a new line is anchored"));
    public final Setting.Num lift = add(new Setting.Num("Sky lift", 5, -5, 20,
        "Blocks above eye height for the anchor"));
    public final Setting.Num scale = add(new Setting.Num("Sky scale", 2.2, 0.5, 6));
    public final Setting.Num lines = add(new Setting.Num("Context lines", 1, 0, 3));

    private static LyricsSky inst;
    private static Vec3 anchor;
    private static String anchored;

    public LyricsSky() { super("LyricsSky", "Synced lyrics over the sky", Category.RENDER); inst = this; }

    @Override public void onEnable() {
        MediaService.start(MediaService.Consumer.LYRICS_SKY);
        LyricsService.start();
        anchor = null; anchored = null;
        NoxRender.add(LyricsSky::emit);
    }

    @Override protected void onDisable() {
        NoxRender.remove(LyricsSky::emit);
        LyricsService.stop();
        MediaService.stop(MediaService.Consumer.LYRICS_SKY);
        anchor = null; anchored = null;
    }

    private static void emit() {
        var m = inst;
        if (m == null || !m.isEnabled() || !m.world.on()) return;
        var mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null || mc.level == null) { anchor = null; return; }
        if (!MediaService.active() || !LyricsService.found()) return;
        String cur = LyricsService.current();
        if (cur == null || cur.isEmpty()) return;

        // new line → re-anchor at a fresh spot ahead of the CURRENT view
        if (!cur.equals(anchored)) {
            anchored = cur;
            anchor = p.getEyePosition()
                .add(p.getLookAngle().scale(m.distance.get()))
                .add(0, m.lift.get(), 0);
        }
        if (anchor == null) return;

        float s = (float) (double) m.scale.get();
        Gizmos.billboardText(cur, anchor,
            TextGizmo.Style.forColorAndCentered(0xFF8AF0FF).withScale(s))
            .setAlwaysOnTop();
        String next = LyricsService.next();
        if (next != null && !next.isEmpty())
            Gizmos.billboardText(next, anchor.add(0, -0.55 * s, 0),
                TextGizmo.Style.forColorAndCentered(0x66FFFFFF).withScale(s * 0.55f))
                .setAlwaysOnTop();
    }
}
