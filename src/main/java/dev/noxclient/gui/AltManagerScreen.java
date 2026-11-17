package dev.noxclient.gui;

import dev.noxclient.alts.AltManager;
import dev.noxclient.alts.AltProxy;
import dev.noxclient.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Account manager — paste a Microsoft session token to play as that account,
 * add offline alts, route connections through a SOCKS/HTTP proxy. Switching
 * applies on the next join; proxy wraps every outbound socket.
 */
public class AltManagerScreen extends Screen {
    private final Screen parent;
    private final List<int[]> hot = new ArrayList<>();
    // hot kinds
    private static final int ALT = 0, TOKEN = 1, OFFLINE = 2, ADD_T = 3, ADD_O = 4,
        PTYPE = 5, PHOST = 6, PPORT = 7, PUSER = 8, PPASS = 9, DEL = 10;
    private String tokenBuf = "", nameBuf = "", hostBuf, portBuf, userBuf, passBuf;
    private String focused;
    private int scroll;

    public AltManagerScreen(Screen parent) {
        super(Component.literal("NOX Accounts"));
        this.parent = parent;
        AltManager.load();
        AltProxy p = AltManager.proxy;
        hostBuf = p.host; portBuf = p.port > 0 ? String.valueOf(p.port) : "";
        userBuf = p.user; passBuf = p.pass;
    }

    private int winW() { return Math.min(540, width - 40); }
    private int winH() { return Math.min(390, height - 40); }
    private int winX() { return (width - winW()) / 2; }
    private int winY() { return (height - winH()) / 2; }

    // 26.3: SDL text input only flows after a GuiEventListener claims it —
    // custom fields (no EditBox) would otherwise never see char events
    @Override public void added() { super.added(); minecraft.onTextInputFocusChange(this, true); }
    @Override public void removed() { minecraft.onTextInputFocusChange(this, false); super.removed(); }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        Theme t = Theme.current();
        var font = Minecraft.getInstance().font;
        Gui.backdrop(g, width, height, 1f);
        int x0 = winX(), y0 = winY(), w = winW(), h = winH();
        hot.clear();
        Gui.shadow(g, x0, y0, w, h, UICustom.radius);
        Gui.round(g, x0, y0, w, h, UICustom.radius, UICustom.winFill);
        Gui.windowFx(x0, y0, w, h, UICustom.radius);
        Gui.windowFrost(x0, y0, w, h, UICustom.radius);
        Gui.textBold(g, font, "A C C O U N T S", x0 + 16, y0 + 12, t.accent());
        Gui.text(g, font, AltManager.status(), x0 + 16, y0 + 26, UICustom.textMute);
        boolean xHov = ModuleCard.inside(mx, my, x0 + w - 26, y0 + 8, 18, 16);
        Gui.textCenter(g, font, "×", x0 + w - 17, y0 + 12, xHov ? 0xFFFF8888 : UICustom.textMute);
        hot.add(new int[]{x0 + w - 26, y0 + 8, 18, 16, DEL, -1});

        int lw = 200, lx = x0 + 14, ly = y0 + 44;
        Gui.round(g, lx, ly, lw, h - 60, 8, UICustom.cardFillBottom);
        Gui.text(g, font, "Saved", lx + 10, ly + 8, UICustom.textMute);
        int ay = ly + 22;
        var alts = AltManager.alts();
        for (int i = 0; i < alts.size(); i++) {
            var a = alts.get(i);
            if (ay > ly + h - 76) break;
            boolean hov = ModuleCard.inside(mx, my, lx + 4, ay, lw - 8, 24);
            Gui.round(g, lx + 4, ay, lw - 8, 24, 6,
                a == AltManager.active() ? t.accent() & 0x44FFFFFF : hov ? UICustom.cardFillTop : 0xFF14141C);
            Gui.text(g, font, a.name, lx + 12, ay + 4, hov ? 0xFFFFFFFF : 0xFFE0E0F0);
            Gui.text(g, font, a.offline ? "offline" : a.uuid.substring(0, 8) + "…",
                lx + 12, ay + 14, UICustom.textMute);
            if (a == AltManager.active())
                Gui.text(g, font, "●", lx + lw - 20, ay + 8, t.accent());
            hot.add(new int[]{lx + 4, ay, lw - 8, 24, ALT, i});
            ay += 28;
        }

        int rx = lx + lw + 12, rw = x0 + w - 14 - rx;
        int ry = ly;
        ry = field(g, font, t, mx, my, rx, ry, rw, "Microsoft session token", tokenBuf, "token", TOKEN, true);
        ry = btn(g, font, t, mx, my, rx, ry + 2, rw, "Add token account", ADD_T, 0);
        ry = field(g, font, t, mx, my, rx, ry + 8, rw, "Offline name (cracked)", nameBuf, "name", OFFLINE, false);
        ry = btn(g, font, t, mx, my, rx, ry + 2, rw, "Add offline account", ADD_O, 0);

        Gui.text(g, font, "Proxy — " + AltManager.proxy.describe(), rx, ry + 10, UICustom.textMute);
        ry += 24;
        AltProxy.Type[] types = AltProxy.Type.values();
        int pw = (rw - 6 * (types.length - 1)) / types.length;
        for (int i = 0; i < types.length; i++) {
            boolean sel = AltManager.proxy.type == types[i];
            boolean hov = ModuleCard.inside(mx, my, rx + i * (pw + 6), ry, pw, 18);
            Gui.round(g, rx + i * (pw + 6), ry, pw, 18, 9,
                sel ? t.accent() : hov ? UICustom.cardFillTop : 0xFF14141C);
            Gui.textCenter(g, font, types[i].name(), rx + i * (pw + 6) + pw / 2, ry + 5,
                sel ? 0xFFFFFFFF : UICustom.textMute);
            hot.add(new int[]{rx + i * (pw + 6), ry, pw, 18, PTYPE, i});
        }
        ry += 26;
        int hw = (rw - 8) * 2 / 3;
        ry = field(g, font, t, mx, my, rx, ry, hw, "host", hostBuf, "phost", PHOST, false);
        field(g, font, t, mx, my, rx + hw + 8, ry - 26, rw - hw - 8, "port", portBuf, "pport", PPORT, false);
        int uh = (rw - 8) / 2;
        ry = field(g, font, t, mx, my, rx, ry, uh, "user (opt)", userBuf, "puser", PUSER, false);
        field(g, font, t, mx, my, rx + uh + 8, ry - 26, rw - uh - 8, "pass (opt)", passBuf, "ppass", PPASS, true);
        Gui.text(g, font, "Switch applies on next join · proxy wraps join + ping",
            x0 + 16, y0 + h - 18, UICustom.textMute);
        super.extractRenderState(g, mx, my, dt);
    }

    private int field(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, Theme t,
                      int mx, int my, int x, int y, int w, String label, String buf,
                      String key, int kind, boolean mask) {
        Gui.text(g, font, label, x, y, UICustom.textMute);
        boolean foc = key.equals(focused);
        Gui.round(g, x, y + 12, w, 18, 6, foc ? UICustom.cardFillTop : 0xFF14141C);
        if (foc) Gui.roundBorder(g, x, y + 12, w, 18, 6, t.accent(), 0x00000000);
        String show = mask && !buf.isEmpty() ? "•".repeat(Math.min(28, buf.length())) : buf;
        Gui.text(g, font, show + (foc && (System.currentTimeMillis() / 450) % 2 == 0 ? "_" : ""),
            x + 7, y + 17, 0xFFE0E0F0);
        hot.add(new int[]{x, y + 12, w, 18, kind, 0});
        return y + 38;
    }

    private int btn(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, Theme t,
                    int mx, int my, int x, int y, int w, String label, int kind, int arg) {
        boolean hov = ModuleCard.inside(mx, my, x, y, w, 20);
        Gui.round(g, x, y, w, 20, 7, hov ? t.accent() & 0x88FFFFFF : t.accent() & 0x44FFFFFF);
        Gui.textCenter(g, font, label, x + w / 2, y + 6, 0xFFFFFFFF);
        hot.add(new int[]{x, y, w, 20, kind, arg});
        return y + 28;
    }

    private void commit() {
        AltManager.proxy.host = hostBuf.trim();
        try { AltManager.proxy.port = Integer.parseInt(portBuf.trim()); } catch (Exception ignored) {}
        AltManager.proxy.user = userBuf.trim();
        AltManager.proxy.pass = passBuf;
        AltManager.save();
    }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        int mx = (int) e.x(), my = (int) e.y();
        for (int[] r : hot) {
            if (ModuleCard.inside(mx, my, r[0], r[1], r[2], r[3])) {
                switch (r[4]) {
                    case DEL -> { minecraft.setScreenAndShow(parent); return true; }
                    case ALT -> {
                        var a = AltManager.alts().get(r[5]);
                        if (e.button() == 3) AltManager.remove(a); else AltManager.apply(a, true);
                        return true;
                    }
                    case ADD_T -> { AltManager.loginToken(tokenBuf); tokenBuf = ""; return true; }
                    case ADD_O -> { AltManager.loginOffline(nameBuf); nameBuf = ""; return true; }
                    case PTYPE -> {
                        AltManager.proxy.type = AltProxy.Type.values()[r[5]];
                        commit(); return true;
                    }
                    case TOKEN -> focused = "token"; case OFFLINE -> focused = "name";
                    case PHOST -> focused = "phost"; case PPORT -> focused = "pport";
                    case PUSER -> focused = "puser"; case PPASS -> focused = "ppass";
                }
                return true;
            }
        }
        focused = null;
        return super.mouseClicked(e, dbl);
    }

    @Override public boolean charTyped(CharacterEvent e) {
        if (focused == null) return super.charTyped(e);
        String s = e.codepointAsString();
        for (char c : s.toCharArray()) {
            if (c < 32) continue;
            switch (focused) {
                case "token" -> tokenBuf += c;
                case "name" -> { if (Character.isLetterOrDigit(c) || c == '_') nameBuf += c; }
                case "phost" -> hostBuf += c;
                case "pport" -> { if (Character.isDigit(c)) portBuf += c; }
                case "puser" -> userBuf += c;
                case "ppass" -> passBuf += c;
            }
        }
        return true;
    }

    @Override public boolean keyPressed(KeyEvent e) {
        int k = e.key();
        if (focused != null) {
            if (k == com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE) {
                switch (focused) {
                    case "token" -> tokenBuf = back(tokenBuf);
                    case "name" -> nameBuf = back(nameBuf);
                    case "phost" -> hostBuf = back(hostBuf);
                    case "pport" -> portBuf = back(portBuf);
                    case "puser" -> userBuf = back(userBuf);
                    case "ppass" -> passBuf = back(passBuf);
                }
                return true;
            }
            if (k == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN) {
                focused = null; commit(); return true;
            }
            if (k == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
                focused = null; return true;
            }
            return true;
        }
        if (k == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            commit(); minecraft.setScreenAndShow(parent); return true;
        }
        return super.keyPressed(e);
    }

    private static String back(String s) { return s.isEmpty() ? s : s.substring(0, s.length() - 1); }

    @Override public boolean isPauseScreen() { return false; }
}
