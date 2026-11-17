package dev.noxclient.replay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Replay export (Modul 22, D.3) — renders a .donutreplay to a PNG sequence
 * (same orbit projection as ReplayScreen, headless via Java2D) and can pipe
 * raw frames into ffmpeg for MP4 output.
 *
 *   PNG:  ReplayExporter.exportPng(file, outDir, fps)
 *   MP4:  ReplayExporter.exportMp4(file, out.mp4, fps)   [needs ffmpeg on PATH]
 */
public final class ReplayExporter {
    private static final int W = 1280, H = 720;

    public static int exportPng(Path file, Path outDir, int fps) throws Exception {
        ReplayPlayer rp = ReplayPlayer.load(file);
        Files.createDirectories(outDir);
        int frames = (int) (rp.duration() / 1000.0 * fps);
        for (int i = 0; i <= frames; i++) {
            BufferedImage img = render(rp, i * 1000.0 / fps);
            ImageIO.write(img, "PNG", outDir.resolve(String.format("f%05d.png", i)).toFile());
        }
        return frames;
    }

    /** Pipe RGB frames straight into ffmpeg → mp4. Returns ffmpeg exit code. */
    public static int exportMp4(Path file, Path out, int fps) throws Exception {
        ReplayPlayer rp = ReplayPlayer.load(file);
        Process ff = new ProcessBuilder("ffmpeg", "-y",
            "-f", "rawvideo", "-pix_fmt", "rgba",
            "-s", W + "x" + H, "-r", String.valueOf(fps), "-i", "-",
            "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "20",
            out.toString()).redirectErrorStream(false).start();
        var pipe = ff.getOutputStream();
        int frames = (int) (rp.duration() / 1000.0 * fps);
        for (int i = 0; i <= frames; i++) {
            BufferedImage img = render(rp, i * 1000.0 / fps);
            int[] px = img.getRGB(0, 0, W, H, null, 0, W);
            byte[] raw = new byte[px.length * 4];
            for (int p = 0; p < px.length; p++) {
                raw[p * 4] = (byte) (px[p] >> 16);
                raw[p * 4 + 1] = (byte) (px[p] >> 8);
                raw[p * 4 + 2] = (byte) px[p];
                raw[p * 4 + 3] = (byte) (px[p] >> 24);
            }
            pipe.write(raw);
        }
        pipe.close();
        return ff.waitFor();
    }

    private static BufferedImage render(ReplayPlayer rp, double ms) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x10, 0x10, 0x14)); g.fillRect(0, 0, W, H);
        List<ReplayPlayer.Ent> ents = rp.sample(ms);
        if (ents.isEmpty()) { g.dispose(); return img; }
        var p0 = ents.stream().filter(e -> e.tag() == 1).findFirst().orElse(ents.get(0));
        float yaw = 0.6f, pitch = 0.5f, zoom = 4f;
        float cosY = (float) Math.cos(yaw), sinY = (float) Math.sin(yaw);
        float cosP = (float) Math.cos(pitch), sinP = (float) Math.sin(pitch);
        for (var e : ents) {
            float dx = e.x() - p0.x(), dy = e.y() - p0.y(), dz = e.z() - p0.z();
            float x1 = dx * cosY - dz * sinY, z1 = dx * sinY + dz * cosY;
            float y1 = dy * cosP - z1 * sinP;
            float z2 = dy * sinP + z1 * cosP;
            float scale = 400f / Math.max(40f, z2 + 120f);
            int sx = (int) (W / 2 + x1 * zoom * scale), sy = (int) (H / 2 - y1 * zoom * scale);
            g.setColor(switch (e.tag()) {
                case 1 -> new Color(0x66E0FF); case 2 -> new Color(0xE8E8EC);
                case 3 -> new Color(0xFFD870); default -> new Color(0x8A8A95);
            });
            g.fillOval(sx - 4, sy - 4, 9, 9);
        }
        // timeline strip
        g.setColor(new Color(0x2E, 0x2E, 0x36)); g.fillRect(60, H - 50, W - 120, 6);
        g.setColor(new Color(0xFF, 0x6E, 0xC7));
        int px = 60 + (int) ((W - 120) * (ms / Math.max(1, rp.duration())));
        g.fillRect(60, H - 50, px - 60, 6);
        g.setColor(Color.WHITE);
        g.drawString(String.format("%d:%02d", (int) (ms / 60000), (int) (ms / 1000) % 60), 10, H - 44);
        g.dispose();
        return img;
    }
}
