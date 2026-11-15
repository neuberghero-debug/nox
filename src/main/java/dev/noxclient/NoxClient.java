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
    public static final String VERSION = "0.1.0-alpha";
    public static final String DISCORD_INVITE = "https://discord.gg/tewtZTsZf5";

    public static NoxClient INSTANCE;
    public ModuleManager modules;
    public ConfigManager config;
    public NoxRPC rpc;
    private boolean guiKeyWasDown;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        modules = new ModuleManager();
        modules.registerAll();
        config = new ConfigManager();
        config.load();
        rpc = new NoxRPC();
        rpc.start();
        dev.noxclient.premium.LicenseManager.init();
        dev.noxclient.util.TamperGuard.verifyAsync();
        HudElementRegistry.addLast(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("noxclient", "overlay"),
            new NoxOverlay());

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { config.save(); rpc.stop(); }));

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
        NoxWindowScreen.open = mc.gui.screen() instanceof NoxWindowScreen;
        boolean down = InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
        if (down && !guiKeyWasDown) {
            if (NoxWindowScreen.open) mc.setScreenAndShow(null);
            else if (mc.gui.screen() == null) mc.setScreenAndShow(new NoxWindowScreen());
            // screen != null (pause/inventory/chat): don't rip the user out
        }
        guiKeyWasDown = down;
        dev.noxclient.util.DebugConsole.tick(mc);
        NoxOverlay.noteTick();   // feeds the TPS HUD widget (was never called → stuck at 20.0)
        modules.tick(mc);
        dev.noxclient.physics.AnimationSystem.tick(mc);
        if (dev.noxclient.util.TamperGuard.compromised()) {
            dev.noxclient.util.TamperGuard.maybeShowScreen(mc);
            dev.noxclient.util.TamperGuard.warn();
        }
        if (mc.player != null && mc.level != null) config.onServerChange();
    }

    public Theme theme() { return Theme.current(); }
}
