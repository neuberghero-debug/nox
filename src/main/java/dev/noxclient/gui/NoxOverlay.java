package dev.noxclient.gui;

import dev.noxclient.NoxClient;
import dev.noxclient.module.donutsmp.AntiESPBypass;
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
        drawMarks(g, cp, mat, w, h, AntiESPBypass.MARKS);
        drawMarks(g, cp, mat, w, h, dev.noxclient.module.donutsmp.OreESP.HITS);
        drawMarks(g, cp, mat, w, h, dev.noxclient.module.donutsmp.HoleESP.HITS);
        drawIntelMarks(g, mc, cp, mat, w, h);
        float hs = dev.noxclient.gui.UICustom.hudScale;
        // auto-fit: on small logical screens (high vanilla GUI scale) the HUD
        // shrinks proportionally instead of overflowing; floor keeps it legible
        float fit = Math.min(1f, Math.min(w / 420f, h / 240f));
        hs *= Math.max(fit, 0.45f);
        if (hs != 1f) {
            g.pose().pushMatrix();
            g.pose().scale(hs, hs);
        }
        int sw = (int) (w / hs), sh = (int) (h / hs);
        drawWidgets(g, mc, sw, sh);
        drawTargetHud(g, mc, sw, sh);
        drawSessionStats(g, mc, sw, sh);
        if (hs != 1f) g.pose().popMatrix();
        var hm = NoxClient.INSTANCE.modules.get("HudWidgets");
        boolean compassOn = hm instanceof dev.noxclient.module.render.HudWidgets hw
            && hw.isEnabled() && hw.compass.on() && mc.player != null;
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
        if (hw.coords.on() && mc.player != null) {
            var p = mc.player.blockPosition();
            lines.add(new String[]{p.getX() + " " + p.getY() + " " + p.getZ(), "text"});
        }
        if (hw.fps.on()) lines.add(new String[]{mc.getFps() + " fps", "text"});
        if (hw.ping.on() && mc.getConnection() != null && mc.player != null) {
            var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null) lines.add(new String[]{info.getLatency() + " ms", "text"});
        }
        if (hw.nether.on() && mc.player != null) {
            var p = mc.player.blockPosition();
            boolean isNether = mc.level.dimension().toString().contains("nether");
            lines.add(new String[]{isNether
                ? "overworld: " + p.getX() * 8 + " " + p.getZ() * 8
                : "nether: " + p.getX() / 8 + " " + p.getZ() / 8, "nether"});
        }
        if (hw.facing.on() && mc.player != null) {
            lines.add(new String[]{switch (mc.player.getDirection()) {
                case NORTH -> "N (-Z)"; case SOUTH -> "S (+Z)";
                case EAST -> "E (+X)"; case WEST -> "W (-X)";
                default -> "?";
            }, "text"});
        }
        if (hw.bps.on() && mc.player != null)
            lines.add(new String[]{String.format("%.1f b/s",
                mc.player.getDeltaMovement().horizontalDistance() * 20), "text"});
        if (hw.tps.on()) lines.add(new String[]{tpsText(), "text"});
        if (hw.session.on()) {
            long s3 = (System.currentTimeMillis() - sessionStart) / 1000;
            lines.add(new String[]{String.format("session %d:%02d", s3 / 60, s3 % 60), "text"});
        }
        if (hw.effects.on() && mc.player != null) {
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
        int infoTop = bottom ? h - 4 - lines.size() * 11 : 4;
        int y = infoTop;
        for (var l : lines) {
            int col = switch (l[1]) {
                case "nether" -> 0xFFB44DFF; case "effect" -> 0xFF8AE0FF;
                case "text" -> t.text();
                default -> (int) Long.parseLong(l[1], 16);
            };
            int lx = rightSide ? w - 6 - rightPad - Gui.width(mc.font, l[0]) : 6;
            Gui.textShadow(g, mc.font, l[0], lx, y, col); y += 11;
        }
        int infoBottom = y;   // first free row below the info block

        // arraylist — glass pills w/ accent edge + slide-in animation
        if (hw.arraylist.on()) {
            var mods = NoxClient.INSTANCE.modules.all().stream()
                .filter(dev.noxclient.module.Module::isEnabled)
                .sorted((a, b2) -> Integer.compare(
                    Gui.width(mc.font, b2.name), Gui.width(mc.font, a.name))).toList();
            boolean lRight = hw.listSide.get().contains("Right");
            boolean lBot = hw.listSide.get().contains("Bottom");
            // cap the list to what actually fits — overflow becomes "+N more"
            int maxRows = Math.max(3, (h - 8) / 13);
            int hidden = Math.max(0, mods.size() - maxRows);
            if (hidden > 0) mods = mods.subList(0, maxRows - 1);
            // same corner as the info block? start below it instead of overlapping
            boolean sameCorner = lRight == rightSide && lBot == bottom;
            int ay = lBot ? h - 4 - (mods.size() + (hidden > 0 ? 1 : 0)) * 13
                          : (sameCorner && !lines.isEmpty() ? infoBottom + 3 : 4);
            if (sameCorner && lBot && !lines.isEmpty())
                ay = Math.min(ay, infoTop - 3 - (mods.size() + (hidden > 0 ? 1 : 0)) * 13);
            long nowMs = System.currentTimeMillis();
            var live = new java.util.HashSet<String>();
            for (var mod : mods) {
                live.add(mod.name);
                // slide-in: modules ease from the screen edge over ~180ms
                float a = listAnim.computeIfAbsent(mod.name,
                    k -> nowMs / 1000f);
                float p = Math.min(1f, (nowMs / 1000f - a) / 0.18f);
                float ease = 1f - (1f - p) * (1f - p);            // ease-out quad
                int tw = Gui.width(mc.font, mod.name);
                int slide = (int) ((1f - ease) * 16);
                int px = lRight ? w - 6 - rightPad - tw - 8 + slide : 4 - slide;
                // glass pill: soft vertical gradient + hairline accent edge
                Gui.vgrad(g, px, ay, tw + 10, 12, 4, 0xC0131824, 0x800A0E16);
                Gui.pill(g, lRight ? px + tw + 8 : px, ay + 1, 2, 10, t.accent());
                Gui.textShadow(g, mc.font, mod.name, px + 6, ay + 3,
                    (int) (0xFF * ease) << 24 | (t.text() & 0xFFFFFF));
                ay += 13;
            }
            listAnim.keySet().retainAll(live); // evict disabled modules
            if (hidden > 0) {
                String more = "+" + hidden + " more";
                int tw = Gui.width(mc.font, more);
                int px = lRight ? w - 6 - rightPad - tw - 8 : 4;
                Gui.vgrad(g, px, ay, tw + 10, 12, 4, 0xC0131824, 0x800A0E16);
                Gui.textShadow(g, mc.font, more, px + 6, ay + 3, 0xFF8A8A9E);
            }
        }
        if (hw.compass.on()) drawCompass(g, mc, w, t);
        if (hw.armor.on() || hw.totems.on()) drawGear(g, mc, w, h, hw);
        drawSpotify(g, mc, t, w, h);
        var lm = NoxClient.INSTANCE.modules.get("LyricsSky");
        if (lm instanceof dev.noxclient.module.render.LyricsSky ls && ls.isEnabled() && ls.inGame.on())
            LyricsHud.render(g, w / 2, h - 46, (int) (double) ls.lines.get());
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

    /**
     * Top-center compass strip: cardinal/intercardinal letters + 15° ticks
     * sliding with player yaw, accent needle marks the facing direction.
     * MC yaw: 0=S(+Z), 90=W, 180=N, 270=E.
     */
    private void drawCompass(GuiGraphicsExtractor g, Minecraft mc, int w,
                             dev.noxclient.gui.theme.Theme t) {
        int cw = Math.min(220, w - 120);
        if (cw < 90) return;
        int cx = w / 2, cy = 6, ch = 16;
        Gui.vgrad(g, cx - cw / 2, cy, cw, ch, 8, 0xC0131824, 0x800A0E16);
        float yaw = mc.player.getYRot() % 360f;
        if (yaw < 0) yaw += 360f;
        float ppd = cw / 160f;                       // ~±80° visible range
        g.enableScissor(cx - cw / 2 + 2, cy, cx + cw / 2 - 2, cy + ch);
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
                g.fill(x, cy + ch - 6, x + 1, cy + ch - 2, a | 0x005A5A6E);
            }
        }
        g.disableScissor();
        g.fill(cx - 1, cy + 3, cx + 1, cy + ch - 3, t.accent());   // facing needle
    }

    /** Armor row + totem counter above the hotbar — DonutSMP PvP staples. */
    private void drawGear(GuiGraphicsExtractor g, Minecraft mc, int w, int h,
                          dev.noxclient.module.render.HudWidgets hw) {
        var inv = mc.player.getInventory();
        int y = h - 46;   // sits just above the vanilla hotbar
        int x = 6;
        if (hw.armor.on()) {
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
        }
        if (hw.totems.on()) {
            int n = 0;
            for (int i = 0; i < inv.getContainerSize(); i++)
                if (inv.getItem(i).is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING))
                    n += inv.getItem(i).getCount();
            if (n > 0) {
                var st = new net.minecraft.world.item.ItemStack(
                    net.minecraft.world.item.Items.TOTEM_OF_UNDYING);
                int tx = w - 24, ty = h - 46;
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
        int x = Math.clamp((int) (double) th.posX.get(), 2, Math.max(2, w - pw - 2));
        int y = Math.clamp((int) (double) th.posY.get(), 2, Math.max(2, h - ph - 2));
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
        int x = Math.clamp((int) (double) ss.posX.get(), 2, Math.max(2, w - pw - 2));
        int y = Math.clamp((int) (double) ss.posY.get(), 2, Math.max(2, h - ph - 2));

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
        int x = Math.clamp((int) (double) sp.posX.get(), 2, Math.max(2, sw - w - 16));
        int y = Math.clamp((int) (double) sp.posY.get(), 2, Math.max(2, sh - ph2 - 2));
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

    /** b = [minX, minY, maxX, maxY, dist] — tight screen-space entity bounds */
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
                g.fill(x0, y0, x1, y1, pc & 0x22FFFFFF);
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
                        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1,
                            (es.color.get() & 0x00FFFFFF) | 0x3A000000);
                        g.fill(x0, y0, x1, y1, (es.color.get() & 0x00FFFFFF) | 0x88000000);
                        g.outline(x0 - 1, y0 - 1, bw + 2, bh + 2, col);
                    }
                    case "Box" -> {
                        g.fill(x0, y0, x1, y1, es.color.get());
                        g.outline(x0, y0, bw, bh, col);
                    }
                    default -> g.outline(x0, y0, bw, bh, col);
                }
            }
        }
        if (isPlayer && tr instanceof Tracers t && t.isEnabled() && t.players.on()
            && mc.player.distanceToSqr(e) < t.maxDist.get() * t.maxDist.get()) {
            line(g, w / 2, h - 20, cx, y1, t.color.get() | 0xFF000000);
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
            g.fill((int) p[0] - s, (int) p[1] - s, (int) p[0] + s, (int) p[1] + s, en.getValue() & 0x55FFFFFF);
            g.outline((int) p[0] - s, (int) p[1] - s, s * 2, s * 2, en.getValue());
        }
    }

    /**
     * BaseIntel marks: category-colored diamonds + a label for the nearest
     * few so chest-open sounds / BE leaks read as "chest @ -4y", not just dots.
     */
    private void drawIntelMarks(GuiGraphicsExtractor g, Minecraft mc, Vec3 cp,
                                Matrix4f mat, int w, int h) {
        if (dev.noxclient.util.BaseIntel.size() == 0) return;
        int[] labels = {0};
        dev.noxclient.util.BaseIntel.forEach((pos, hit) -> {
            double[] p = project(Vec3.atCenterOf(pos), cp, mat, w, h);
            if (p == null || p[2] > 260) return;
            int s = (int) Math.max(3, Math.min(14, 160 / p[2]));
            g.fill((int) p[0] - s, (int) p[1] - s, (int) p[0] + s, (int) p[1] + s,
                hit.color & 0x55FFFFFF);
            g.outline((int) p[0] - s, (int) p[1] - s, s * 2, s * 2, hit.color);
            if (labels[0] < 12 && p[2] < 160 && hit.label != null && !hit.label.isEmpty()) {
                labels[0]++;
                String txt = hit.label + (hit.count > 1 ? " ×" + hit.count : "");
                Gui.textCenter(g, mc.font, txt, (int) p[0], (int) p[1] - s - 9, hit.color);
            }
        });
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
        if (steps == 0) { g.fill(x1, y1, x1 + 1, y1 + 1, color); return; }
        for (int i = 0; i <= steps; i += 2)
            g.fill(x1 + (x2 - x1) * i / steps, y1 + (y2 - y1) * i / steps,
                x1 + (x2 - x1) * i / steps + 1, y1 + (y2 - y1) * i / steps + 1, color);
    }
}
