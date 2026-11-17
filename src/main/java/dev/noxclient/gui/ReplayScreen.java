package dev.noxclient.gui;

import dev.noxclient.replay.ReplayPlayer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Replay playback screen (Modul 22, D.2).
 *
 * Orbit-camera 3D projection of recorded entities: drag = orbit yaw/pitch,
 * wheel = zoom. Bottom bar = timeline slider with clickable event markers.
 * Space/▶ toggle · [ ] speed 0.25x–4x · click marker = seek.
 *
 * Entities draw as tag-colored diamonds (players cyan, items white,
 * projectiles gold, other gray) — deterministic, no world state touched.
 */
public class ReplayScreen extends Screen {
    private final ReplayPlayer rp;
    private float orbitYaw = 0.6f, orbitPitch = 0.5f, zoom = 4f;
    private float cx, cy, cz;                    // orbit center (first player)
    private long lastNanos;
    private boolean draggingView, draggingSeek;
    private static final float[] SPEEDS = {0.25f, 0.5f, 1, 2, 4};
    private int speedIdx = 2;

    private final int TL_X0 = 60, TL_H = 8;      // timeline geometry

    public ReplayScreen(Path file) throws java.io.IOException {
        super(Component.literal("NOX Replay"));
        this.rp = ReplayPlayer.load(file);
        rp.speed = SPEEDS[speedIdx];
    }

    /* ---------- input ---------- */

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        int mx = (int) e.x(), my = (int) e.y();
        int tlY = height - 46;
        // marker clicks → seek
        for (var ev : rp.events()) {
            int x = tlX(ev.ms());
            if (Math.abs(mx - x) <= 4 && Math.abs(my - tlY) <= 8) { rp.seek(ev.ms()); return true; }
        }
        if (my >= tlY - TL_H && my <= tlY + TL_H) { draggingSeek = true; rp.seek(seekMs(mx)); return true; }
        // play/pause button
        if (mx >= 8 && mx <= 34 && my >= tlY - 10 && my <= tlY + 10) { rp.playing = !rp.playing; return true; }
        // speed button
        if (mx >= 38 && mx <= 56 && my >= tlY - 10 && my <= tlY + 10) {
            speedIdx = (speedIdx + 1) % SPEEDS.length; rp.speed = SPEEDS[speedIdx]; return true;
        }
        draggingView = true;
        return true;
    }

    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (draggingSeek) { rp.seek(seekMs((int) e.x())); return true; }
        if (draggingView) {
            orbitYaw += (float) (dx * 0.008f);
            orbitPitch = Math.clamp(orbitPitch + (float) (dy * 0.006f), -1.4f, 1.4f);
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override public boolean mouseReleased(MouseButtonEvent e) {
        draggingView = draggingSeek = false;
        return super.mouseReleased(e);
    }

    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        zoom = Math.clamp(zoom - (float) dy * 0.5f, 1f, 30f);
        return true;
    }

    @Override public boolean keyPressed(KeyEvent e) {
        if (e.key() == 44) { rp.playing = !rp.playing; return true; }   // SDL SPACE=44
        if (e.isEscape()) { onClose(); return true; }
        return super.keyPressed(e);
    }

    private int tlX(long ms) { return TL_X0 + (int) ((width - 2 * TL_X0) * (ms / (double) Math.max(1, rp.duration()))); }
    private double seekMs(int mx) {
        return rp.duration() * Math.clamp((mx - TL_X0) / (double) (width - 2 * TL_X0), 0, 1);
    }

    /* ---------- render ---------- */

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        g.fill(0, 0, width, height, 0xE0101014);
        long now = System.nanoTime();
        if (lastNanos != 0) rp.tick((now - lastNanos) / 1_000_000L);
        lastNanos = now;

        List<ReplayPlayer.Ent> ents = rp.sample();
        // orbit center = first player entity (tag 1), else centroid
        if (!ents.isEmpty()) {
            var p = ents.stream().filter(e -> e.tag() == 1).findFirst().orElse(ents.get(0));
            cx = p.x(); cy = p.y(); cz = p.z();
        }
        int vpW = width, vpH = height - 70;
        float sinY = (float) Math.sin(orbitYaw), cosY = (float) Math.cos(orbitYaw);
        float sinP = (float) Math.sin(orbitPitch), cosP = (float) Math.cos(orbitPitch);
        for (var e : ents) {
            float dx = e.x() - cx, dy = e.y() - cy, dz = e.z() - cz;
            // yaw orbit
            float x1 = dx * cosY - dz * sinY, z1 = dx * sinY + dz * cosY;
            // pitch orbit
            float y1 = dy * cosP - z1 * sinP;
            float z2 = dy * sinP + z1 * cosP;
            float scale = 400f / Math.max(40f, z2 + 120f);
            int sx = (int) (vpW / 2 + x1 * zoom * scale);
            int sy = (int) (vpH / 2 - y1 * zoom * scale);
            int col = switch (e.tag()) {
                case 1 -> 0xFF66E0FF; case 2 -> 0xFFE8E8EC;
                case 3 -> 0xFFFFD870; default -> 0xFF8A8A95;
            };
            if ((e.flags() & 4) != 0) col = (col & 0x00FFFFFF) | 0x44000000;
            diamond(g, sx, sy, 4, col);
        }

        /* timeline */
        int tlY = height - 46;
        Gui.fill(g, TL_X0, tlY - 3, width - TL_X0, tlY + 3, 0xFF2E2E36);
        int px = tlX((long) rp.cursor);
        Gui.fill(g, TL_X0, tlY - 3, px, tlY + 3, UICustom.accent);
        for (var ev : rp.events()) {
            int x = tlX(ev.ms());
            Gui.fill(g, x - 1, tlY - 6, x + 1, tlY + 6, 0xFFFFD870);
        }
        diamond(g, px, tlY, 5, 0xFFFFFFFF);

        /* controls */
        Gui.text(g, font, rp.playing ? "❚❚" : "▶", 10, tlY - 5, 0xFFE8E8EC);
        Gui.text(g, font, SPEEDS[speedIdx] + "x", 40, tlY - 5, 0xFFE8E8EC);
        Gui.text(g, font,
            fmt((long) rp.cursor) + " / " + fmt(rp.duration())
                + "   " + ents.size() + " ents   drag=orbit wheel=zoom space=play",
            TL_X0, tlY + 14, 0xFF9B9BA3);
        Gui.text(g, font, "NOX REPLAY", 10, 10, UICustom.accent);
        Gui.text(g, font, rp.clientVersion(), 10, 22, 0xFF5C5C66);
        super.extractRenderState(g, mx, my, dt);
    }

    private static void diamond(GuiGraphicsExtractor g, int x, int y, int r, int col) {
        for (int i = 0; i < r; i++) {
            Gui.fill(g, x - i, y - r + i, x + i + 1, y - r + i + 1, col);
            Gui.fill(g, x - i, y + r - i, x + i + 1, y + r - i + 1, col);
        }
    }

    private static String fmt(long ms) {
        long s = ms / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
