package dev.noxclient.gui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.noxclient.gui.theme.Theme;
import dev.noxclient.module.render.Cosmetics;
import dev.noxclient.premium.LicenseManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;

/**
 * In-game cape editor: paint the 10x16 front design of a 64x32 cape texture.
 * Saves to nox/capes/nox_edited.png (front = design, back = mirrored design,
 * edges/top/bottom filled from the rim), switches the cape style to "Custom"
 * and reloads the dynamic texture. Requires the one-time cape entitlement
 * (or premium) — locked state shows an unlock hint instead of the canvas.
 */
public class CapeEditorScreen extends Screen {
    private static final int CW = 10, CH = 16, CELL = 13;
    private static final int[] PALETTE = {
        0x000000, 0x1A1A2E, 0x2E2E4A, 0x4A4A6E, 0x808090, 0xC0C0D0, 0xFFFFFF, 0xFF8B5CF6,
        0xFF6EC7, 0xB44DFF, 0x7C3AED, 0x5599FF, 0x22D3EE, 0x55FF88, 0x2DD4A0, 0xA3E635,
        0xFFD700, 0xFFAA00, 0xFF6B35, 0xFF4444, 0x991B1B, 0x7C2D12, 0xF5DEB3, 0x8B5A2B,
        0xFF9AA2, 0xFFB7C5, 0x87CEEB, 0x0A2540, 0x134E4A, 0x365314, 0x422006, 0x581C87,
    };
    private static final String[] TOOLS = {"pen", "fill", "pick", "wipe"};

    private final Screen parent;
    private final int[] px = new int[CW * CH];      // ABGR-free: 0xRRGGBB or -1 = transparent
    private final ArrayDeque<int[]> undo = new ArrayDeque<>();
    private int color = PALETTE[7], tool;
    private int cx, cy, rx;                          // canvas + right-panel origins
    private boolean painting, erasing;

    public CapeEditorScreen(Screen parent) {
        super(Component.literal("NOX Cape Editor"));
        this.parent = parent;
        java.util.Arrays.fill(px, -1);
        seedFromFile();
    }

    private void seedFromFile() {
        try {
            var files = CustomCape.available();
            if (files.isEmpty()) return;
            try (var in = Files.newInputStream(files.get(0))) {
                NativeImage img = NativeImage.read(in);
                if (img.getWidth() >= 64 && img.getHeight() >= 32) {
                    int[] pix = img.getPixelsABGR();
                    int w = img.getWidth();
                    for (int y = 0; y < CH; y++)
                        for (int x = 0; x < CW; x++) {
                            int abgr = pix[(1 + y) * w + 1 + x];
                            px[y * CW + x] = ((abgr >>> 24) == 0) ? -1
                                : ((abgr & 0xFF) << 16) | (abgr & 0xFF00) | ((abgr >>> 16) & 0xFF);
                        }
                }
                img.close();
            }
        } catch (Exception ignored) {}
    }

    private boolean unlocked() {
        return LicenseManager.capeUnlocked();
    }

    @Override
    protected void init() {
        cx = width / 2 - CW * CELL / 2 - 60;
        cy = height / 2 - CH * CELL / 2 - 6;
        rx = cx + CW * CELL + 22;
    }

    // ---- painting ---------------------------------------------------------

    private int cellAt(double mx, double my) {
        int c = (int) (mx - cx) / CELL, r = (int) (my - cy) / CELL;
        return (c >= 0 && c < CW && r >= 0 && r < CH) ? r * CW + c : -1;
    }

    private void snapshot() {
        undo.push(px.clone());
        while (undo.size() > 40) undo.pollLast();
    }

    private void paintAt(int i) {
        if (i < 0) return;
        switch (tool) {
            case 1 -> fill(i);
            case 2 -> { if (px[i] != -1) color = px[i]; tool = 0; }
            case 3 -> px[i] = -1;
            default -> px[i] = erasing ? -1 : color;
        }
    }

    private void fill(int i) {
        int target = px[i];
        if (target == color) return;
        ArrayDeque<Integer> q = new ArrayDeque<>();
        q.push(i);
        while (!q.isEmpty()) {
            int p = q.pop();
            if (px[p] != target) continue;
            px[p] = color;
            int c = p % CW, r = p / CW;
            if (c > 0) q.push(p - 1);
            if (c < CW - 1) q.push(p + 1);
            if (r > 0) q.push(p - CW);
            if (r < CH - 1) q.push(p + CW);
        }
    }

    private void save() {
        try {
            NativeImage img = new NativeImage(64, 32, true);
            for (int y = 0; y < CH; y++) {
                for (int x = 0; x < CW; x++) {
                    int rgb = px[y * CW + x];
                    if (rgb == -1) continue;
                    int abgr = 0xFF000000 | ((rgb & 0xFF) << 16) | (rgb & 0xFF00) | (rgb >>> 16);
                    // front face (1,1)-(11,17); back (12,1)-(22,17) mirrored
                    img.setPixelABGR(1 + x, 1 + y, abgr);
                    img.setPixelABGR(21 - x, 1 + y, abgr);
                    if (x == 0) { img.setPixelABGR(0, 1 + y, abgr); img.setPixelABGR(22, 1 + y, abgr); }
                    if (x == CW - 1) { img.setPixelABGR(11, 1 + y, abgr); img.setPixelABGR(12, 1 + y, abgr); }
                    if (y == 0) { img.setPixelABGR(1 + x, 0, abgr); img.setPixelABGR(12 + (9 - x), 0, abgr); }
                    if (y == CH - 1) img.setPixelABGR(11 + (9 - x), 0, abgr);
                }
            }
            Files.createDirectories(CustomCape.dir());
            Path out = CustomCape.dir().resolve("nox_edited.png");
            img.writeToFile(out);
            img.close();
            if (Cosmetics.INSTANCE != null) {
                Cosmetics.INSTANCE.capeStyle.set("Custom");
                Cosmetics.INSTANCE.noxCape.set(true);
                if (!Cosmetics.INSTANCE.isEnabled()) Cosmetics.INSTANCE.toggle();
            }
            String err = CustomCape.reload();
            Toasts.push(err == null ? "cape saved & worn" : "saved, load failed: " + err, 0xFF8B5CF6);
            onClose();
        } catch (Exception e) {
            Toasts.push("save failed: " + e.getMessage(), 0xFFFF5555);
        }
    }

    // ---- input ------------------------------------------------------------

    private static int norm(int sdl) {
        return switch (sdl) { case 1 -> 0; case 3 -> 1; case 2 -> 2; default -> sdl; };
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (!unlocked()) return super.mouseClicked(e, doubleClick);
        double mx = e.x(), my = e.y();
        int btn = norm(e.button());

        int i = cellAt(mx, my);
        if (i >= 0) {
            if (btn == 1) { snapshot(); px[i] = -1; erasing = true; painting = true; }
            else { snapshot(); erasing = false; painting = true; paintAt(i); }
            return true;
        }
        // palette
        int pc = (int) (mx - rx) / 18, pr = (int) (my - cy) / 18;
        if (pc >= 0 && pc < 4 && pr >= 0 && pr < 8) {
            color = PALETTE[pr * 4 + pc];
            if (tool == 2) tool = 0;
            return true;
        }
        // tools row
        int ti = (int) (mx - rx) / 62, ty = cy + 8 * 18 + 10;
        if (ti >= 0 && ti < 4 && my >= ty && my < ty + 18) { tool = ti; return true; }
        // undo / clear / save buttons
        int by = ty + 26;
        if (in(mx, my, rx, by, 118, 18) && !undo.isEmpty()) {
            System.arraycopy(undo.pop(), 0, px, 0, px.length); return true;
        }
        if (in(mx, my, rx + 126, by, 118, 18)) {
            snapshot(); java.util.Arrays.fill(px, -1); return true;
        }
        if (in(mx, my, rx, by + 26, 244, 22)) { save(); return true; }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (painting) { paintAt(cellAt(e.x(), e.y())); return true; }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        painting = false;
        return super.mouseReleased(e);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        int k = e.key();
        boolean ctrl = com.mojang.blaze3d.platform.InputConstants.isKeyDown(224)
            || com.mojang.blaze3d.platform.InputConstants.isKeyDown(228);
        if (k == com.mojang.blaze3d.platform.InputConstants.KEY_Z
            && ctrl && !undo.isEmpty()) {
            System.arraycopy(undo.pop(), 0, px, 0, px.length);
            return true;
        }
        return super.keyPressed(e);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---- render -----------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        Theme t = Theme.current();
        g.fill(0, 0, width, height, 0xE6080812);
        Gui.textCenter(g, font, "N O X  cape editor", width / 2, cy - 30, t.accent());

        if (!unlocked()) {
            int bw = 260, bx = width / 2 - bw / 2, by = height / 2 - 44;
            Gui.shadow(g, bx, by, bw, 88, 12);
            Gui.roundBorder(g, bx, by, bw, 88, 12, t.accent() & 0x55FFFFFF, UICustom.cardFillTop);
            Gui.textCenter(g, font, "Cape tier required", width / 2, by + 14, t.text());
            Gui.textCenter(g, font, "One-time unlock — draw, upload and wear", width / 2, by + 34, 0xFF8B8BA0);
            Gui.textCenter(g, font, "your own cape (incl. physics cloth)", width / 2, by + 46, 0xFF8B8BA0);
            Gui.textCenter(g, font, "shop pill in the ClickGUI or /buy on Discord", width / 2, by + 62, t.accent());
            super.extractRenderState(g, mx, my, dt);
            return;
        }

        // canvas frame + checkerboard for transparency
        Gui.shadow(g, cx - 4, cy - 4, CW * CELL + 8, CH * CELL + 8, 8);
        Gui.round(g, cx - 4, cy - 4, CW * CELL + 8, CH * CELL + 8, 8, UICustom.cardFillTop);
        for (int r = 0; r < CH; r++)
            for (int c = 0; c < CW; c++) {
                int x0 = cx + c * CELL, y0 = cy + r * CELL;
                g.fill(x0, y0, x0 + CELL, y0 + CELL,
                    ((r + c) & 1) == 0 ? 0xFF20202E : 0xFF282838);
                int col = px[r * CW + c];
                if (col != -1)
                    g.fill(x0 + 1, y0 + 1, x0 + CELL, y0 + CELL, 0xFF000000 | col);
            }
        // grid lines + hover cell
        for (int c = 0; c <= CW; c++)
            g.fill(cx + c * CELL, cy, cx + c * CELL + 1, cy + CH * CELL, 0x22000000);
        for (int r = 0; r <= CH; r++)
            g.fill(cx, cy + r * CELL, cx + CW * CELL, cy + r * CELL + 1, 0x22000000);
        int hov = cellAt(mx, my);
        if (hov >= 0)
            Gui.roundBorder(g, cx + (hov % CW) * CELL, cy + (hov / CW) * CELL,
                CELL, CELL, 2, t.accent(), 0x00000000);
        Gui.text(g, font, "10x16 · front (back auto-mirrored)", cx, cy + CH * CELL + 8, 0xFF6A6A80);
        Gui.text(g, font, "LMB paint · RMB erase · ctrl-Z undo", cx, cy + CH * CELL + 20, 0xFF55556A);

        // palette
        Gui.text(g, font, "palette", rx, cy - 12, 0xFF8B8BA0);
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 4; c++) {
                int x0 = rx + c * 18, y0 = cy + r * 18, col = PALETTE[r * 4 + c];
                g.fill(x0, y0, x0 + 16, y0 + 16, 0xFF000000 | col);
                if (col == color) Gui.roundBorder(g, x0 - 1, y0 - 1, 18, 18, 3, t.accent(), 0x00000000);
            }
        // tools
        int ty = cy + 8 * 18 + 10;
        for (int i = 0; i < TOOLS.length; i++) {
            int x0 = rx + i * 62;
            boolean sel = tool == i;
            Gui.pill(g, x0, ty, 58, 18, sel ? t.accent() : 0xFF32324A);
            Gui.textCenter(g, font, TOOLS[i], x0 + 29, ty + 5, sel ? 0xFF101018 : 0xFF9C9CB8);
        }
        // actions
        int by = ty + 26;
        Gui.pill(g, rx, by, 118, 18, undo.isEmpty() ? 0xFF26263A : 0xFF32324A);
        Gui.textCenter(g, font, "undo", rx + 59, by + 5, undo.isEmpty() ? 0xFF55556A : 0xFF9C9CB8);
        Gui.pill(g, rx + 126, by, 118, 18, 0xFF32324A);
        Gui.textCenter(g, font, "clear", rx + 185, by + 5, 0xFF9C9CB8);
        Gui.pill(g, rx, by + 26, 244, 22, t.accent());
        Gui.textCenter(g, font, "save & wear", rx + 122, by + 33, 0xFF101018);
        Gui.text(g, font, "pngs also load from nox/capes/", rx, by + 56, 0xFF55556A);

        super.extractRenderState(g, mx, my, dt);
    }

    @Override
    public void onClose() { minecraft.setScreenAndShow(parent); }
    @Override
    public boolean isPauseScreen() { return false; }
}
