package dev.noxclient;

import com.mojang.blaze3d.platform.InputConstants;
import dev.noxclient.config.ConfigManager;
import dev.noxclient.discord.NoxRPC;
import dev.noxclient.gui.NoxWindowScreen;
import dev.noxclient.gui.theme.Theme;
import dev.noxclient.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import dev.noxclient.gui.NoxOverlay;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;

public class NoxClient implements ClientModInitializer {
    public static final String NAME = "NOX";
    public static final String VERSION = "0.1.0-beta";
    public static final String DISCORD_INVITE = "https://discord.gg/tewtZTsZf5";

    public static NoxClient INSTANCE;
    public ModuleManager modules;
    public ConfigManager config;
    public NoxRPC rpc;
    private boolean guiKeyWasDown;
    /** Set by NOX screens on close — the RSHIFT edge in the same tick must not
     *  instantly reopen the GUI it just closed. */
    public static volatile long guiClosedAt;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        dev.noxclient.util.TamperGuard.verifySync();   // FIRST: compromised jar
        // gets flagged before any license key / alt token is ever touched
        modules = new ModuleManager();
        modules.registerAll();
        config = new ConfigManager();
        config.load();
        rpc = new NoxRPC();
        rpc.start();
        if (!dev.noxclient.util.TamperGuard.compromised())
            dev.noxclient.premium.LicenseManager.init();
        else
            System.err.println("[NOX] license init skipped — jar failed integrity");
        dev.noxclient.alts.AltManager.load();
        dev.noxclient.util.TamperGuard.verifyAsync();  // keeps re-checking in bg
        dev.noxclient.security.CrashLoopGuard.init(
            net.minecraft.client.Minecraft.getInstance());
        dev.noxclient.update.UpdateManager.init();
        dev.noxclient.telemetry.Telemetry.init();
        dev.noxclient.stats.Stats.init();
        dev.noxclient.util.CrashReporter.init(
            net.minecraft.client.Minecraft.getInstance());  // /nox crashreport + audit
        dev.noxclient.chat.NoxChat.init(                // /nox chat|chatkey|chatrelay
            net.minecraft.client.Minecraft.getInstance()
                .gameDirectory.toPath().resolve("nox"));
        dev.noxclient.module.ExtraModules.register(modules);
        dev.noxclient.security.CrashLoopGuard.enforce(modules.all());
        HudElementRegistry.addLast(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("noxclient", "overlay"),
            new NoxOverlay());

        // one-time notice when migrating from the old NoxWindowScreen config
        if (config.legacyGui) {
            dev.noxclient.gui.Toasts.push(
                "NOX ClickGUI ist jetzt Standard — altes GUI via 'noxctl guilegacy'", 0xFFFFD870);
        }

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            dev.noxclient.telemetry.Telemetry.shutdown();
            dev.noxclient.stats.StatsStore.saveAsync();
            config.save(); rpc.stop();
        }));

        // addons (entrypoint "nox:addon") + /nox <name> <args…> dispatcher
        var ctx = new dev.noxclient.api.AddonContext(modules);
        dev.noxclient.api.NoxAddonLoader.load(() -> ctx);
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register(
            (dispatcher, env) -> dispatcher.register(
                net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("nox")
                    .executes(c -> {
                        c.getSource().sendFeedback(net.minecraft.network.chat.Component.literal(
                            "§7[NOX] commands: " + (dev.noxclient.api.AddonContext.COMMANDS.isEmpty()
                                ? "none" : String.join(", ", dev.noxclient.api.AddonContext.COMMANDS.keySet()))));
                        return 1;
                    })
                    .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands
                        .argument("args", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes(c -> {
                            String in = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "args");
                            String[] parts = in.split("\\s+");
                            var h = dev.noxclient.api.AddonContext.COMMANDS.get(parts[0].toLowerCase());
                            if (h == null)
                                c.getSource().sendFeedback(net.minecraft.network.chat.Component.literal(
                                    "§c[NOX] unknown command '" + parts[0] + "'"));
                            else {
                                String[] rest = java.util.Arrays.copyOfRange(parts, 1, parts.length);
                                h.accept(rest);
                            }
                            return 1;
                        }))));
    }

    private void onTick(Minecraft mc) {
        // open is re-derived every tick — ShopScreen/PremiumScreen can replace
        // the window without calling onClose(), which used to wedge the flag
        // (RShift then closed a null screen and keybinds stayed dead).
        NoxWindowScreen.open = mc.gui.screen() instanceof NoxWindowScreen
            || mc.gui.screen() instanceof dev.noxclient.gui.NoxClickGui;
        if (mc.getConnection() == null)
            dev.noxclient.util.MoveGuard.reset();   // no stale queued pos into a new server
        boolean down = InputConstants.isKeyDown(InputConstants.KEY_RSHIFT)
            || dev.noxclient.module.ModuleManager.FAKE_KEYS.contains(InputConstants.KEY_RSHIFT);
        if (down && !guiKeyWasDown && System.currentTimeMillis() - guiClosedAt > 300) {
            if (NoxWindowScreen.open) mc.setScreenAndShow(null);
            else if (mc.gui.screen() == null) mc.setScreenAndShow(openClickGui());
            // screen != null (pause/inventory/chat): don't rip the user out
        }
        guiKeyWasDown = down;
        dev.noxclient.util.DebugConsole.tick(mc);
        NoxOverlay.noteTick();   // feeds the TPS HUD widget (was never called → stuck at 20.0)
        modules.tick(mc);
        dev.noxclient.physics.AnimationSystem.tick(mc);
        dev.noxclient.security.FailSafe.tick();
        if (dev.noxclient.util.TamperGuard.compromised()) {
            dev.noxclient.util.TamperGuard.maybeShowScreen(mc);
            dev.noxclient.util.TamperGuard.warn();
        }
        if (mc.player != null && mc.level != null) config.onServerChange();
        dev.noxclient.premium.LicenseManager.tick();   // silent 24h revalidation
        dev.noxclient.update.UpdateManager.tick();     // 6h signed-update check
        dev.noxclient.telemetry.Telemetry.tick();
        dev.noxclient.stats.Stats.tick();
        dev.noxclient.util.CrashReporter.tick();         // one-shot crash scan
        dev.noxclient.security.Heartbeat.tick();         // 10 min integrity beat
        // first-start privacy consent (Modul 20.1) — once, over the title screen
        if (!dev.noxclient.telemetry.Telemetry.asked()
            && mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen)
            mc.setScreenAndShow(new dev.noxclient.gui.PrivacyScreen());
    }

    /**
     * clickStyle → which GUI opens. "Studio" is the NVG flagship; every other
     * style maps to a NoxWindowScreen layout (Cards/Compact rows/Sidebar/
     * Panels/Hub) so the style picker actually changes what you get.
     */
    private static net.minecraft.client.gui.screens.Screen openClickGui() {
        return "Studio".equals(dev.noxclient.gui.UICustom.clickStyle)
            ? new dev.noxclient.gui.NoxClickGui()
            : new dev.noxclient.gui.NoxWindowScreen();
    }

    public Theme theme() { return Theme.current(); }
}
