package dev.noxclient.gui;

import dev.noxclient.NoxClient;
import dev.noxclient.gui.ds.DS;
import dev.noxclient.gui.nvg.NvgOverlay;
import dev.noxclient.util.EspMarks;
import dev.noxclient.module.donutsmp.Auction;
import dev.noxclient.module.donutsmp.StorageESP;
import dev.noxclient.module.render.ESP;
import dev.noxclient.module.render.PlayerESP;
import dev.noxclient.module.render.Tracers;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * HUD overlay: projects world positions to screen space through the camera
 * view-rotation-projection matrix, then draws ESP boxes, tracers and finder
 * markers with the normal 2D pipeline.
 */
public class NoxOverlay implements HudElement {
    private float hs = 1f;   // current HUD scale — rects are reported in screen px

    /** Report the screen-space rect an element occupies (for the HUD editor). */
    private void rep(String id, int x, int y, int w, int h) {
        HudLayout.report(id, (int) (x * hs), (int) (y * hs), (int) (w * hs), (int) (h * hs));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var cam = mc.gameRenderer.mainCamera();
        Matrix4f mat = cam.getViewRotationProjectionMatrix(new Matrix4f());
        Vec3 cp = cam.position();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();

        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player) continue;
            double[] b = projectBox(e.getBoundingBox(), cp, mat, w, h);
            if (b == null) continue;
            drawEntity(g, mc, e, b);
        }
        // finder markers
        drawMarks(g, cp, mat, w, h, StorageESP.HITS);
        drawMarks(g, cp, mat, w, h, EspMarks.MARKS);
        drawMarks(g, cp, mat, w, h, dev.noxclient.module.donutsmp.OreESP.HITS);
        drawMarks(g, cp, mat, w, h, dev.noxclient.module.donutsmp.DebrisFinder.HITS);
        drawMarks(g, cp, mat, w, h, dev.noxclient.module.donutsmp.DebrisFinder.AREAS);
        float hs = dev.noxclient.gui.UICustom.hudScale;
        // auto-fit: on small logical screens (high vanilla GUI scale) the HUD
        // shrinks proportionally instead of overflowing; floor keeps it legible
        float fit = Math.min(1f, Math.min(w / 420f, h / 240f));
        hs *= Math.max(fit, 0.45f);
        this.hs = hs;
        if (hs != 1f) {
            g.pose().pushMatrix();
            g.pose().scale(hs, hs);
        }
        int sw = (int) (w / hs), sh = (int) (h / hs);
        drawWidgets(g, mc, sw, sh);
        drawTargetHud(g, mc, sw, sh);
        drawSessionStats(g, mc, sw, sh);
        drawStats(g, mc, sw, sh);
        drawScoreboard(g, mc, sw, sh);
        drawAuction(g, mc, sw, sh);
        drawBaseFinder(g, mc, sw, sh);
        drawRegionMap(g, mc, sw, sh);
        drawDebrisPanel(g, mc, sw, sh);
        drawStaffLive(g, mc, sw, sh);
        drawBaseEspCards(g, mc, cam, cp, mat, sw, sh);
        drawWheel(g, mc, sw, sh);
        if (hs != 1f) g.pose().popMatrix();
        var hm = NoxClient.INSTANCE.modules.get("HudWidgets");
        boolean compassOn = hm instanceof dev.noxclient.module.render.HudWidgets hw
            && hw.isEnabled() && hw.show(hw.compass) && mc.player != null;
        Toasts.render(g, mc.font, w, compassOn ? (int) (24 * hs) + 2 : 4);
    }

    private static final long sessionStart = System.currentTimeMillis();
    private static double lastTickNanos = -1, ema = 20f;
    private static final java.util.Map<String, Float> listAnim = new java.util.HashMap<>();
    private static String tpsText() { return String.format("%.1f tps", ema); }
    /** Called from a tick mixin if present; falls back to 20. */
    public static void noteTick() {
        long now = System.nanoTime();
        if (lastTickNanos > 0) {
            double tps = 1e9 / (now - lastTickNanos);
            ema = Math.min(20, ema * 0.95 + tps * 0.05);
        }
        lastTickNanos = now;
    }

    private void drawWidgets(GuiGraphicsExtractor g, Minecraft mc, int w, int h) {
        var m = NoxClient.INSTANCE.modules.get("HudWidgets");
        if (!(m instanceof dev.noxclient.module.render.HudWidgets hw) || !hw.isEnabled()) return;
        var t = dev.noxclient.gui.theme.Theme.current();
        // info column — collect lines, then anchor to the configured corner
        var lines = new java.util.ArrayList<String[]>();
        if (hw.watermark.on()) lines.add(new String[]{"NOX " + NoxClient.VERSION, Integer.toHexString(t.accent())});
        // coords render as their own centered mono card (16.4) — not a line
        if (hw.coords.on() && mc.player != null && !dev.noxclient.module.misc.StreamerMode.coordsHidden())
            drawCoordsCard(g, mc, w, h);
        if (hw.show(hw.fps)) lines.add(new String[]{mc.getFps() + " fps", "text"});
        if (hw.show(hw.ping) && mc.getConnection() != null && mc.player != null) {
            var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null) lines.add(new String[]{info.getLatency() + " ms", "text"});
        }
        if (hw.show(hw.nether) && mc.player != null && !dev.noxclient.module.misc.StreamerMode.coordsHidden()) {
            var p = mc.player.blockPosition();
            boolean isNether = mc.level.dimension().toString().contains("nether");
            lines.add(new String[]{isNether
                ? "overworld: " + p.getX() * 8 + " " + p.getZ() * 8
                : "nether: " + p.getX() / 8 + " " + p.getZ() / 8, "nether"});
        }
        if (hw.show(hw.facing) && mc.player != null) {
            lines.add(new String[]{switch (mc.player.getDirection()) {
                case NORTH -> "N (-Z)"; case SOUTH -> "S (+Z)";
                case EAST -> "E (+X)"; case WEST -> "W (-X)";
                default -> "?";
            }, "text"});
        }
        if (hw.show(hw.bps) && mc.player != null)
            lines.add(new String[]{String.format("%.1f b/s",
                mc.player.getDeltaMovement().horizontalDistance() * 20), "text"});
        if (hw.show(hw.tps)) lines.add(new String[]{tpsText(), "text"});
        if (hw.show(hw.session)) {
            long s3 = (System.currentTimeMillis() - sessionStart) / 1000;
            lines.add(new String[]{String.format("session %d:%02d", s3 / 60, s3 % 60), "text"});
        }
        if (hw.show(hw.effects) && mc.player != null) {
            var active = mc.player.getActiveEffects().stream().toList();
            int ei = 0;
            for (var eff : active) {
                if (++ei > 8) {
                    lines.add(new String[]{"+" + (active.size() - 8) + " more", "effect"});
                    break;
                }
                lines.add(new String[]{eff.getEffect().getRegisteredName()
                    + " " + (eff.getAmplifier() + 1), "effect"});
            }
        }

        int rightPad = scoreboardPad(mc);
        boolean rightSide = hw.anchor.get().contains("Right");
        boolean bottom = hw.anchor.get().contains("Bottom");
        float[] fp = HudLayout.get("info");
        int maxW = 0;
        for (var l : lines) maxW = Math.max(maxW, Gui.width(mc.font, l[0]));
        int infoTop = fp != null ? (int) (fp[1] * h) : (bottom ? h - 4 - lines.size() * 11 : 4);
        int infoX = fp != null ? (int) (fp[0] * w) : (rightSide ? w - 6 - rightPad - maxW : 6);
        int y = infoTop;
        String iStyle = hw.infoStyle.get();
        final float S = hs;
        if (!lines.isEmpty() && !"Lines".equals(iStyle)) {
            // modern grouped card: subtle bg + accent edge (+ per-line pills)
            int cw = maxW + ("Card".equals(iStyle) ? 16 : 10);
            int ch = lines.size() * 11 + ("Card".equals(iStyle) ? 7 : 0);
            int cx = fp != null ? infoX - 6 : (rightSide ? w - 6 - rightPad - cw : 4);
            int cy = fp != null ? infoTop - 4 : infoTop;
            boolean card = "Card".equals(iStyle), pills = "Pills".equals(iStyle);
            if (card) y = cy + 4;
            int ly = y;
            var tx = new java.util.ArrayList<String>(lines.size());
            var tcol = new java.util.ArrayList<Integer>(lines.size());
            var tacc = new java.util.ArrayList<Integer>(lines.size());
            var ty = new java.util.ArrayList<Float>(lines.size());
            for (var l : lines) {
                tx.add(l[0]); ty.add((float) ly);
                tcol.add(switch (l[1]) {
                    case "nether" -> 0xFFB44DFF; case "effect" -> 0xFF8AE0FF;
                    case "text" -> t.text() | 0xFF000000;
                    default -> (int) Long.parseLong(l[1], 16) | 0xFF000000;
                });
                tacc.add(l[1].equals("nether") ? 0xFFB44DFF : t.accent() | 0xFF000000);
                ly += 11;
            }
            final int fcx = cx, fcy = cy, fcw = cw, fch = ch;
            final int fInfoX = infoX, fRP = rightPad, fW = w;
            NvgOverlay.op((c2, st) -> {
                float X = fcx * S, Y = fcy * S, CW = fcw * S, CH = fch * S;
                if (card) {
                    dev.noxclient.gui.nvg.Nvg.shadow(c2, st, X, Y, CW, CH, 7f * S,
                        8f * S, 0x59000000);
                    dev.noxclient.gui.nvg.Nvg.vgrad(c2, st, X, Y, CW, CH, 7f * S,
                        withA(DS.surfaceAlt(), 0.92f), withA(DS.surface(), 0.92f));
                    dev.noxclient.gui.nvg.Nvg.round(c2, st, X, Y, 2f * S, CH, 3f * S,
                        t.accent() | 0xFF000000);
                    dev.noxclient.gui.nvg.Nvg.roundB(c2, st, X, Y, CW, CH, 7f * S,
                        0, DS.line());
                    dev.noxclient.gui.nvg.Nvg.insetHi(c2, st, X, Y, CW, 7f * S,
                        0x14FFFFFF);
                }
                float fs = 9.5f * S;
                for (int i = 0; i < tx.size(); i++) {
                    float lyy = ty.get(i) * S;
                    float lw = dev.noxclient.gui.nvg.Nvg.textW(c2, "inter", fs, tx.get(i));
                    float lx = fp != null ? (fInfoX + 2) * S
                        : (rightSide ? X + CW - 8f * S - lw : X + 8f * S);
                    if (pills) {
                        dev.noxclient.gui.nvg.Nvg.round(c2, st, lx - 4f * S, lyy - 1f * S,
                            lw + 8f * S, 10f * S, 5f * S, withA(DS.surfaceAlt(), 0.85f));
                        dev.noxclient.gui.nvg.Nvg.round(c2, st, lx - 4f * S, lyy - 1f * S,
                            2f * S, 10f * S, 3f * S, tacc.get(i));
                    }
                    dev.noxclient.gui.nvg.Nvg.text(c2, st, "inter", fs, lx, lyy,
                        tx.get(i), tcol.get(i));
                }
            });
            rep("info", cx, cy, cw, ch);
        } else {
            int ly2 = y;
            var tx = new java.util.ArrayList<String>(lines.size());
            var tcol = new java.util.ArrayList<Integer>(lines.size());
            var ty = new java.util.ArrayList<Float>(lines.size());
            for (var l : lines) {
                tx.add(l[0]); ty.add((float) ly2);
                tcol.add(switch (l[1]) {
                    case "nether" -> 0xFFB44DFF; case "effect" -> 0xFF8AE0FF;
                    case "text" -> t.text() | 0xFF000000;
                    default -> (int) Long.parseLong(l[1], 16) | 0xFF000000;
                });
                ly2 += 11;
            }
            final int fInfoX = infoX, fRP = rightPad, fW = w;
            NvgOverlay.op((c2, st) -> {
                float fs = 9.5f * S;
                for (int i = 0; i < tx.size(); i++) {
                    float lw = dev.noxclient.gui.nvg.Nvg.textW(c2, "inter", fs, tx.get(i));
                    float lx = fp != null ? fInfoX * S
                        : (rightSide ? (fW - 6 - fRP) * S - lw : 6f * S);
                    dev.noxclient.gui.nvg.Nvg.text(c2, st, "inter", fs, lx, ty.get(i) * S,
                        tx.get(i), tcol.get(i));
                }
            });
            y = ly2;
            if (!lines.isEmpty()) rep("info", infoX, infoTop, maxW, y - infoTop);
        }
        y = Math.max(y, infoTop + (lines.isEmpty() ? 0 : lines.size() * 11) + ("Card".equals(iStyle) ? 11 : 0));
        int infoBottom = y;   // first free row below the info block

        // arraylist — glass pills w/ accent edge + slide-in animation
        if (hw.arraylist.on()) {
            var mods = NoxClient.INSTANCE.modules.all().stream()
                .filter(mo -> !mo.hideList() && (mo.isEnabled() || hw.showDisabled.get()))
                .sorted((a, b2) -> Integer.compare(
                    Gui.width(mc.font, b2.name), Gui.width(mc.font, a.name))).toList();
            boolean lRight = hw.listSide.get().contains("Right");
            boolean lBot = hw.listSide.get().contains("Bottom");
            float[] lp = HudLayout.get("arraylist");
            boolean edgeRight = lp == null && lRight;   // free-pos rows pin the accent left
            // cap the list to what actually fits — overflow becomes "+N more"
            int maxRows = Math.max(3, (h - 8) / 13);
            // enabled first (sorted by width), disabled trail behind dimmed
            if (hw.showDisabled.get())
                mods = mods.stream().sorted((a, b2) -> Boolean.compare(b2.isEnabled(), a.isEnabled()))
                    .toList();
            int hidden = Math.max(0, mods.size() - maxRows);
            if (hidden > 0) mods = mods.subList(0, maxRows - 1);
            // same corner as the info block? start below it instead of overlapping
            boolean sameCorner = lRight == rightSide && lBot == bottom;
            int ay = lp != null ? (int) (lp[1] * h)
                : lBot ? h - 4 - (mods.size() + (hidden > 0 ? 1 : 0)) * 13
                          : (sameCorner && !lines.isEmpty() ? infoBottom + 3 : 4);
            if (lp == null && sameCorner && lBot && !lines.isEmpty())
                ay = Math.min(ay, infoTop - 3 - (mods.size() + (hidden > 0 ? 1 : 0)) * 13);
            int listTop = ay, maxRw = 0, baseX = lp != null ? (int) (lp[0] * w) : 2;
            long nowMs = System.currentTimeMillis();
            var live = new java.util.HashSet<String>();
            // rows are captured at extract-time, drawn AA'd by the NVG pass —
            // per-row: name, y, slide px, ease alpha, enabled flag
            var rN = new java.util.ArrayList<String>();   var rY = new java.util.ArrayList<Float>();
            var rS = new java.util.ArrayList<Float>();    var rE = new java.util.ArrayList<Float>();
            var rOn = new java.util.ArrayList<Boolean>(); var rW = new java.util.ArrayList<Float>();
            for (var mod : mods) {
                live.add(mod.name);
                // slide-in: modules ease from the screen edge over ~180ms
                float a = listAnim.computeIfAbsent(mod.name,
                    k -> nowMs / 1000f);
                if (a < 0) { a = -a; listAnim.put(mod.name, a); } // re-enabled mid-slide: keep easing in
                float p = Math.min(1f, (nowMs / 1000f - a) / 0.18f);
                float ease = 1f - (1f - p) * (1f - p);            // ease-out quad
                int tw = Gui.width(mc.font, mod.name);
                int slide = (int) ((1f - ease) * 16);
                int rw = tw + 10;
                rN.add(mod.name); rY.add((float) ay); rS.add((float) slide);
                rE.add(ease); rOn.add(mod.isEnabled()); rW.add((float) rw);
                maxRw = Math.max(maxRw, rw);
                ay += 13;
            }
            // disabled modules slide+fade out over 180ms before eviction
            float nowSec = nowMs / 1000f;
            var it = listAnim.entrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                if (live.contains(e.getKey())) continue;
                if (e.getValue() >= 0) e.setValue(-nowSec); // first miss: stamp death time (negative)
                float p = (nowSec + e.getValue()) / 0.18f;
                if (UICustom.reducedMotion || p >= 1f) { it.remove(); continue; }
                float e2 = 1f - p * p;
                int tw = Gui.width(mc.font, e.getKey());
                int slide = (int) ((1f - e2) * 16);
                int rw = tw + 10;
                rN.add(e.getKey()); rY.add((float) ay); rS.add((float) slide);
                rE.add(e2); rOn.add(Boolean.TRUE); rW.add((float) rw);
                maxRw = Math.max(maxRw, rw);
                ay += 13;
            }
            if (hidden > 0) {
                rN.add("+" + hidden + " more"); rY.add((float) ay); rS.add(0f);
                rE.add(1f); rOn.add(Boolean.FALSE); rW.add((float) (Gui.width(mc.font, rN.getLast()) + 10));
                maxRw = Math.max(maxRw, rW.getLast().intValue());
                ay += 13;
            }
            final int fBaseX = baseX, fRP = rightPad, fW = w;
            final int fAccent = t.accent() | 0xFF000000, fText = t.text() | 0xFF000000;
            NvgOverlay.op((c2, st) -> {
                float fs = 9.5f * S, rh = 12f * S;
                for (int i = 0; i < rN.size(); i++) {
                    String name = rN.get(i);
                    float ease = rE.get(i); if (ease <= 0.001f) continue;
                    float tw2 = dev.noxclient.gui.nvg.Nvg.textW(c2, "inter", fs, name);
                    float rw = Math.max(rW.get(i) * S, tw2 + 10f * S);
                    float yy = rY.get(i) * S, sl = rS.get(i) * S;
                    float px = fp != null ? fBaseX * S - sl
                        : edgeRight ? (fW - 2 - fRP) * S - rw + sl : 2f * S - sl;
                    boolean on = rOn.get(i), more = name.startsWith("+");
                    int bg = withA(DS.surfaceAlt(), 0.72f * ease);
                    dev.noxclient.gui.nvg.Nvg.round(c2, st, px, yy, rw, rh, 4f * S, bg);
                    dev.noxclient.gui.nvg.Nvg.roundB(c2, st, px, yy, rw, rh, 4f * S,
                        0, withA(DS.line(), ease));
                    if (!more)
                        dev.noxclient.gui.nvg.Nvg.round(c2, st,
                            edgeRight ? px + rw - 2.5f * S : px + 0.5f * S,
                            yy + 2f * S, 2f * S, 8f * S, 1.5f * S,
                            on ? withA(fAccent, ease) : withA(0xFF4A4A5A, ease));
                    int tcol = more ? withA(DS.muted(), ease)
                        : on ? withA(fText, ease) : withA(0xFF7A7A8E, ease * 0.8f);
                    dev.noxclient.gui.nvg.Nvg.text(c2, st, "inter", fs,
                        edgeRight ? px + rw - 5f * S - tw2 : px + 5f * S,
                        yy + (rh - fs) * 0.5f - 0.5f * S, name, tcol);
                }
            });
            if (ay > listTop) {
                int rx = lp != null ? baseX
                    : edgeRight ? w - 2 - rightPad - maxRw : 2;
                rep("arraylist", rx, listTop, maxRw, ay - listTop);
            }
        }
        if (hw.show(hw.compass)) drawCompass(g, mc, w, h, t);
        if (hw.show(hw.armor) || hw.show(hw.totems)) drawGear(g, mc, w, h, hw);
        drawSpotify(g, mc, t, w, h);
        var lm = NoxClient.INSTANCE.modules.get("LyricsSky");
        if (lm instanceof dev.noxclient.module.render.LyricsSky ls && ls.isEnabled() && ls.inGame.on()) {
            float[] yp = HudLayout.get("lyrics");
            int lx0 = yp != null ? (int) (yp[0] * w) : w / 2 - 100;
            int ly0 = yp != null ? (int) (yp[1] * h) : h - 52;
            rep("lyrics", lx0, ly0, 200, 30);
            LyricsHud.render(g, lx0 + 100, ly0 + 6, (int) (double) ls.lines.get());
        }
    }

    /** Pixels to keep clear on the right while a scoreboard sidebar is shown. */
    private static int scoreboardPad(Minecraft mc) {
        try {
            var sb = mc.level.getScoreboard();
            var obj = sb.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
            if (obj == null) return 0;
            int w = Gui.width(mc.font, obj.getDisplayName().getString());
            for (var e : sb.listPlayerScores(obj))
                if (!e.isHidden())
                    w = Math.max(w, Gui.width(mc.font, e.ownerName().getString())
                        + Gui.width(mc.font, " " + e.value()));
            return Math.min(w + 24, mc.getWindow().getGuiScaledWidth() / 2);
        } catch (Throwable t) { return 0; }
    }

    /** JetBrains Mono font for the coords card (Modul 14.2/16.4). */
    private static final net.minecraft.network.chat.FontDescription MONO =
        new net.minecraft.network.chat.FontDescription.Resource(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("noxclient", "nox_mono"));
    private static final java.util.Map<String, net.minecraft.network.chat.Component> MONO_TXT =
        new java.util.LinkedHashMap<>(128, .75f, true) {
            @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, net.minecraft.network.chat.Component> e) {
                return size() > 512;
            }};

    private static net.minecraft.network.chat.Component mono(String s) {
        return MONO_TXT.computeIfAbsent(s,
            k -> net.minecraft.network.chat.Component.literal(k)
                .withStyle(st -> st.withFont(MONO)));
    }

    /**
     * Coordinates card (16.4): centered card under the compass — mono digits,
     * color-coded X/Y/Z axes, dimension icon, current biome.
     */
    private void drawCoordsCard(GuiGraphicsExtractor g, Minecraft mc, int w, int h) {
        var p = mc.player.blockPosition();
        var t = dev.noxclient.gui.theme.Theme.current();
        String xs = String.valueOf(p.getX()), ys = String.valueOf(p.getY()),
               zs = String.valueOf(p.getZ());
        String dim = mc.level.dimension().identifier().getPath();
        String biome = "";
        try {
            biome = mc.level.getBiome(p).unwrapKey().map(k -> k.identifier().getPath()).orElse("");
        } catch (Throwable ignored) {}
        String dimShort = dim.replace("the_", "");
        int cw = mc.font.width(mono(xs + " " + ys + " " + zs + " " + dimShort + " " + biome)) + 40;
        int cx = w / 2, cy = 26;
        float[] cp = HudLayout.get("coords");
        if (cp != null) {
            cx = Math.clamp((int) (cp[0] * w), 2, Math.max(2, w - cw - 2)) + cw / 2;
            cy = Math.clamp((int) (cp[1] * h), 2, Math.max(2, h - 24));
        } else {
            // the auction panel defaults to the same top-center slot — stack below it
            var am = NoxClient.INSTANCE.modules.get("Auction");
            int[] ar = HudLayout.rect("auction");
            if (am instanceof dev.noxclient.module.donutsmp.Auction a && a.isEnabled()
                && a.winnerAt < 0 && ar != null
                && cx - cw / 2 < ar[0] + ar[2] && cx + cw / 2 > ar[0]
                && cy < ar[1] + ar[3] && cy + 22 > ar[1])
                cy = ar[1] + ar[3] + 4;
        }
        Gui.round(g, cx - cw / 2, cy, cw, 22, 10, 0xB8141824);
        Gui.roundBorder(g, cx - cw / 2, cy, cw, 22, 10, 0x26FFFFFF, 0);
        rep("coords", cx - cw / 2, cy, cw, 22);
        int x = cx - cw / 2 + 8;
        // dimension dot: nether=red, end=violet, overworld=accent
        int dc = dim.contains("nether") ? 0xFFFF5555 : dim.contains("end") ? 0xFFB44DFF : t.accent();
        Gui.circle(g, x + 3, cy + 11, 3, dc); x += 12;
        // color-coded axes — X red-ish, Y green-ish, Z blue-ish (axis convention)
        Gui.mono(g, mc.font, xs, x, cy + 7, 0xFFFF7A7A); x += Gui.monoWidth(mc.font, xs);
        Gui.mono(g, mc.font, " ", x, cy + 7, 0xFF8A8A9E); x += Gui.monoWidth(mc.font, " ");
        Gui.mono(g, mc.font, ys, x, cy + 7, 0xFF8AE08A); x += Gui.monoWidth(mc.font, ys);
        Gui.mono(g, mc.font, " ", x, cy + 7, 0xFF8A8A9E); x += Gui.monoWidth(mc.font, " ");
        Gui.mono(g, mc.font, zs, x, cy + 7, 0xFF7AB8FF); x += Gui.monoWidth(mc.font, zs);
        if (!biome.isEmpty()) {
            String tag = " " + biome;
            Gui.mono(g, mc.font, tag, x, cy + 7, 0xFF5A5A6E);
        }
    }

    /**
     * Top-center compass strip: cardinal/intercardinal letters + 15° ticks
     * sliding with player yaw, accent needle marks the facing direction.
     * MC yaw: 0=S(+Z), 90=W, 180=N, 270=E.
     */
    private void drawCompass(GuiGraphicsExtractor g, Minecraft mc, int w, int h,
                             dev.noxclient.gui.theme.Theme t) {
        int cw = Math.min(220, w - 120);
        if (cw < 90) return;
        float[] cp = HudLayout.get("compass");
        int cx = cp != null ? (int) (cp[0] * w) + cw / 2 : w / 2;
        int cy = cp != null ? (int) (cp[1] * h) : 6;
        int ch = 16;
        rep("compass", cx - cw / 2, cy, cw, ch);
        Gui.vgrad(g, cx - cw / 2, cy, cw, ch, 8, 0xC0131824, 0x800A0E16);
        float yaw = mc.player.getYRot() % 360f;
        if (yaw < 0) yaw += 360f;
        float ppd = cw / 160f;                       // ~±80° visible range
        Gui.clipOn(cx - cw / 2 + 2, cy, cx + cw / 2 - 2, cy + ch);
        for (int deg = 0; deg < 360; deg += 15) {
            float d = deg - yaw;
            d -= Math.round(d / 360f) * 360f;         // wrap to ±180
            int x = Math.round(cx + d * ppd);
            if (x < cx - cw / 2 + 6 || x > cx + cw / 2 - 6) continue;
            float fade = Math.max(0.3f, 1f - Math.abs(d) / 95f);
            int a = ((int) (0xFF * fade)) << 24;
            if (deg % 45 == 0) {
                String l = switch (deg) {
                    case 0 -> "S"; case 45 -> "SW"; case 90 -> "W"; case 135 -> "NW";
                    case 180 -> "N"; case 225 -> "NE"; case 270 -> "E"; default -> "SE";
                };
                int col = a | ((deg == 180 ? t.accent() : t.text()) & 0xFFFFFF);
                Gui.text(g, mc.font, l, x - Gui.width(mc.font, l) / 2, cy + 4, col);
            } else {
                Gui.fill(g, x, cy + ch - 6, x + 1, cy + ch - 2, a | 0x005A5A6E);
            }
        }
        Gui.clipOff();
        Gui.fill(g, cx - 1, cy + 3, cx + 1, cy + ch - 3, t.accent());   // facing needle
    }

    /** Armor row + totem counter above the hotbar — DonutSMP PvP staples. */
    private void drawGear(GuiGraphicsExtractor g, Minecraft mc, int w, int h,
                          dev.noxclient.module.render.HudWidgets hw) {
        var inv = mc.player.getInventory();
        float[] gp = HudLayout.get("gear");
        int y = gp != null ? (int) (gp[1] * h) : h - 46;   // sits just above the vanilla hotbar
        int x = gp != null ? (int) (gp[0] * w) : 6;
        int x0 = x;
        if (hw.show(hw.armor)) {
            // 26.3: armor lives in EntityEquipment, not Inventory rows
            for (var slot : new net.minecraft.world.entity.EquipmentSlot[] {
                net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.FEET }) {
                var st = mc.player.getItemBySlot(slot);
                if (st.isEmpty()) continue;
                g.item(st, x, y);
                g.itemDecorations(mc.font, st, x, y);
                x += 17;
            }
            var main = inv.getSelectedItem();
            if (!main.isEmpty()) { g.item(main, x, y); g.itemDecorations(mc.font, main, x, y); x += 17; }
            var off = mc.player.getOffhandItem();
            if (!off.isEmpty()) { g.item(off, x, y); g.itemDecorations(mc.font, off, x, y); }
            if (x > x0) rep("gear", x0, y, x - x0, 16);
        }
        if (hw.show(hw.totems)) {
            int n = 0;
            for (int i = 0; i < inv.getContainerSize(); i++)
                if (inv.getItem(i).is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING))
                    n += inv.getItem(i).getCount();
            if (n > 0) {
                var st = new net.minecraft.world.item.ItemStack(
                    net.minecraft.world.item.Items.TOTEM_OF_UNDYING);
                float[] tp = HudLayout.get("totems");
                int tx = tp != null ? (int) (tp[0] * w) : w - 24;
                int ty = tp != null ? (int) (tp[1] * h) : h - 46;
                rep("totems", tx - 24, ty, 48, 16);
                g.item(st, tx, ty);
                Gui.text(g, mc.font, "x" + n, tx - 4 - Gui.width(mc.font, "x" + n),
                    ty + 5, n <= 1 ? 0xFFFF5555 : 0xFFFFD75A);
            }
        }
    }

    /** TargetHUD — name, health bar, armor for the entity we're fighting. */
    private void drawTargetHud(GuiGraphicsExtractor g, Minecraft mc, int w, int h) {
        var m = NoxClient.INSTANCE.modules.get("TargetHUD");
        if (!(m instanceof dev.noxclient.module.render.TargetHUD th) || !th.isEnabled()) return;
        var target = dev.noxclient.module.combat.KillAura.currentTarget;
        if (target == null || !target.isAlive()) return;
        var t = dev.noxclient.gui.theme.Theme.current();

        int pw = 148, ph = th.armor.on() ? 46 : 34;
        float[] tp = HudLayout.get("targethud");
        int x = tp != null ? Math.clamp((int) (tp[0] * w), 2, Math.max(2, w - pw - 2))
            : Math.clamp((int) (double) th.posX.get(), 2, Math.max(2, w - pw - 2));
        int y = tp != null ? Math.clamp((int) (tp[1] * h), 2, Math.max(2, h - ph - 2))
            : Math.clamp((int) (double) th.posY.get(), 2, Math.max(2, h - ph - 2));
        rep("targethud", x, y, pw, ph);
        Gui.shadow(g, x, y, pw, ph, 9);
        Gui.roundBorder(g, x, y, pw, ph, 9, t.accent() & 0x55FFFFFF, 0xF2141821);

        String name = target.getName().getString();
        while (Gui.width(mc.font, name) > pw - 46 && name.length() > 3)
            name = name.substring(0, name.length() - 1);
        Gui.textBold(g, mc.font, name, x + 8, y + 7, 0xFFF0E8FF);
        String dist = (int) Math.sqrt(mc.player.distanceToSqr(target)) + "m";
        Gui.text(g, mc.font, dist, x + pw - 8 - Gui.width(mc.font, dist), y + 7, 0xFF8A8A9E);

        // health bar — green → yellow → red by fraction
        float frac = Math.min(1f, target.getHealth() / Math.max(1f, target.getMaxHealth()));
        int barX = x + 8, barY = y + 19, barW = pw - 16, barH = 6;
        Gui.round(g, barX, barY, barW, barH, 3, 0xFF262634);
        int hc = frac > 0.5f
            ? lerpColor(0xFFFFCC44, 0xFF55DD66, (frac - 0.5f) * 2)
            : lerpColor(0xFFFF5544, 0xFFFFCC44, frac * 2);
        Gui.round(g, barX, barY, Math.max(4, (int) (barW * frac)), barH, 3, hc);
        String hp = String.format("%.1f", target.getHealth());
        Gui.text(g, mc.font, hp, barX + barW + 4 - Gui.width(mc.font, hp), barY + 8,
            target.getAbsorptionAmount() > 0 ? 0xFFFFD75A : 0xFF9C9CB8);

        if (th.armor.on()) {
            int ax = x + 8;
            for (var slot : new net.minecraft.world.entity.EquipmentSlot[] {
                net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.FEET }) {
                var st = target.getItemBySlot(slot);
                if (st.isEmpty()) { ax += 17; continue; }
                g.item(st, ax, y + 28);
                ax += 17;
            }
        }
    }

    private static int lerpColor(int a, int b, float t) {
        return (((a >>> 24 & 0xFF) + (int) (((b >>> 24 & 0xFF) - (a >>> 24 & 0xFF)) * t)) << 24)
            | (((a >>> 16 & 0xFF) + (int) (((b >>> 16 & 0xFF) - (a >>> 16 & 0xFF)) * t)) << 16)
            | (((a >>> 8 & 0xFF) + (int) (((b >>> 8 & 0xFF) - (a >>> 8 & 0xFF)) * t)) << 8)
            | ((a & 0xFF) + (int) (((b & 0xFF) - (a & 0xFF)) * t));
    }

    /** SessionStats — money/shards/kills deltas parsed off the DonutSMP sidebar. */
    private void drawSessionStats(GuiGraphicsExtractor g, Minecraft mc, int w, int h) {
        var m = NoxClient.INSTANCE.modules.get("SessionStats");
        if (!(m instanceof dev.noxclient.module.donutsmp.SessionStats ss) || !ss.isEnabled()) return;
        if (ss.lines.isEmpty()) return;
        var t = dev.noxclient.gui.theme.Theme.current();

        int rows = ss.lines.size() + 1;
        int pw = 0, ph = 18 + rows * 11;
        for (var e : ss.lines.entrySet())
            pw = Math.max(pw, Gui.width(mc.font, e.getKey() + "  " + e.getValue() + "  +9.9M"));
        pw = Math.min(pw + 16, 150);
        float[] tp = HudLayout.get("sessionstats");
        int x = tp != null ? Math.clamp((int) (tp[0] * w), 2, Math.max(2, w - pw - 2))
            : Math.clamp((int) (double) ss.posX.get(), 2, Math.max(2, w - pw - 2));
        int y = tp != null ? Math.clamp((int) (tp[1] * h), 2, Math.max(2, h - ph - 2))
            : Math.clamp((int) (double) ss.posY.get(), 2, Math.max(2, h - ph - 2));
        rep("sessionstats", x, y, pw, ph);

        Gui.shadow(g, x, y, pw, ph, 8);
        Gui.roundBorder(g, x, y, pw, ph, 8, t.accent() & 0x55FFFFFF, 0xF2141821);
        Gui.textBold(g, mc.font, "Session", x + 8, y + 6, t.accent() | 0xFF000000);
        String time = ss.sessionTime();
        Gui.text(g, mc.font, time, x + pw - 8 - Gui.width(mc.font, time), y + 6, 0xFF8A8A9E);

        int ry = y + 18;
        for (var e : ss.lines.entrySet()) {
            Gui.text(g, mc.font, e.getKey(), x + 8, ry, 0xFFB8B8CC);
            String val = e.getValue();
            long d = ss.delta(e.getKey());
            String right = val + (ss.deltas.on() && d != 0 ? " " + (d > 0 ? "+" : "") + fmt(d) : "");
            Gui.text(g, mc.font, right, x + pw - 8 - Gui.width(mc.font, right), ry,
                d > 0 ? 0xFF55DD66 : d < 0 ? 0xFFFF5544 : 0xFFF0E8FF);
            ry += 11;
        }
    }

    /**
     * StatsHud — the ClickGUI live-stats widgets as a draggable HUD panel.
     * Per-widget toggles (kills/min sparkline, K/D gauge, money/h bars),
     * compact one-line mode, opacity + accent edge — position via HUD editor.
     */
    private void drawStats(GuiGraphicsExtractor g, Minecraft mc, int w, int h) {
        var m = NoxClient.INSTANCE.modules.get("StatsHud");
        if (!(m instanceof dev.noxclient.module.render.StatsHud sh2) || !sh2.isEnabled()) return;
        dev.noxclient.gui.comp.Charts.tickSample();   // keep rings fed while HUD is up
        final float S = hs;                            // NVG draws in real GUI units
        final float alpha = (float) (sh2.opacity.get().doubleValue() / 100.0); // setting is 0-100

        if (sh2.compact.on()) {
            var parts = new java.util.ArrayList<String>();
            float[] kpm = dev.noxclient.gui.comp.Charts.kpmSeries();
            if (sh2.kpm.on()) parts.add(String.format("%.1f/m", kpm.length > 0 ? kpm[kpm.length - 1] : 0));
            if (sh2.kd.on()) parts.add(String.format("kd %.2f", dev.noxclient.gui.comp.Charts.kd()));
            float[] mph = dev.noxclient.gui.comp.Charts.mphSeries();
            if (sh2.mph.on()) parts.add(dev.noxclient.gui.comp.Charts.kfmt(
                mph.length > 0 ? mph[mph.length - 1] : 0) + "/h");
            if (parts.isEmpty()) return;
            String line = String.join("  ·  ", parts);
            int pw = Gui.width(mc.font, line) + 22, ph = 15;
            float[] tp = HudLayout.get("stats");
            int x = tp != null ? Math.clamp((int) (tp[0] * w), 2, Math.max(2, w - pw - 2)) : 6;
            int y = tp != null ? Math.clamp((int) (tp[1] * h), 2, Math.max(2, h - ph - 2))
                : (int) (h * 0.45);
            rep("stats", x, y, pw, ph);
            boolean edge = sh2.edge.on();
            dev.noxclient.gui.nvg.NvgOverlay.op((c, st) -> {
                float X = x * S, Y = y * S, PW = pw * S, PH = ph * S;
                dev.noxclient.gui.nvg.Nvg.vgrad(c, st, X, Y, PW, PH, 8 * S,
                    withA(dev.noxclient.gui.ds.DS.surfaceAlt(), alpha),
                    withA(dev.noxclient.gui.ds.DS.surface(), alpha));
                dev.noxclient.gui.nvg.Nvg.roundB(c, st, X, Y, PW, PH, 8 * S, 0x00FFFFFF,
                    dev.noxclient.gui.ds.DS.line());
                if (edge) dev.noxclient.gui.nvg.Nvg.round(c, st, X, Y + 3 * S, 2 * S, PH - 6 * S,
                    1 * S, dev.noxclient.gui.ds.DS.accent());
                dev.noxclient.gui.nvg.Nvg.text(c, st, "inter_sb", 9.5f * S, X + 9 * S, Y + PH / 2,
                    line, dev.noxclient.gui.ds.DS.text(),
                    org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
            });
            return;
        }

        // expanded card — measure with unscaled logical units, scale inside the op
        int pw = 148;
        int ph = 22;                                     // header row
        if (sh2.kpm.on()) ph += 34;
        if (sh2.kd.on()) ph += 40;
        if (sh2.mph.on()) ph += 30;
        int gap = (int) (double) sh2.spacing.get();
        ph += gap;
        if (!sh2.header.on()) ph -= 8;
        if (!sh2.kpm.on() && !sh2.kd.on() && !sh2.mph.on()) return;
        float[] tp = HudLayout.get("stats");
        int x = tp != null ? Math.clamp((int) (tp[0] * w), 2, Math.max(2, w - pw - 2)) : 6;
        int y = tp != null ? Math.clamp((int) (tp[1] * h), 2, Math.max(2, h - ph - 2))
            : (int) (h * 0.40);
        rep("stats", x, y, pw, ph);

        var sv = dev.noxclient.stats.Stats.sessionView();
        float[] kpmS = dev.noxclient.gui.comp.Charts.kpmSeries();
        float[] mphS = dev.noxclient.gui.comp.Charts.mphSeries();
        double kdV = dev.noxclient.gui.comp.Charts.kd();
        String kills = sv.getOrDefault("Kills", "0"), deaths = sv.getOrDefault("Deaths", "0");
        String tm = String.format("%d:%02d",
            (System.currentTimeMillis() - sessionStart) / 60000,
            ((System.currentTimeMillis() - sessionStart) / 1000) % 60);
        boolean hdr = sh2.header.on(), kpmOn = sh2.kpm.on(), kdOn = sh2.kd.on(),
            mphOn = sh2.mph.on(), edge = sh2.edge.on();
        final int fph = ph, fgap = gap;

        dev.noxclient.gui.nvg.NvgOverlay.op((c, st) -> {
            float X = x * S, Y = y * S, PW = pw * S, PH = fph * S, PAD = 10 * S, GAP = fgap * S;
            // obsidian card
            dev.noxclient.gui.nvg.Nvg.shadow(c, st, X, Y, PW, PH, 10 * S, 7 * S, 0x55000000);
            dev.noxclient.gui.nvg.Nvg.vgrad(c, st, X, Y, PW, PH, 10 * S,
                withA(dev.noxclient.gui.ds.DS.surfaceAlt(), alpha),
                withA(dev.noxclient.gui.ds.DS.surface(), alpha));
            dev.noxclient.gui.nvg.Nvg.roundB(c, st, X, Y, PW, PH, 10 * S, 0x00FFFFFF,
                dev.noxclient.gui.ds.DS.line());
            dev.noxclient.gui.nvg.Nvg.insetHi(c, st, X + 2 * S, Y, PW - 4 * S, 8 * S, 0x0EFFFFFF);
            if (edge) dev.noxclient.gui.nvg.Nvg.round(c, st, X, Y + 4 * S, 2 * S, PH - 8 * S,
                1 * S, dev.noxclient.gui.ds.DS.accent());

            float ry = Y + PAD;
            if (hdr) {
                dev.noxclient.gui.nvg.Nvg.textL(c, st, "inter_b", 8f * S, X + PAD, ry,
                    "SESSION", dev.noxclient.gui.ds.DS.cardAccent(), 1.6f * S,
                    org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_TOP);
                dev.noxclient.gui.nvg.Nvg.text(c, st, "mono", 8.5f * S, X + PW - PAD, ry,
                    tm, dev.noxclient.gui.ds.DS.muted(),
                    org.lwjgl.nanovg.NanoVG.NVG_ALIGN_RIGHT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_TOP);
                ry += 14 * S;
            }

            // kills/min — label + value row, sparkline beneath
            if (kpmOn) {
                dev.noxclient.gui.nvg.Nvg.text(c, st, "inter", 8.5f * S, X + PAD, ry,
                    "KILLS / MIN", dev.noxclient.gui.ds.DS.muted(),
                    org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_TOP);
                float latest = kpmS.length > 0 ? kpmS[kpmS.length - 1] : 0;
                dev.noxclient.gui.nvg.Nvg.text(c, st, "mono", 10.5f * S, X + PW - PAD, ry - 1 * S,
                    String.format(java.util.Locale.US, "%.1f", latest),
                    dev.noxclient.gui.ds.DS.text(),
                    org.lwjgl.nanovg.NanoVG.NVG_ALIGN_RIGHT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_TOP);
                ry += 11 * S;
                sparklineH(c, st, X + PAD, ry, PW - PAD * 2, 20 * S, kpmS, S);
                ry += 23 * S + GAP;
            }

            // k/d — gauge left, value + kills/deaths right
            if (kdOn) {
                dev.noxclient.gui.nvg.Nvg.text(c, st, "inter", 8.5f * S, X + PAD, ry,
                    "K / D", dev.noxclient.gui.ds.DS.muted(),
                    org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_TOP);
                ry += 11 * S;
                float gcx = X + PAD + 14 * S, gcy = ry + 14 * S, gr = 12.5f * S;
                float pct = (float) Math.min(1, kdV / 4.0);
                dev.noxclient.gui.nvg.Nvg.arc(c, st, gcx, gcy, gr, 4.5f * S, 135, 405,
                    dev.noxclient.gui.ds.DS.surface());
                if (pct > 0.002f)
                    dev.noxclient.gui.nvg.Nvg.arc(c, st, gcx, gcy, gr, 4.5f * S, 135,
                        135 + pct * 270, dev.noxclient.gui.ds.DS.accent());
                dev.noxclient.gui.nvg.Nvg.text(c, st, "mono", 8f * S, gcx, gcy,
                    String.format(java.util.Locale.US, "%.2f", kdV),
                    dev.noxclient.gui.ds.DS.text(),
                    org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE);
                float tx = X + PAD + 32 * S;
                dev.noxclient.gui.nvg.Nvg.text(c, st, "inter", 9f * S, tx, ry + 5 * S,
                    "kills", dev.noxclient.gui.ds.DS.muted(),
                    org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_TOP);
                dev.noxclient.gui.nvg.Nvg.text(c, st, "mono", 9.5f * S, tx + 34 * S, ry + 5 * S,
                    kills, dev.noxclient.gui.ds.DS.text(),
                    org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_TOP);
                dev.noxclient.gui.nvg.Nvg.text(c, st, "inter", 9f * S, tx, ry + 17 * S,
                    "deaths", dev.noxclient.gui.ds.DS.muted(),
                    org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_TOP);
                dev.noxclient.gui.nvg.Nvg.text(c, st, "mono", 9.5f * S, tx + 34 * S, ry + 17 * S,
                    deaths, dev.noxclient.gui.ds.DS.text(),
                    org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_TOP);
                ry += 29 * S + GAP;
            }

            // money/h — label + value row, bars beneath
            if (mphOn) {
                dev.noxclient.gui.nvg.Nvg.text(c, st, "inter", 8.5f * S, X + PAD, ry,
                    "MONEY / HOUR", dev.noxclient.gui.ds.DS.muted(),
                    org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_TOP);
                float latest = mphS.length > 0 ? mphS[mphS.length - 1] : 0;
                dev.noxclient.gui.nvg.Nvg.text(c, st, "mono", 10.5f * S, X + PW - PAD, ry - 1 * S,
                    dev.noxclient.gui.comp.Charts.kfmt(latest),
                    dev.noxclient.gui.ds.DS.text(),
                    org.lwjgl.nanovg.NanoVG.NVG_ALIGN_RIGHT | org.lwjgl.nanovg.NanoVG.NVG_ALIGN_TOP);
                ry += 11 * S;
                barsH(c, st, X + PAD, ry, PW - PAD * 2, 15 * S, mphS, S);
            }
        });
    }

    /** DS argb with caller opacity applied to the alpha byte. */
    private static int withA(int argb, float mul) {
        int a = (int) (((argb >>> 24) & 0xFF) * mul);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** NVG sparkline — smooth polyline + area fade + live tip (HUD scale aware). */
    private static void sparklineH(long c, org.lwjgl.system.MemoryStack st, float x, float y,
                                   float w, float h, float[] v, float S) {
        float max = 1;
        for (float a : v) if (a > max) max = a;
        for (int i = 0; i <= 2; i++) {
            float gy = y + h - 1 - i * (h - 1) / 2;
            dev.noxclient.gui.nvg.Nvg.rect(c, st, x, gy, w, 1,
                dev.noxclient.gui.nvg.Nvg.fade(dev.noxclient.gui.ds.DS.border(), 0.4f));
        }
        if (v.length < 2) return;
        float[] pts = new float[v.length * 2];
        float sx = w / (v.length - 1);
        for (int i = 0; i < v.length; i++) {
            pts[i * 2] = x + i * sx;
            pts[i * 2 + 1] = y + h - 2 - v[i] / max * (h - 5 * S);
        }
        dev.noxclient.gui.nvg.Nvg.area(c, st, pts, y + h - 1, y,
            dev.noxclient.gui.ds.DS.accent() & 0xFFFFFF, 0.20f);
        dev.noxclient.gui.nvg.Nvg.poly(c, st, pts, 1.4f * S, dev.noxclient.gui.ds.DS.accent());
        dev.noxclient.gui.nvg.Nvg.circle(c, st, pts[pts.length - 2], pts[pts.length - 1],
            1.8f * S, dev.noxclient.gui.ds.DS.accent());
    }

    /** NVG bar chart — rounded bars, newest brightest. */
    private static void barsH(long c, org.lwjgl.system.MemoryStack st, float x, float y,
                              float w, float h, float[] v, float S) {
        float max = 1;
        for (float a : v) if (a > max) max = a;
        dev.noxclient.gui.nvg.Nvg.rect(c, st, x, y + h - 1, w, 1,
            dev.noxclient.gui.ds.DS.border());
        int n = v.length;
        float bw = Math.max(3 * S, (w - (n - 1) * 3 * S) / n);
        for (int i = 0; i < n; i++) {
            float bh = Math.max(2 * S, v[i] / max * (h - 3 * S));
            dev.noxclient.gui.nvg.Nvg.round(c, st, x + i * (bw + 3 * S), y + h - 1 - bh, bw, bh,
                1.5f * S, i == n - 1 ? dev.noxclient.gui.ds.DS.accent()
                    : dev.noxclient.gui.nvg.Nvg.fade(dev.noxclient.gui.ds.DS.accent(), 0.45f));
        }
    }

    /** Scoreboard module — NOX-styled sidebar replacing the vanilla one. */
    private void drawScoreboard(GuiGraphicsExtractor g, Minecraft mc, int w, int h) {
        var m = NoxClient.INSTANCE.modules.get("Scoreboard");
        if (!(m instanceof dev.noxclient.module.render.Scoreboard sb) || !sb.replaces()) return;
        if (sb.mode.get().equals("Hidden")) return;
        var lines = sb.lines();
        if (lines.isEmpty()) return;
        var t = dev.noxclient.gui.theme.Theme.current();
        String title = sb.title();

        int pw = 0;
        for (var l : lines)
            pw = Math.max(pw, Gui.width(mc.font, l.text() + "  " + l.value()));
        pw = Math.max(pw, Gui.width(mc.font, title)) + 18;
        pw = Math.min(pw, w / 3 + 40);
        int ph = (title.isEmpty() ? 0 : 16) + lines.size() * 11 + 10;
        float[] fp = HudLayout.get("scoreboard");
        int x = fp != null ? Math.clamp((int) (fp[0] * w), 2, Math.max(2, w - pw - 2))
            : w - pw - 6;
        int y = fp != null ? Math.clamp((int) (fp[1] * h), 2, Math.max(2, h - ph - 2))
            : h / 2 - ph / 2;
        rep("scoreboard", x, y, pw, ph);

        Gui.roundBorder(g, x, y, pw, ph, 8, t.accent() & 0x44FFFFFF, 0xC8141821);
        int ry = y + 5;
        if (!title.isEmpty()) {
            Gui.textCenter(g, mc.font, title, x + pw / 2, ry + 1, t.accent() | 0xFF000000);
            ry += 10;
            Gui.fill(g, x + 10, ry, x + pw - 10, ry + 1, 0x33FFFFFF);
            ry += 6;
        }
        for (var l : lines) {
            Gui.text(g, mc.font, l.text(), x + 9, ry, 0xFFE8E8F5);
            if (!l.value().isEmpty())
                Gui.text(g, mc.font, l.value(),
                    x + pw - 9 - Gui.width(mc.font, l.value()), ry, 0xFFB8B8CC);
            ry += 11;
        }
    }

    /** Auction module — animated top-center panel + winner celebration. */
    private void drawAuction(GuiGraphicsExtractor g, Minecraft mc, int w, int h) {
        var m = NoxClient.INSTANCE.modules.get("Auction");
        if (!(m instanceof dev.noxclient.module.donutsmp.Auction a) || !a.isEnabled()) return;
        var t = dev.noxclient.gui.theme.Theme.current();
        long now = System.currentTimeMillis();

        if (a.winnerAt >= 0) {   // celebration banner
            float p = Math.min(1f, (now - a.winnerAt) / 5_000f);
            if (p >= 1f) return;
            float in = UICustom.reducedMotion ? 1 : Math.min(1f, (now - a.winnerAt) / 350f);
            float scale = 0.6f + 0.4f * (1f - (1f - in) * (1f - in) * (1f - in));
            float fade = p > 0.8f ? 1f - (p - 0.8f) / 0.2f : 1f;
            int bw2 = 260, bh = 54;
            int bx = w / 2 - bw2 / 2, by = h / 2 - 90;
            g.pose().pushMatrix();
            g.pose().translate(w / 2f, by + bh / 2f);
            g.pose().scale(scale, scale);
            g.pose().translate(-w / 2f, -(by + bh / 2f));
            int al = ((int) (0xF0 * fade)) << 24;
            Gui.shadow(g, bx, by, bw2, bh, 12);
            Gui.roundBorder(g, bx, by, bw2, bh, 12, (0xFFD870) | al, al | 0x141821);
            Gui.textCenter(g, mc.font, "★  AUCTION WON  ★", w / 2, by + 8, 0xFFD870 | al);
            String wn = a.winnerName != null ? a.winnerName + "  ·  $" + Auction.fmt(a.winnerBid) : "no bids";
            Gui.textCenter(g, mc.font, wn, w / 2, by + 22, al | 0xFFFFFF);
            Gui.textCenter(g, mc.font, a.item.get(), w / 2, by + 38, al | 0x8A8A9E);
            if (!UICustom.reducedMotion) {
                double tt = now / 1000.0;
                int[] cc = {0xFFFFD870, t.accent() | 0xFF000000, 0xFF7AFFE0, 0xFFFF6EC7};
                for (int i = 0; i < 14; i++) {
                    double ang = i * (Math.PI * 2 / 14) + tt * 0.8;
                    int cx = w / 2 + (int) (Math.cos(ang) * (bw2 / 2 + 14));
                    int cy = by + bh / 2 + (int) (Math.sin(ang) * (bh / 2 + 12));
                    Gui.circle(g, cx, cy, 2, (cc[i % cc.length] & 0x00FFFFFF) | al);
                }
            }
            g.pose().popMatrix();
            return;
        }

        int pw = 190, ph = a.topThree.on() && a.bids.size() > 1 ? 62 : 44;
        float[] fp = HudLayout.get("auction");
        int x = fp != null ? Math.clamp((int) (fp[0] * w), 2, Math.max(2, w - pw - 2))
            : w / 2 - pw / 2;
        int y = fp != null ? Math.clamp((int) (fp[1] * h), 2, Math.max(2, h - ph - 2)) : 26;
        rep("auction", x, y, pw, ph);

        Gui.shadow(g, x, y, pw, ph, 9);
        Gui.roundBorder(g, x, y, pw, ph, 9, 0x66FFD870, 0xE6141821);
        Gui.text(g, mc.font, "AUCTION · " + a.item.get(), x + 9, y + 6, 0xFFFFD870);
        long left = a.millisLeft();
        String tstr = left >= 60_000 ? (left / 60_000) + "m" + (left / 1000 % 60) + "s" : (left / 1000) + "s";
        Gui.text(g, mc.font, tstr, x + pw - 9 - Gui.width(mc.font, tstr), y + 6, 0xFF8A8A9E);
        int bw = pw - 18;
        Gui.pill(g, x + 9, y + 17, bw, 3, 0xFF2A2A38);
        Gui.pill(g, x + 9, y + 17, Math.max(2, (int) (bw * a.progress())), 3, 0xFFFFD870);

        if (a.leader != null) {
            float pl = UICustom.reducedMotion ? 1 : Math.min(1f, (now - a.leaderChangedAt) / 300f);
            int lcol = lerpColor(0xFFFFFFFF, 0xFF55DD66, pl);
            String lead = "♛ " + a.leader + "  $" + Auction.fmt(a.leaderAmount);
            Gui.textBold(g, mc.font, lead, x + 9, y + 25, lcol);
            String bc = a.bids.size() + " bid" + (a.bids.size() == 1 ? "" : "s");
            Gui.text(g, mc.font, bc, x + pw - 9 - Gui.width(mc.font, bc), y + 25, 0xFF8A8A9E);
            if (a.topThree.on()) {
                int ry = y + 38, rank = 0;
                for (var e : a.top()) {
                    if (++rank == 1) continue;
                    Gui.text(g, mc.font, rank + ". " + e.getKey(), x + 9, ry, 0xFFB8B8CC);
                    String amt = "$" + Auction.fmt(e.getValue());
                    Gui.text(g, mc.font, amt, x + pw - 9 - Gui.width(mc.font, amt), ry, 0xFF8A8A9E);
                    ry += 11;
                }
            }
        } else {
            Gui.text(g, mc.font, "waiting for bids — /pay me!", x + 9, y + 25, 0xFF8A8A9E);
        }
    }

    private static String fmt(long v) {
        if (Math.abs(v) >= 1e6) return String.format("%.1fM", v / 1e6);
        if (Math.abs(v) >= 1e3) return String.format("%.1fK", v / 1e3);
        return String.valueOf(v);
    }

    private void drawSpotify(GuiGraphicsExtractor g, Minecraft mc, dev.noxclient.gui.theme.Theme t,
                             int sw, int sh) {
        var m = NoxClient.INSTANCE.modules.get("SpotifyHud");
        if (!(m instanceof dev.noxclient.module.render.SpotifyHud sp) || !sp.isEnabled()) return;
        if (!dev.noxclient.media.MediaService.active()
            || (!dev.noxclient.media.MediaService.playing() && !sp.pausedToo.on())) return;
        String line = "♫ " + dev.noxclient.media.MediaService.artist()
            + " — " + dev.noxclient.media.MediaService.title();
        int w = Math.min(Gui.width(mc.font, line), Math.min(220, sw - 16));
        int ph2 = sp.progressBar.on() ? 22 : 14;
        float[] pp = HudLayout.get("spotify");
        int x = pp != null ? Math.clamp((int) (pp[0] * sw), 2, Math.max(2, sw - w - 16))
            : Math.clamp((int) (double) sp.posX.get(), 2, Math.max(2, sw - w - 16));
        int y = pp != null ? Math.clamp((int) (pp[1] * sh), 2, Math.max(2, sh - ph2 - 2))
            : Math.clamp((int) (double) sp.posY.get(), 2, Math.max(2, sh - ph2 - 2));
        drawTwitch(g, mc, t, sw, sh);
        rep("spotify", x, y, w + 14, ph2);
        dev.noxclient.gui.Gui.roundBorder(g, x, y, w + 14, sp.progressBar.on() ? 22 : 14, 6,
            t.accent() & 0x66FFFFFF, 0xB8141420);
        String clipped = line;
        while (Gui.width(mc.font, clipped + "…") > w && !clipped.isEmpty())
            clipped = clipped.substring(0, clipped.length() - 1);
        if (!clipped.equals(line)) clipped += "…";
        Gui.text(g, mc.font, clipped, x + 7, y + 4, 0xFF1DB954);
        if (sp.progressBar.on()) {
            int bw = w + 2;
            dev.noxclient.gui.Gui.pill(g, x + 6, y + 15, bw, 3, 0xFF2A2A38);
            dev.noxclient.gui.Gui.pill(g, x + 6, y + 15,
                Math.max(2, (int) (bw * dev.noxclient.media.MediaService.progress())), 3, 0xFF1DB954);
        }
    }

    /** Twitch chat HUD — purple-accent card, live dot, last N IRC lines. */
    private void drawTwitch(GuiGraphicsExtractor g, Minecraft mc, dev.noxclient.gui.theme.Theme t,
                            int sw, int sh) {
        var m = NoxClient.INSTANCE.modules.get("TwitchChat");
        if (!(m instanceof dev.noxclient.module.render.TwitchChat tc) || !tc.isEnabled()) return;
        var chat = dev.noxclient.media.TwitchService.lines();
        if (chat.isEmpty() && dev.noxclient.media.TwitchService.status().equals("no channel")) return;
        int head = tc.liveDot.on() ? 12 : 0;
        int ph = head + chat.size() * 11 + 8;
        int w = 200;
        float[] pp = HudLayout.get("twitch");
        int x = pp != null ? Math.clamp((int) (pp[0] * sw), 2, Math.max(2, sw - w - 16))
            : Math.clamp((int) (double) tc.posX.get(), 2, Math.max(2, sw - w - 16));
        int y = pp != null ? Math.clamp((int) (pp[1] * sh), 2, Math.max(2, sh - ph - 2))
            : Math.clamp((int) (double) tc.posY.get(), 2, Math.max(2, sh - ph - 2));
        rep("twitch", x, y, w, ph);
        dev.noxclient.gui.Gui.round(g, x, y, w, ph, 7, 0xB8141420);
        dev.noxclient.gui.Gui.pill(g, x, y, 3, ph, 0xFF9146FF);
        int ty = y + 5;
        if (head > 0) {
            int dc = dev.noxclient.media.TwitchService.live() ? 0xFFEB3333 : 0xFF555566;
            dev.noxclient.gui.Gui.circle(g, x + 10, ty + 4, 3, dc);
            Gui.text(g, mc.font, dev.noxclient.media.TwitchService.live() ? "LIVE" : "offline",
                x + 18, ty + 1, 0xFFB8B8CC);
            Gui.text(g, mc.font, "#" + tc.channel.get(), x + w - 8
                - Gui.width(mc.font, "#" + tc.channel.get()), ty + 1, 0xFF9146FF);
            ty += head;
        }
        for (String line : chat) {
            String clipped = line;
            while (Gui.width(mc.font, clipped + "…") > w - 14 && !clipped.isEmpty())
                clipped = clipped.substring(0, clipped.length() - 1);
            if (!clipped.equals(line)) clipped += "…";
            Gui.text(g, mc.font, clipped, x + 8, ty, 0xFFD8D8E8);
            ty += 11;
        }
    }

    private void drawEntity(GuiGraphicsExtractor g, Minecraft mc, Entity e, double[] b) {
        var modules = NoxClient.INSTANCE.modules;
        var esp = modules.get("ESP");
        var pesp = modules.get("PlayerESP");
        var tr = modules.get("Tracers");
        boolean isPlayer = e instanceof Player;
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        int x0 = (int) b[0], y0 = (int) b[1], x1 = (int) b[2], y1 = (int) b[3];
        int cx = (x0 + x1) / 2, bw = Math.max(4, x1 - x0), bh = Math.max(6, y1 - y0);
        // grow tiny far-away hits so they stay visible, clamp to screen
        if (bw < 10 || bh < 14) {
            int grow = Math.max(10 - bw, (14 - bh) / 2);
            x0 -= grow / 2; x1 += grow / 2; y0 -= grow; y1 += grow / 2;
        }
        x0 = Math.max(-w, x0); x1 = Math.min(w * 2, x1);
        y0 = Math.max(-h, y0); y1 = Math.min(h * 2, y1);
        bw = x1 - x0; bh = y1 - y0;

        if (isPlayer && pesp instanceof PlayerESP pe && pe.isEnabled()
            && mc.player.distanceToSqr(e) < pe.maxDist.get() * pe.maxDist.get()) {
            if (pe.box.on()) {
                int pc = dev.noxclient.module.misc.Friends.isFriend(e) ? 0xFF40E080 : 0xFFFF4040;
                Gui.fill(g, x0, y0, x1, y1, pc & 0x22FFFFFF);
                g.outline(x0, y0, bw, bh, pc);
            }
            String label = pe.name.on() ? e.getName().getString() : "";
            if (pe.distance.on()) label += (label.isEmpty() ? "" : " ") + (int) Math.sqrt(mc.player.distanceToSqr(e)) + "m";
            if (pe.health.on() && e instanceof net.minecraft.world.entity.LivingEntity le)
                label += " " + (int) le.getHealth() + "hp";
            if (!label.isEmpty())
                Gui.textCenter(g, mc.font, label, cx, y0 - 10, 0xFFFF6666);
        } else if (esp instanceof ESP es && es.isEnabled()) {
            boolean draw = (isPlayer && es.players.on())
                || (!isPlayer && e instanceof net.minecraft.world.entity.item.ItemEntity && es.items.on())
                || (!isPlayer && e instanceof net.minecraft.world.entity.LivingEntity
                    && !(e instanceof net.minecraft.world.entity.item.ItemEntity) && es.mobs.on()
                    && (!es.hostileOnly.on()
                        || e instanceof net.minecraft.world.entity.monster.Monster));
            if (draw) {
                int col = es.color.get() | 0xFF000000;
                switch (es.style.get()) {
                    case "Glow" -> {
                        Gui.fill(g, x0 - 1, y0 - 1, x1 + 1, y1 + 1,
                            (es.color.get() & 0x00FFFFFF) | 0x3A000000);
                        Gui.fill(g, x0, y0, x1, y1, (es.color.get() & 0x00FFFFFF) | 0x88000000);
                        g.outline(x0 - 1, y0 - 1, bw + 2, bh + 2, col);
                    }
                    case "Box" -> {
                        Gui.fill(g, x0, y0, x1, y1, es.color.get());
                        g.outline(x0, y0, bw, bh, col);
                    }
                    default -> g.outline(x0, y0, bw, bh, col);
                }
            }
        }
        if (isPlayer && tr instanceof Tracers t && t.isEnabled() && t.players.on()
            && mc.player.distanceToSqr(e) < t.maxDist.get() * t.maxDist.get()) {
            line(g, w / 2, h - 20, cx, y1, t.playerCol.get() | 0xFF000000);
        }
    }

    /**
     * Projects the 8 bounding-box corners → tight screen rect
     * [minX, minY, maxX, maxY, maxDepth]. Corners behind the camera used to
     * drop the whole box — the near-range flicker. Instead we clip the 12
     * box edges against the near plane (w is linear in world space, so a
     * world-space lerp hits w=0 exactly) and keep the visible portion.
     * Null only when the entire box is behind the camera.
     */
    private static final int[][] BOX_EDGES = {
        {0,1},{2,3},{4,5},{6,7},{0,2},{1,3},{4,6},{5,7},{0,4},{1,5},{2,6},{3,7}};
    private final Vector4f[] boxClip = {new Vector4f(), new Vector4f(), new Vector4f(), new Vector4f(),
        new Vector4f(), new Vector4f(), new Vector4f(), new Vector4f()};
    private final boolean[] boxVis = new boolean[8];

    private double[] projectBox(net.minecraft.world.phys.AABB bb, Vec3 cp,
                                Matrix4f mat, int w, int h) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, depth = 0;
        boolean any = false;
        for (int i = 0; i < 8; i++) {
            double cx = (i & 1) == 0 ? bb.minX : bb.maxX,
                   cy = (i & 2) == 0 ? bb.minY : bb.maxY,
                   cz = (i & 4) == 0 ? bb.minZ : bb.maxZ;
            Vector4f v = boxClip[i].set((float) (cx - cp.x), (float) (cy - cp.y),
                (float) (cz - cp.z), 1f).mul(mat);
            boxVis[i] = v.w > 0.01f;
        }
        for (int[] e : BOX_EDGES) {
            for (int i : e) {
                if (!boxVis[i]) continue;
                double[] p = projectClip(boxClip[i], w, h);
                minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
                minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
                depth = Math.max(depth, p[2]); any = true;
            }
            if (boxVis[e[0]] == boxVis[e[1]]) continue;
            Vector4f a = boxClip[e[0]], b = boxClip[e[1]];
            float t = (0.01f - a.w) / (b.w - a.w);
            // clip point is behind the near plane by construction — evaluate
            // its clip-space position directly instead of a full re-project
            float cx = a.x + (b.x - a.x) * t, cy = a.y + (b.y - a.y) * t, cw = 0.01f;
            double sx = (cx / cw * 0.5 + 0.5) * w, sy = (0.5 - cy / cw * 0.5) * h;
            minX = Math.min(minX, sx); maxX = Math.max(maxX, sx);
            minY = Math.min(minY, sy); maxY = Math.max(maxY, sy);
            any = true;
        }
        return any ? new double[] {minX, minY, maxX, maxY, depth} : null;
    }

    private double[] projectClip(Vector4f v, int w, int h) {
        float ndcX = v.x / v.w, ndcY = v.y / v.w;
        return new double[] {(ndcX * 0.5f + 0.5f) * w, (0.5f - ndcY * 0.5f) * h, v.w};
    }

    private void drawMarks(GuiGraphicsExtractor g, Vec3 cp, Matrix4f mat, int w, int h,
                           java.util.Map<net.minecraft.core.BlockPos, Integer> marks) {
        if (marks.isEmpty()) return;
        for (var en : marks.entrySet()) {
            double[] p = project(Vec3.atCenterOf(en.getKey()), cp, mat, w, h);
            if (p == null || p[2] > 200) continue;
            // p[2] is view-space depth in blocks — uncapped 900/depth produced
            // screen-filling squares for close markers
            int s = (int) Math.max(3, Math.min(14, 160 / p[2]));
            Gui.fill(g, (int) p[0] - s, (int) p[1] - s, (int) p[0] + s, (int) p[1] + s, en.getValue() & 0x55FFFFFF);
            g.outline((int) p[0] - s, (int) p[1] - s, s * 2, s * 2, en.getValue());
        }
    }

    /** world → [screenX, screenY, distance]; null when behind camera */
    private double[] project(Vec3 world, Vec3 camPos, Matrix4f mat, int w, int h) {
        Vector4f v = new Vector4f((float) (world.x - camPos.x), (float) (world.y - camPos.y),
            (float) (world.z - camPos.z), 1f);
        v.mul(mat);
        if (v.w <= 0.01f) return null;
        float ndcX = v.x / v.w, ndcY = v.y / v.w;
        return new double[] {(ndcX * 0.5f + 0.5f) * w, (0.5f - ndcY * 0.5f) * h, v.w};
    }

    private void line(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1), steps = Math.max(dx, dy);
        if (steps == 0) { Gui.fill(g, x1, y1, x1 + 1, y1 + 1, color); return; }
        for (int i = 0; i <= steps; i += 2)
            Gui.fill(g, x1 + (x2 - x1) * i / steps, y1 + (y2 - y1) * i / steps,
                x1 + (x2 - x1) * i / steps + 1, y1 + (y2 - y1) * i / steps + 1, color);
    }

    // ------------------------------------------------------------------
    // DonutSMP suite — BaseFinder list, region grid, debris counter,
    // BaseESP cluster cards, radial wheel.

    private static final String[] DIR8 = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    private static String dir8(double dx, double dz) {
        double ang = Math.atan2(dx, -dz);            // 0 = north(-z), cw
        int i = (int) Math.round(ang / (Math.PI / 4)) & 7;
        return DIR8[i];
    }

    /** BaseFinder — Prestige-style detection list, top-left. */
    private void drawBaseFinder(GuiGraphicsExtractor g, Minecraft mc, int w, int h) {
        var m = NoxClient.INSTANCE.modules.get("BaseFinder");
        if (!(m instanceof dev.noxclient.module.donutsmp.BaseFinder bf)
            || !bf.isEnabled() || !bf.showHud.on()) return;
        var es = dev.noxclient.util.BaseIntel.top(mc.player.blockPosition(),
            Math.max(bf.maxRows.intVal() + 1, 8));
        int min = bf.minConf.intVal(), rows = bf.maxRows.intVal();
        var list = new java.util.ArrayList<dev.noxclient.util.BaseIntel.Entry>();
        int more = 0;
        for (var e : es) {
            if (e.pct < min) continue;
            if (list.size() < rows) list.add(e); else more++;
        }
        int total = dev.noxclient.util.BaseIntel.entries().size();
        int pw = 172, ph = 18 + list.size() * 26 + (more > 0 || list.isEmpty() ? 12 : 0);
        float[] fp = HudLayout.get("basefinder");
        int x = fp != null ? Math.clamp((int) (fp[0] * w), 2, Math.max(2, w - pw - 2)) : 4;
        int y = fp != null ? Math.clamp((int) (fp[1] * h), 2, Math.max(2, h - ph - 2)) : 64;
        rep("basefinder", x, y, pw, ph);
        var t = dev.noxclient.gui.theme.Theme.current();

        Gui.shadow(g, x, y, pw, ph, 8);
        Gui.roundBorder(g, x, y, pw, ph, 8, t.accent() | 0x66000000, 0xE6141821);
        Gui.textBold(g, mc.font, "BaseFinder", x + 8, y + 6, t.accent());
        String cnt = total + " nearby";
        Gui.text(g, mc.font, cnt, x + pw - 8 - Gui.width(mc.font, cnt), y + 7, 0xFF8A8A9E);

        int ry = y + 18;
        if (list.isEmpty())
            Gui.text(g, mc.font, "scanning…", x + 8, ry + 2, 0xFF6A6A7E);
        for (int i = 0; i < list.size(); i++) {
            var e = list.get(i);
            double dx = e.cp.getMiddleBlockX() - mc.player.getX();
            double dz = e.cp.getMiddleBlockZ() - mc.player.getZ();
            int dist = (int) Math.hypot(dx, dz);
            int col = e.pct >= 70 ? 0xFF55DD66 : e.pct >= 40 ? 0xFFFFCC44 : 0xFFFF8844;
            Gui.text(g, mc.font, String.valueOf(i + 1), x + 8, ry, 0xFF6A6A7E);
            Gui.textBold(g, mc.font, e.type, x + 18, ry, 0xFFE8E8F2);
            String pc2 = e.pct + "%";
            Gui.textBold(g, mc.font, pc2, x + pw - 8 - Gui.width(mc.font, pc2), ry, col);
            String sub = dir8(dx, dz) + " " + dist + "m  /  Y " + String.format("%.1f", e.avgY)
                + "  /  " + e.sections + "/" + e.scanned + " sections";
            Gui.text(g, mc.font, sub, x + 18, ry + 10, 0xFF8A8A9E);
            int bw = pw - 18 - Gui.width(mc.font, pc2) - 26;
            Gui.pill(g, x + 18, ry + 21, bw, 2, 0xFF2A2A38);
            Gui.pill(g, x + 18, ry + 21, Math.max(2, bw * e.pct / 100), 2, col);
            ry += 26;
        }
        if (more > 0)
            Gui.text(g, mc.font, "+" + more + " more nearby", x + 8, ry + 2, 0xFF6A6A7E);
    }

    /** RegionMap — DonutSMP shard grid, top-right. */
    private void drawRegionMap(GuiGraphicsExtractor g, Minecraft mc, int w, int h) {
        var m = NoxClient.INSTANCE.modules.get("RegionMap");
        if (!(m instanceof dev.noxclient.module.donutsmp.RegionMap rm) || !rm.isEnabled()
            || !mc.level.dimension().toString().contains("overworld")) return;
        int cols = rm.cols.intVal(), rows = rm.rows.intVal();
        long cs = (long) (rm.cellSize.get() * 1000);
        int cw = rm.cellPx.intVal();
        int pw = cols * cw + 16, ph = rows * cw + 34;
        float[] fp = HudLayout.get("regionmap");
        int x = fp != null ? Math.clamp((int) (fp[0] * w), 2, Math.max(2, w - pw - 2)) : w - pw - 4;
        int y = fp != null ? Math.clamp((int) (fp[1] * h), 2, Math.max(2, h - ph - 2)) : 4;
        rep("regionmap", x, y, pw, ph);
        var t = dev.noxclient.gui.theme.Theme.current();

        double px = mc.player.getX(), pz = mc.player.getZ();
        int cx = (int) Math.floor(px / cs) + cols / 2, cz = (int) Math.floor(pz / cs) + rows / 2;
        int cur = rm.regionId(cx, cz);

        Gui.shadow(g, x, y, pw, ph, 8);
        Gui.roundBorder(g, x, y, pw, ph, 8, t.accent() | 0x66000000, 0xE6141821);
        String head = "DONUT REGIONS";
        Gui.textBold(g, mc.font, head, x + 8, y + 6, t.accent());
        String cur2 = (cur >= 0 ? String.valueOf(cur) : "—") + " " + rm.clusterName(cx, cz);
        Gui.text(g, mc.font, cur2, x + pw - 8 - Gui.width(mc.font, cur2), y + 7, 0xFF8A8A9E);

        int gy = y + 16;
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            int id = rm.regionId(c - cols / 2, r - rows / 2);
            int col = rm.clusterColor(c - cols / 2, r - rows / 2);
            boolean here = (c - cols / 2) == cx && (r - rows / 2) == cz;
            Gui.fill(g, x + 8 + c * cw, gy + r * cw, x + 8 + c * cw + cw - 1, gy + r * cw + cw - 1,
                here ? col : (col & 0x00FFFFFF) | 0x99000000);
            if (here) g.outline(x + 8 + c * cw - 1, gy + r * cw - 1, cw + 1, cw + 1, 0xFFFFFFFF);
            if (id > 0 && cw >= 8) {
                String s = String.valueOf(id);
                Gui.text(g, mc.font, s,
                    x + 8 + c * cw + (cw - Gui.width(mc.font, s)) / 2 - 1,
                    gy + r * cw + (cw - 7) / 2, here ? 0xFF101018 : 0xCCFFFFFF);
            }
        }
        String coords = "X:" + (int) px + " Z:" + (int) pz + "  " + rm.clusterName(cx, cz);
        Gui.text(g, mc.font, coords, x + 8, y + ph - 10, 0xFF8A8A9E);
    }

    /** DebrisFinder — "AREAS n / BLOCKS m" counter card. */
    private void drawDebrisPanel(GuiGraphicsExtractor g, Minecraft mc, int w, int h) {
        var m = NoxClient.INSTANCE.modules.get("DebrisFinder");
        if (!(m instanceof dev.noxclient.module.donutsmp.DebrisFinder df) || !df.isEnabled()) return;
        int pw = 148, ph = 42;
        float[] fp = HudLayout.get("debrisfinder");
        int x = fp != null ? Math.clamp((int) (fp[0] * w), 2, Math.max(2, w - pw - 2)) : 4;
        int y = fp != null ? Math.clamp((int) (fp[1] * h), 2, Math.max(2, h - ph - 2)) : h - ph - 40;
        rep("debrisfinder", x, y, pw, ph);
        var t = dev.noxclient.gui.theme.Theme.current();
        Gui.shadow(g, x, y, pw, ph, 8);
        Gui.roundBorder(g, x, y, pw, ph, 8, 0x66FFAA33, 0xE6141821);
        Gui.textBold(g, mc.font, "ANCIENT DEBRIS", x + 8, y + 6, 0xFFFFAA33);
        Gui.text(g, mc.font, "Areas " + df.areas(), x + 8, y + 17, 0xFFE8E8F2);
        String bl = "Blocks " + df.blocks();
        Gui.text(g, mc.font, bl, x + pw - 8 - Gui.width(mc.font, bl), y + 17, 0xFF55DD66);
        Gui.text(g, mc.font, "Possible debris in outlined areas", x + 8, y + 28, 0xFF8A8A9E);
    }

    /** StaffLive — "who of the staff list is online right now" card. */
    private void drawStaffLive(GuiGraphicsExtractor g, Minecraft mc, int w, int h) {
        var m = NoxClient.INSTANCE.modules.get("StaffLive");
        if (!(m instanceof dev.noxclient.module.donutsmp.StaffLive sl) || !sl.isEnabled()
            || !sl.panel.on()) return;
        var online = dev.noxclient.module.donutsmp.StaffLive.online();
        java.util.List<String> wanted = new java.util.ArrayList<>();
        for (String n : sl.staff.get().split(",")) {
            String t = n.trim().toLowerCase(java.util.Locale.ROOT);
            if (!t.isEmpty() && !wanted.contains(t)) wanted.add(t);
        }
        java.util.List<String> off = new java.util.ArrayList<>();
        if (sl.showOffline.on())
            for (String s : wanted) if (!online.containsKey(s)) off.add(s);
        int rows = online.size() + off.size();
        int pw = 150, ph = 20 + Math.max(1, rows) * 12;
        float[] fp = HudLayout.get("stafflive");
        int x = fp != null ? Math.clamp((int) (fp[0] * w), 2, Math.max(2, w - pw - 2)) : w - pw - 4;
        int y = fp != null ? Math.clamp((int) (fp[1] * h), 2, Math.max(2, h - ph - 2)) : h - ph - 60;
        rep("stafflive", x, y, pw, ph);
        var t = dev.noxclient.gui.theme.Theme.current();
        int edge = online.isEmpty() ? 0x668A8A9E : 0x66FF5555;
        Gui.shadow(g, x, y, pw, ph, 8);
        Gui.roundBorder(g, x, y, pw, ph, 8, edge, 0xE6141821);
        Gui.textBold(g, mc.font, "STAFF", x + 8, y + 6,
            online.isEmpty() ? 0xFF8A8A9E : 0xFFFF5555);
        String cnt = online.size() + " online";
        Gui.text(g, mc.font, cnt, x + pw - 8 - Gui.width(mc.font, cnt), y + 7, 0xFF8A8A9E);
        int ry = y + 20;
        if (rows == 0) {
            Gui.text(g, mc.font, "none listed", x + 8, ry, 0xFF6A6A7E); return; }
        long now = System.currentTimeMillis();
        for (var e : online.entrySet()) {
            long dur = (now - e.getValue()) / 60000;
            String durS = dur < 1 ? "now" : dur < 60 ? dur + "m" : dur / 60 + "h" + dur % 60 + "m";
            Gui.text(g, mc.font, "●", x + 8, ry, 0xFF55DD66);
            Gui.text(g, mc.font, e.getKey(), x + 16, ry, 0xFFE8E8F2);
            Gui.text(g, mc.font, durS, x + pw - 8 - Gui.width(mc.font, durS), ry, 0xFF8A8A9E);
            ry += 12;
        }
        for (String s : off) {
            Gui.text(g, mc.font, "○", x + 8, ry, 0xFF5A5A6E);
            Gui.text(g, mc.font, s, x + 16, ry, 0xFF5A5A6E);
            ry += 12;
        }
    }

    /** BaseESP — floating verdict cards over the hottest storage clusters. */
    private void drawBaseEspCards(GuiGraphicsExtractor g, Minecraft mc,
                                  net.minecraft.client.Camera cam, Vec3 cp, Matrix4f mat,
                                  int w, int h) {
        var m = NoxClient.INSTANCE.modules.get("StorageESP");
        if (!(m instanceof dev.noxclient.module.donutsmp.StorageESP se) || !se.isEnabled()
            || !se.cards.on()) return;
        int shown = 0;
        for (var cl : se.clusters()) {
            if (++shown > 3) break;
            double[] p = project(Vec3.atCenterOf(cl.pos), cp, mat, w, h);
            if (p == null || p[2] < 6 || p[2] > 320) continue;
            int cw2 = 128, ch2 = 40;
            int cx2 = (int) p[0] - cw2 / 2, cy2 = (int) p[1] - ch2 - 10;
            String dist = (int) p[2] + "m";
            Gui.shadow(g, cx2, cy2, cw2, ch2, 7);
            Gui.roundBorder(g, cx2, cy2, cw2, ch2, 7, 0x6655DD66, 0xE6141821);
            Gui.textBold(g, mc.font, "BASE ESP", cx2 + 7, cy2 + 5, 0xFF55DD66);
            Gui.text(g, mc.font, dist, cx2 + cw2 - 7 - Gui.width(mc.font, dist), cy2 + 6, 0xFF8A8A9E);
            int vcol = cl.verdict() == 2 ? 0xFF55DD66 : cl.verdict() == 1 ? 0xFFFFCC44 : 0xFFFF8844;
            Gui.textBold(g, mc.font, cl.verdictName(), cx2 + 7, cy2 + 14, vcol);
            String sc = (int) cl.score + " score";
            Gui.text(g, mc.font, sc, cx2 + cw2 - 7 - Gui.width(mc.font, sc), cy2 + 15, 0xFF8A8A9E);
            Gui.text(g, mc.font, cl.containers + " containers, " + cl.hoppers + " hoppers",
                cx2 + 7, cy2 + 24, 0xFFB8B8CC);
            if (cl.verdict() == 0)
                Gui.text(g, mc.font, "Not enough clear condition evidence", cx2 + 7, cy2 + 33, 0xFF6A6A7E);
        }
    }

    /** DonutWheel — radial action menu, centered; selection follows cursor angle. */
    private void drawWheel(GuiGraphicsExtractor g, Minecraft mc, int w, int h) {
        var m = NoxClient.INSTANCE.modules.get("DonutWheel");
        if (!(m instanceof dev.noxclient.module.donutsmp.DonutWheel dw) || !dw.isEnabled()) return;
        var acts = dw.actions();
        if (acts.isEmpty()) return;
        dw.vw = w; dw.vh = h;
        var t = dev.noxclient.gui.theme.Theme.current();
        int cx = w / 2, cy = h / 2, rIn = 34, rOut = 86;
        int sel = dw.selected(mc, w, h);
        long now = System.currentTimeMillis();
        float in = UICustom.reducedMotion ? 1 : Math.min(1f, (now - dw.openedAt) / 160f);
        float ease = 1f - (1f - in) * (1f - in);
        int n = acts.size();
        for (int i = 0; i < n; i++) {
            double a0 = -Math.PI / 2 + i * (Math.PI * 2 / n);
            double a1 = a0 + Math.PI * 2 / n;
            double am = (a0 + a1) / 2;
            int seg = 48;
            int col = i == sel ? (t.accent() & 0x00FFFFFF) | 0xDD000000 : 0xD8141821;
            for (int sgi = 0; sgi <= seg; sgi++) {
                double a = a0 + (a1 - a0) * sgi / seg;
                int x1 = cx + (int) (Math.cos(a) * rIn * ease), y1 = cy + (int) (Math.sin(a) * rIn * ease);
                int x2 = cx + (int) (Math.cos(a) * rOut * ease), y2 = cy + (int) (Math.sin(a) * rOut * ease);
                // radial spokes ~1px apart at the outer edge fill the sector
                for (int r = 0; r <= rOut - rIn; r += 2) {
                    int xr = x1 + (x2 - x1) * r / (rOut - rIn);
                    int yr = y1 + (y2 - y1) * r / (rOut - rIn);
                    Gui.fill(g, xr, yr, xr + 2, yr + 2, col);
                }
            }
            // gap between sectors
            double ga = a1;
            line(g, cx + (int) (Math.cos(ga) * rIn * ease), cy + (int) (Math.sin(ga) * rIn * ease),
                cx + (int) (Math.cos(ga) * rOut * ease), cy + (int) (Math.sin(ga) * rOut * ease), 0xFF0A0C12);
            int lx = cx + (int) (Math.cos(am) * ((rIn + rOut) / 2f * ease));
            int ly = cy + (int) (Math.sin(am) * ((rIn + rOut) / 2f * ease));
            String lbl = acts.get(i).name();
            Gui.textCenter(g, mc.font, lbl.length() > 12 ? lbl.substring(0, 11) + "…" : lbl,
                lx, ly - 3, i == sel ? 0xFFFFFFFF : 0xFFB8B8CC);
        }
        Gui.circle(g, cx, cy, (int) (rIn * ease) - 2, 0xF0141821);
        g.outline(cx - (int) (rIn * ease) + 2, cy - (int) (rIn * ease) + 2,
            ((int) (rIn * ease) - 2) * 2, ((int) (rIn * ease) - 2) * 2, 0xFF2A2A38);
        Gui.textCenterBold(g, mc.font, sel >= 0 ? acts.get(sel).name() : "wheel", cx, cy - 4, t.accent());
    }
}
