package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.gui.Toasts;

/**
 * VoidRescue — stolen from Zyron RelogBypass + WaterClient AutoChunkLoader.
 * Falling below Y=-2 on DSMP means a bedrock/void trap (or a chunk-loading
 * gap). This runs a configurable command ladder to get you out: the classic
 * move is a throwaway home slot — delhome/sethome/home — so you teleport
 * back to your own safe home instead of dying in the void.
 *   triggerY   — fall below this Y to fire (void sits at -2 under bedrock)
 *   commands   — comma-separated commands, run top-to-bottom, 800ms apart.
 *                Default is the AutoChunkLoader cycle on home slot 9.
 *   once       — disable after firing so a bad sequence can't loop
 */
public class VoidRescue extends Module {
    private final Setting.Mode method = add(new Setting.Mode("Method", "Commands",
        "Commands", "Relog"));
    private final Setting.Num triggerY = add(new Setting.Num("Trigger Y", -2, -64, 20,
        "Fire when the player falls below this Y"));
    private final Setting.Str commands = add(new Setting.Str("Escape commands",
        "delhome 9,sethome 9,home 9",
        "Comma-separated, sent with ~800ms gaps"));
    private final Setting.Num delay = add(new Setting.Num("Cmd delay (ms)", 800, 200, 5000));
    private final Setting.Bool once = add(new Setting.Bool("Fire once", false,
        "Disable after the ladder ran (loops are server-visible)"));
    private final Setting.Num cooldown = add(new Setting.Num("Cooldown (s)", 30, 5, 300,
        "Min seconds between ladders when 'Fire once' is off"));

    private String[] ladder = new String[0];
    private int step = -1;
    private long nextStep, lastFire;
    private net.minecraft.client.multiplayer.ServerData relogServer;
    private long relogAt = -1;

    public VoidRescue() { super("VoidRescue",
        "Void/bedrock-trap escape — fires a home/rtp command ladder below Y",
        Category.DONUTSMP); }

    @Override protected void onEnable() { step = -1; relogAt = -1; }
    @Override protected void onDisable() { relogServer = null; relogAt = -1; }

    @Override public void onTick() {
        long now = System.currentTimeMillis();

        // relog path: world is gone after our disconnect — rejoin once the
        // title/disconnect screen is up and the delay has passed
        if (relogServer != null && mc.level == null) {
            if (relogAt < 0) relogAt = now + 1500;
            if (now < relogAt) return;
            var server = relogServer; relogServer = null; relogAt = -1;
            try {
                var addr = net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(server.ip);
                net.minecraft.client.gui.screens.ConnectScreen.startConnecting(
                    new net.minecraft.client.gui.screens.TitleScreen(), mc, addr, server, false, null);
            } catch (Exception ignored) {}
            return;
        }

        if (mc.player == null || mc.getConnection() == null) return;

        if (step >= 0) {   // ladder running
            if (now >= nextStep && step < ladder.length) {
                String c = ladder[step++].trim();
                if (!c.isEmpty())
                    mc.getConnection().sendCommand(c.startsWith("/") ? c.substring(1) : c);
                nextStep = now + (long) (double) delay.get();
                mark("escape step " + step + "/" + ladder.length);
            }
            if (step >= ladder.length) {
                step = -1;
                Toasts.push("VoidRescue: escape ladder done", 0xFF66D9A0);
                if (once.on()) setEnabled(false);
            }
            return;
        }

        if (mc.player.getY() >= triggerY.get()) return;
        if (now - lastFire < cooldown.get() * 1000) return;

        // Relog (Anubis AutoRelog): hard-disconnect and rejoin — drops you out
        // of the void dimension entirely instead of trusting home commands
        if ("Relog".equals(method.get()) || "relog".equalsIgnoreCase(method.get())) {
            var server = mc.getCurrentServer();
            if (server == null) return;
            relogServer = server;
            lastFire = now;
            Toasts.push("VoidRescue: relog at Y=" + (int) mc.player.getY(), 0xFFFF6666);
            try { mc.getConnection().getConnection().disconnect(
                net.minecraft.network.chat.Component.literal("VoidRescue relog")); }
            catch (Exception e) { relogServer = null; }
            if (once.on()) setEnabled(false);
            return;
        }

        String cmds = commands.get();
        if (cmds == null || cmds.isBlank()) return;
        ladder = cmds.split(",");
        step = 0; nextStep = now; lastFire = now;
        Toasts.push("VoidRescue: Y=" + (int) mc.player.getY() + " — escaping", 0xFFFF6666);
    }

    @Override public String status() {
        if (!isEnabled()) return "off";
        return step >= 0 ? "escaping " + step + "/" + ladder.length : "armed y<" + triggerY.get().intValue();
    }
}
