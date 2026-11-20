package dev.noxclient.util;

import dev.noxclient.NoxClient;
import dev.noxclient.gui.NoxWindowScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * File-driven debug console. Watches <gameDir>/nox_cmd each tick so an
 * external script can drive the client without OS-level input injection:
 *   shot            screenshot -> run/screenshots/*.png
 *   gui / close     open / close the NOX window
 *   click X Y [B]   mouse click at gui-scaled coords (button 0 default)
 *   dclick X Y      double click
 *   drag X1 Y1 X2 Y2
 *   scroll X Y DY
 *   key CODE        key press (GLFW key code, e.g. 256 = ESC, 344 = RSHIFT)
 *   char C          type one character
 *   state           write screen/size/guiScale to nox_ack
 * writes "ok <cmd>" or "err <msg>" to nox_ack.
 */
public final class DebugConsole {
    /** Authoritative screen — lives in Gui in 26.3, not Minecraft. */
    private static Screen screenOf(Minecraft mc) { return mc.gui.screen(); }
    /** Injected cursor position; -1 = use the real mouse. Screens read this so
     *  `move`/`click` debug commands drive hover state too. */
    public static volatile double hoverX = -1, hoverY = -1;
    private static Path cmd, ack;
    private static long lastPoll;

    public static void init() {
        Path dir = FabricLoader.getInstance().getGameDir();
        cmd = dir.resolve("nox_cmd");
        ack = dir.resolve("nox_ack");
    }

    public static void tick(Minecraft mc) {
        if (cmd == null) init();
        long now = System.currentTimeMillis();
        if (now - lastPoll < 150) return;
        lastPoll = now;
        try {
            if (!Files.exists(cmd)) return;
            String line = Files.readString(cmd).trim();
            Files.delete(cmd);
            if (line.isEmpty()) return;
            String[] p = line.split("\\s+");
            String customAck = run(mc, p, line);
            Files.writeString(ack, customAck != null ? customAck : "ok " + line);
        } catch (Throwable t) {
            t.printStackTrace();
            try { Files.writeString(ack, "err " + t); } catch (Exception ignored) {}
        }
    }

    private static String run(Minecraft mc, String[] p, String raw) throws Exception {
        Screen s = screenOf(mc);
        switch (p[0]) {
            case "shot" -> {   // backbuffer incl. NVG overlay → /tmp/nox_shot.png (real pixels)
                dev.noxclient.gui.nvg.NvgOverlay.grabFrame(
                        java.nio.file.Path.of("/tmp/nox_shot.png"));
                return "shot queued -> /tmp/nox_shot.png";
            }
            case "shotmc" -> Screenshot.grab(mc, false);   // offscreen RT only (no NVG layer)
            case "framegrab" -> {   // backbuffer incl. NVG overlay → run/framegrab.png
                dev.noxclient.gui.nvg.NvgOverlay.grabFrame(
                        FabricLoader.getInstance().getGameDir().resolve("framegrab.png"));
                return "framegrab queued";
            }
            case "gui" -> mc.setScreenAndShow(new dev.noxclient.gui.NoxClickGui());
            case "guilegacy" -> mc.setScreenAndShow(new NoxWindowScreen());
            case "screen" -> {   // screen -> dump current; screen <cls> -> open NOX screen ("inv" = vanilla inventory)
                if (p.length < 2)
                    return "screen=" + (s == null ? "null" : s.getClass().getSimpleName());
                if (p[1].equalsIgnoreCase("mp")) {
                    mc.setScreenAndShow(new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(
                        new net.minecraft.client.gui.screens.TitleScreen()));
                    return "screen mp -> JoinMultiplayerScreen";
                }
                if (p[1].equalsIgnoreCase("inv") && mc.player != null) {
                    mc.setScreenAndShow(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
                    return "screen inv -> InventoryScreen";
                }
                Class<?> cls = Class.forName("dev.noxclient.gui." + p[1]);
                try {
                    mc.setScreenAndShow((Screen) cls.getDeclaredConstructor().newInstance());
                } catch (NoSuchMethodException e) {
                    var c = cls.getDeclaredConstructor(Screen.class);
                    mc.setScreenAndShow((Screen) c.newInstance(s));
                }
                return "screen " + p[1] + " -> "
                    + (screenOf(mc) == null ? "null" : screenOf(mc).getClass().getSimpleName());
            }
            case "close" -> mc.setScreenAndShow(null);
            case "cam" -> {   // cam [0|1|2] -> first/third-back/third-front person
                var opts = net.minecraft.client.CameraType.values();
                int i = p.length > 1 ? Integer.parseInt(p[1])
                    : (mc.options.getCameraType().ordinal() + 1) % opts.length;
                mc.options.setCameraType(opts[i]);
                return "cam -> " + mc.options.getCameraType();
            }
            case "click" -> {
                double x = d(p[1]), y = d(p[2]);
                int b = p.length > 3 ? Integer.parseInt(p[3]) : 1;
                hoverX = x; hoverY = y;
                setCursor(mc, x, y);            // vanilla widgets hover off the real cursor
                if (s != null) {
                    hoverAt(s, x, y);           // release fires only while the widget is hovered
                    var ev = new MouseButtonEvent(x, y, new MouseButtonInfo(b, 0));
                    boolean hit = s.mouseClicked(ev, false);
                    boolean rel = s.mouseReleased(ev);          // vanilla buttons fire on release
                    return "click " + x + "," + y + " b" + b + " -> press=" + hit + " release=" + rel
                        + " screen=" + s.getClass().getSimpleName();
                }
            }
            case "dclick" -> {
                int b = p.length > 3 ? Integer.parseInt(p[3]) : 1;
                if (s != null) s.mouseClicked(new MouseButtonEvent(d(p[1]), d(p[2]),
                    new MouseButtonInfo(b, 0)), true);
            }
            case "drag" -> {
                hoverX = d(p[3]); hoverY = d(p[4]);
                if (s != null) {
                    s.mouseClicked(new MouseButtonEvent(d(p[1]), d(p[2]), new MouseButtonInfo(1, 0)), false);
                    s.mouseDragged(new MouseButtonEvent(d(p[3]), d(p[4]), new MouseButtonInfo(1, 0)),
                        d(p[3]) - d(p[1]), d(p[4]) - d(p[2]));
                    s.mouseReleased(new MouseButtonEvent(d(p[3]), d(p[4]), new MouseButtonInfo(1, 0)));
                }
            }
            case "move" -> {
                hoverX = d(p[1]); hoverY = d(p[2]);
                setCursor(mc, hoverX, hoverY);
                if (s != null) { hoverAt(s, hoverX, hoverY); s.mouseMoved(hoverX, hoverY); }
            }
            case "scroll" -> {
                if (s != null) s.mouseScrolled(d(p[1]), d(p[2]), 0, d(p[3]));
            }
            case "key" -> {
                int code = Integer.parseInt(p[1]);
                int mod = p.length > 2 ? Integer.parseInt(p[2]) : 0;   // SDL KMOD (ctrl=0xC0)
                if (s != null) {
                    int sdl = toSdl(code);
                    boolean r = s.keyPressed(new KeyEvent(sdl, 0, mod));
                    s.keyReleased(new KeyEvent(sdl, 0, mod));   // tap = press+release
                    return "key " + code + " (sdl " + sdl + ") -> " + r
                        + " screen=" + s.getClass().getSimpleName();
                }
            }
            case "char" -> {
                if (s != null) s.charTyped(new CharacterEvent(p[1].codePointAt(0)));
            }
            case "replay" -> {   // replay [file] → open playback screen
                dev.noxclient.module.misc.ReplayMod.openPlayback(
                    p.length > 1 ? java.nio.file.Path.of(p[1]) : null);
            }
            case "audit" -> {   // run the M29 self-check now, return findings
                dev.noxclient.module.misc.SecurityAudit.run();
                return "audit: " + (dev.noxclient.module.misc.SecurityAudit.lastFindings.isEmpty()
                    ? "all checks pass"
                    : String.join(" | ", dev.noxclient.module.misc.SecurityAudit.lastFindings));
            }
            case "tab" -> {
                int i = Integer.parseInt(p[1]);
                NoxWindowScreen.activeTab = i;
                if (dev.noxclient.gui.NoxClickGui.current != null)
                    dev.noxclient.gui.NoxClickGui.current.setTabIndex(i);
                if (NoxWindowScreen.current != null)
                    NoxWindowScreen.current.tab =
                        dev.noxclient.module.Category.values()[Math.min(i,
                            dev.noxclient.module.Category.values().length - 1)];
            }
            case "join" -> {
                var addr = net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(p[1]);
                var data = new net.minecraft.client.multiplayer.ServerData("debug", p[1],
                    net.minecraft.client.multiplayer.ServerData.Type.OTHER);
                net.minecraft.client.gui.screens.ConnectScreen.startConnecting(
                    mc.gui.screen(), mc, addr, data, false, null);
            }
            case "slot" -> {   // real container click packet — hover-independent
                if (mc.player != null && mc.gameMode != null) {
                    var menu = mc.player.containerMenu;
                    int slot = Integer.parseInt(p[1]);
                    int btn = p.length > 2 ? Integer.parseInt(p[2]) : 0;
                    var type = p.length > 3 ? switch (p[3].toLowerCase()) {
                        case "quick", "shift" -> net.minecraft.world.inventory.ContainerInput.QUICK_MOVE;
                        case "throw" -> net.minecraft.world.inventory.ContainerInput.THROW;
                        case "swap" -> net.minecraft.world.inventory.ContainerInput.SWAP;
                        case "clone" -> net.minecraft.world.inventory.ContainerInput.CLONE;
                        case "pickupall" -> net.minecraft.world.inventory.ContainerInput.PICKUP_ALL;
                        default -> net.minecraft.world.inventory.ContainerInput.PICKUP;
                    } : net.minecraft.world.inventory.ContainerInput.PICKUP;
                    mc.gameMode.handleContainerInput(menu.containerId, slot, btn,
                        type, mc.player);
                    return "slot " + slot + " b" + btn + " " + type;
                }
            }
            case "widgets" -> {   // dump child widgets + bounds of the active screen
                if (s == null) return "widgets: no screen";
                StringBuilder sb = new StringBuilder("widgets[" + s.getClass().getSimpleName() + "]:");
                for (var c : s.children())
                    if (c instanceof net.minecraft.client.gui.components.AbstractWidget w)
                        sb.append(" ").append(w.getClass().getSimpleName())
                          .append("@").append(w.getX()).append(",").append(w.getY())
                          .append(" ").append(w.getWidth()).append("x").append(w.getHeight())
                          .append(w.active ? "" : "(inactive)");
                return sb.toString();
            }
            case "scoreboard" -> {   // dump sidebar scoreboard entries
                if (mc.level == null) return "scoreboard: no level";
                var obj = mc.level.getScoreboard().getDisplayObjective(
                    net.minecraft.world.scores.DisplaySlot.SIDEBAR);
                if (obj == null) return "scoreboard: no sidebar objective";
                var sb = new StringBuilder("scoreboard[" + obj.getDisplayName().getString() + "]:");
                for (var e : mc.level.getScoreboard().listPlayerScores(obj)) {
                    var team = mc.level.getScoreboard().getPlayersTeam(e.owner());
                    String pre = team == null ? "" : team.getPlayerPrefix().getString();
                    String suf = team == null ? "" : team.getPlayerSuffix().getString();
                    sb.append(" [").append(e.display() == null ? "null" : e.display().getString())
                      .append("|pre:").append(pre).append("|suf:").append(suf)
                      .append("=").append(e.value()).append("]");
                }
                sb.append(" | teams:");
                for (var t : mc.level.getScoreboard().getPlayerTeams())
                    sb.append(" {").append(t.getName()).append("=")
                      .append(t.getPlayerPrefix().getString()).append("||")
                      .append(t.getPlayerSuffix().getString()).append("}");
                return sb.toString();
            }
            case "slots" -> {  // dump open container contents
                if (mc.player != null) {
                    var sb = new StringBuilder();
                    var menu = mc.player.containerMenu;
                    for (int i = 0; i < menu.slots.size(); i++) {
                        var it = menu.slots.get(i).getItem();
                        if (!it.isEmpty()) sb.append(i).append('=')
                            .append(it.getItem().toString().replace("minecraft:", ""))
                            .append(' ');
                    }
                    return "slots: " + sb;
                }
            }
            case "cards" -> {   // dump visible card rects (window GUI must be open)
                var sb2 = new StringBuilder("cards: ");
                if (s instanceof dev.noxclient.gui.NoxWindowScreen nw) {
                    for (var c : nw.debugCards())
                        sb2.append(c.module.name).append('@').append(c.x).append(',')
                            .append(c.y).append(',').append(c.width).append('x').append(c.height)
                            .append(" g").append(c.x + c.width - 18).append(',').append(c.y + 4).append("; ");
                }
                return sb2.toString();
            }
            case "all" -> {   // dump every registered module
                var sb3 = new StringBuilder("all[" + NoxClient.INSTANCE.modules.all().size() + "]: ");
                for (var m : NoxClient.INSTANCE.modules.all()) sb3.append(m.name).append(' ');
                return sb3.toString();
            }
            case "save" -> { NoxClient.INSTANCE.config.save(); return "ok save"; }
            case "guiscale" -> {   // guiscale <n> — force vanilla GUI scale live
                int gs = Integer.parseInt(p[1]);
                mc.options.guiScale().set(gs);
                mc.getWindow().setGuiScale(mc.getWindow().calculateScale(gs,
                    mc.options.forceUnicodeFont().get()));
                return "guiscale=" + mc.getWindow().getGuiScale()
                    + " gui=" + mc.getWindow().getGuiScaledWidth()
                    + "x" + mc.getWindow().getGuiScaledHeight();
            }
            case "cape" -> {   // cape | cape reload | cape list
                if (p.length > 1 && "list".equals(p[1])) {
                    var files = dev.noxclient.gui.CustomCape.available();
                    return "capes: " + files.stream().map(f -> f.getFileName().toString())
                        .reduce("", (a, b) -> a.isEmpty() ? b : a + ", " + b)
                        + " (dir=" + dev.noxclient.gui.CustomCape.dir() + ")";
                }
                if (p.length > 1 && "reload".equals(p[1])) {
                    dev.noxclient.gui.CustomCape.reload();
                    return "cape reload queued";
                }
                // bare `cape` = status only — do NOT trigger a reload
                return "cape loaded=" + dev.noxclient.gui.CustomCape.loaded()
                    + " from=" + dev.noxclient.gui.CustomCape.loadedFrom()
                    + " err=" + dev.noxclient.gui.CustomCape.lastError()
                    + " id=" + dev.noxclient.gui.CustomCape.id();
            }
            case "why" -> {   // why <module> — what is it doing right now / why idle
                var m = NoxClient.INSTANCE.modules.get(p[1]);
                if (m == null) return "no module " + p[1];
                try {
                    var sb = new StringBuilder(m.name + (m.isEnabled() ? " [on] " : " [off] ") + m.status());
                    if (m.lastAction != null)
                        sb.append(" | act: ").append(m.lastAction)
                          .append(" (").append((System.currentTimeMillis() - m.lastActionAt) / 1000).append("s ago)");
                    if (m.lastError != null)
                        sb.append(" | ERR: ").append(m.lastError)
                          .append(" (").append((System.currentTimeMillis() - m.lastErrorAt) / 1000).append("s ago)");
                    return sb.toString();
                } catch (Throwable t) { return m.name + " status error: " + t; }
            }
            case "selftest" -> {   // dump status of every enabled module
                var sb2 = new StringBuilder("selftest:");
                for (var m : NoxClient.INSTANCE.modules.all())
                    if (m.isEnabled()) {
                        try { sb2.append(' ').append(m.name).append('=').append(m.status()); }
                        catch (Throwable t) { sb2.append(' ').append(m.name).append("=ERR"); }
                    }
                return sb2.toString();
            }
            case "mods" -> {   // dump enabled modules
                var sb = new StringBuilder("mods: ");
                for (var m : NoxClient.INSTANCE.modules.all())
                    if (m.isEnabled()) sb.append(m.name).append(' ');
                return sb.toString();
            }
            case "share" -> {   // share -> full config to clipboard; share <mod> -> NOXMOD
                if (p.length > 1) NoxClient.INSTANCE.config.shareModule(
                    NoxClient.INSTANCE.modules.get(p[1]));
                else NoxClient.INSTANCE.config.share();
                return "share done";
            }
            case "clip" -> {   // clip [set <text>] -> get/set clipboard
                var kh = mc.keyboardHandler;
                if (p.length > 2 && p[1].equals("set")) {
                    kh.setClipboard(String.join(" ", java.util.Arrays.copyOfRange(p, 2, p.length)));
                    return "clip set";
                }
                String c = kh.getClipboard();
                return "clip[" + (c.length() > 120 ? c.substring(0, 120) + "…" : c) + "]";
            }
            case "import" -> {   // import config from clipboard
                NoxClient.INSTANCE.config.importConfig(mc.keyboardHandler.getClipboard());
                return "import done";
            }
            case "holdkey" -> {   // holdkey <keycode> <press1|release0|repeat2>
                int kc = Integer.parseInt(p[1]);
                if (Integer.parseInt(p[2]) == 1)
                    dev.noxclient.module.ModuleManager.FAKE_KEYS.add(kc);
                else if (Integer.parseInt(p[2]) == 0)
                    dev.noxclient.module.ModuleManager.FAKE_KEYS.remove(kc);
                long win = mc.getWindow().handle();
                int act = Integer.parseInt(p[2]);
                mc.keyboardHandler.keyPress(win, act,
                    new net.minecraft.client.input.KeyEvent(
                        Integer.parseInt(p[1]), 0, 0));
                return "holdkey " + p[1] + " act=" + act;
            }
            case "toggle" -> {   // toggle <module>
                if (p.length < 2) return "usage: toggle <module>";
                var m2 = NoxClient.INSTANCE.modules.get(p[1]);
                if (m2 == null) return "no module " + p[1];
                m2.toggle();
                return p[1] + " -> " + m2.isEnabled();
            }
            case "set" -> {   // set <module> <setting> <value...>
                var m = NoxClient.INSTANCE.modules.get(p[1]);
                if (m == null) return "no module " + p[1];
                // strip "set <module>" prefix — setting names may contain spaces
                String rest = raw.substring(4 + p[1].length()).trim();
                dev.noxclient.setting.Setting<?> hit = null;
                String val = null;
                for (var st : m.settings) {
                    for (String cand : new String[]{st.name, st.name.replace(' ', '_')}) {
                        if (rest.regionMatches(true, 0, cand, 0, cand.length())
                            && (rest.length() == cand.length() || rest.charAt(cand.length()) == ' ')) {
                            hit = st; val = rest.substring(cand.length()).trim(); break;
                        }
                    }
                    if (hit != null) break;
                }
                if (hit == null) return "no setting on " + p[1] + " matching '" + rest + "'";
                try {
                    if (hit instanceof dev.noxclient.setting.Setting.Bool b)
                        b.set(val.equalsIgnoreCase("true") || val.equals("1") || val.equalsIgnoreCase("on"));
                    else if (hit instanceof dev.noxclient.setting.Setting.Num n)
                        n.set(Math.min(n.max, Math.max(n.min, Double.parseDouble(val))));
                    else if (hit instanceof dev.noxclient.setting.Setting.Mode mo)
                        mo.set(val);
                    else if (hit instanceof dev.noxclient.setting.Setting.Col c)
                        c.set((int) Long.parseLong(val.replace("#", ""), 16));
                    else if (hit instanceof dev.noxclient.setting.Setting.Bind b)
                        b.set(Integer.parseInt(val));
                    else
                        ((dev.noxclient.setting.Setting<String>) hit).set(val);
                    return "set " + p[1] + "." + hit.name + " = " + hit.get();
                } catch (Exception ex) { return "err value '" + val + "': " + ex; }
            }
            case "get" -> {   // get <module> -> dump module settings
                if (p.length < 2) return "usage: get <module>";
                var m = NoxClient.INSTANCE.modules.get(p[1]);
                if (m == null) return "no module " + p[1];
                var sb = new StringBuilder(p[1] + (m.isEnabled() ? " [on]: " : " [off]: "));
                for (var st : m.settings) sb.append(st.name).append('=').append(st.get()).append(" | ");
                return sb.toString();
            }
            case "chat" -> {
                if (mc.player != null) {
                    String t = raw.substring(5).trim();
                    if (t.startsWith("/")) mc.player.connection.sendCommand(t.substring(1));
                    else mc.player.connection.sendChat(t);
                }
            }
            case "state" -> {
                var w = mc.getWindow();
                String info = "noxwin=" + (NoxWindowScreen.open ? "open" : "closed")
                    + " screen=" + (s == null ? "null" : s.getClass().getSimpleName())
                    + " gui=" + w.getGuiScaledWidth() + "x" + w.getGuiScaledHeight()
                    + " scale=" + w.getGuiScale()
                    + " win=" + NoxWindowScreen.winX + "," + NoxWindowScreen.winY
                    + "," + NoxWindowScreen.winW + "," + NoxWindowScreen.winH
                    + " rpc=" + (NoxClient.INSTANCE.rpc != null);
                return info;
            }
            case "moveguard" -> { return MoveGuard.dump(); }
            case "look" -> {   // look <yaw> <pitch> — aim camera for shot tests
                if (mc.player != null && p.length > 2) {
                    mc.player.setYRot(Float.parseFloat(p[1]));
                    mc.player.setXRot(Float.parseFloat(p[2]));
                    return "look " + p[1] + " " + p[2];
                }
                return "usage: look <yaw> <pitch>";
            }
            case "gizmo" -> {   // gizmo [r] — test cuboid at player feet (render pipe check)
                if (mc.player != null) {
                    double r = p.length > 1 ? Double.parseDouble(p[1]) : 4;
                    var c = mc.player.blockPosition();
                    dev.noxclient.render.NoxRender.once(() ->
                        net.minecraft.gizmos.Gizmos.cuboid(
                            new net.minecraft.world.phys.AABB(c).inflate(r),
                            net.minecraft.gizmos.GizmoStyle.strokeAndFill(0xFFFF2020, 2f, 0x44FF2020))
                            .setAlwaysOnTop().persistForMillis(5000));
                    return "gizmo queued";
                }
                return "no player";
            }
            case "orepos" -> {   // orepos — dump nearest simulated ore positions
                var om = NoxClient.INSTANCE.modules.get("OreSim");
                if (om instanceof dev.noxclient.module.donutsmp.OreSim o && mc.player != null)
                    return o.nearest(mc.player.position(), 8);
                return "oresim off";
            }
            case "world" -> {   // world <folder> — load a singleplayer save
                if (p.length > 1)
                    mc.createWorldOpenFlows().openWorld(raw.substring(6).trim(), () -> {});
                return "loading";
            }
            case "worldnew" -> {   // worldnew [seed] — fresh 26.3 world for OreSim verification
                var srv = mc.getSingleplayerServer();
                var cfg = srv != null ? srv.getWorldData().getDataConfiguration()
                    : net.minecraft.world.level.WorldDataConfiguration.DEFAULT;
                long wseed = p.length > 1 ? (long) d(p[1]) : 42L;
                var settings = new net.minecraft.world.level.LevelSettings("NOX-Verify",
                    net.minecraft.world.level.GameType.SPECTATOR,
                    net.minecraft.world.level.LevelSettings.DifficultySettings.DEFAULT, true, cfg);
                mc.createWorldOpenFlows().createFreshLevel("NOX-Verify", settings,
                    new net.minecraft.world.level.levelgen.WorldOptions(wseed, true, false),
                    prov -> prov.lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                        .getOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.NORMAL)
                        .value().createWorldDimensions(), null);
                return "creating seed=" + wseed;
            }
            case "smarks" -> {   // dump SoundFind + AntiESPBypass markers (detector verification)
                var sb = new StringBuilder();
                var sm = dev.noxclient.module.donutsmp.SoundFind.MARKS;
                sb.append("soundfind=").append(sm.size());
                for (var e : sm.entrySet()) {
                    var m = e.getValue();
                    sb.append(" | ").append(e.getKey()).append('@')
                        .append((int) m.x).append(',').append((int) m.y).append(',').append((int) m.z)
                        .append(" x").append(m.count).append(m.deep ? " DEEP" : "");
                }
                var am = dev.noxclient.module.donutsmp.AntiESPBypass.MARKS;
                sb.append(" || esp=").append(am.size());
                for (var e : am.entrySet())
                    sb.append(" | ").append(e.getKey());
                return sb.toString();
            }
            case "findore" -> {   // findore <substr> — nearest REAL ore blocks in loaded chunks
                if (mc.level == null || mc.player == null || p.length < 2) return "usage: findore gold_ore";
                String want = p[1];
                var hits = new java.util.ArrayList<net.minecraft.core.BlockPos>();
                var pc = mc.player.chunkPosition();
                for (int dx = -10; dx <= 10; dx++) for (int dz = -10; dz <= 10; dz++) {
                    if (!mc.level.hasChunk(pc.x() + dx, pc.z() + dz)) continue;
                    var ch = mc.level.getChunk(pc.x() + dx, pc.z() + dz);
                    var secs = ch.getSections();
                    int minY = ch.getMinY();
                    for (int si = 0; si < secs.length; si++) {
                        var sec = secs[si];
                        if (sec == null) continue;
                        var states = sec.getStates();
                        if (states == null) continue;
                        for (int sy = 0; sy < 16; sy++) for (int sx = 0; sx < 16; sx++) for (int sz = 0; sz < 16; sz++) {
                            var st = states.get(sx, sy, sz);
                            if (st.isAir()) continue;
                            var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
                            if (id.contains(want))
                                hits.add(new net.minecraft.core.BlockPos(
                                    ch.getPos().getMinBlockX() + sx, minY + si * 16 + sy, ch.getPos().getMinBlockZ() + sz));
                        }
                    }
                }
                if (hits.isEmpty()) return "none loaded";
                hits.sort(java.util.Comparator.comparingDouble(b -> b.distSqr(mc.player.blockPosition())));
                var sb2 = new StringBuilder();
                for (int i = 0; i < Math.min(8, hits.size()); i++)
                    sb2.append(hits.get(i).toShortString()).append("  ");
                return hits.size() + " real: " + sb2;
            }
            case "blockat" -> {   // blockat <x> <y> <z> — real block id at pos
                if (mc.level != null && p.length > 3)
                    return net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .getKey(mc.level.getBlockState(
                            new net.minecraft.core.BlockPos((int) d(p[1]), (int) d(p[2]), (int) d(p[3]))).getBlock())
                        .toString();
                return "no level";
            }
            case "oresim" -> {   // oresim <cx> <cz> — dump module predictions inside chunk
                if (mc.level == null || p.length < 3) return "usage: oresim -9 0";
                var m = dev.noxclient.NoxClient.INSTANCE.modules.get("OreSim");
                if (!(m instanceof dev.noxclient.module.donutsmp.OreSim os)) return "no OreSim";
                return os.dumpChunk((int) d(p[1]), (int) d(p[2]));
            }
        }
        return null;
    }

    private static double d(String s) { return Double.parseDouble(s); }

    /** Set the `hovered` flag on any widget under the point — release-handlers gate on it. */
    private static void hoverAt(Screen s, double x, double y) {
        try {
            var f = net.minecraft.client.gui.components.AbstractWidget.class.getDeclaredField("isHovered");
            f.setAccessible(true);
            for (var c : s.children())
                if (c instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                    f.setBoolean(w, false);   // clear stale hover — release handlers gate on this
                    if (x >= w.getX() && x < w.getX() + w.getWidth()
                        && y >= w.getY() && y < w.getY() + w.getHeight())
                        f.setBoolean(w, true);
                }
        } catch (Throwable ignored) {}
    }

    /** Move the real OS-level cursor fields so vanilla `isHovered` works in tests. */
    private static void setCursor(Minecraft mc, double guiX, double guiY) {
        try {
            var w = mc.getWindow();
            double sx = guiX * w.getScreenWidth() / w.getGuiScaledWidth();
            double sy = guiY * w.getScreenHeight() / w.getGuiScaledHeight();
            for (var f : net.minecraft.client.MouseHandler.class.getDeclaredFields()) {
                if (f.getType() != double.class) continue;
                if (f.getName().equals("xpos")) { f.setAccessible(true); f.setDouble(mc.mouseHandler, sx); }
                if (f.getName().equals("ypos")) { f.setAccessible(true); f.setDouble(mc.mouseHandler, sy); }
            }
        } catch (Throwable ignored) {}
    }

    /** GLFW key codes → SDL scancodes (26.3 InputConstants switched to SDL). */
    private static int toSdl(int glfw) {
        if (glfw >= 65 && glfw <= 90) return glfw - 61;      // A-Z → 4-29
        if (glfw >= 49 && glfw <= 57) return glfw - 19;      // 1-9 → 30-38
        if (glfw == 48) return 39;                            // 0
        if (glfw >= 290 && glfw <= 301) return glfw - 232;   // F1-F12 → 58-69
        return switch (glfw) {
            case 256 -> 41;   case 257 -> 40;  case 258 -> 43;  case 259 -> 42;
            case 32 -> 44;    case 340 -> 225; case 342 -> 227; case 341 -> 224;
            case 343 -> 226;  case 344 -> 229; case 345 -> 228; case 346 -> 230;
            case 260 -> 80;   case 261 -> 79;  case 262 -> 81;  case 263 -> 82;
            case 264 -> 73;   case 265 -> 76;  case 266 -> 74;  case 267 -> 77;
            case 268 -> 75;   case 269 -> 78;  default -> glfw;
        };
    }
}
