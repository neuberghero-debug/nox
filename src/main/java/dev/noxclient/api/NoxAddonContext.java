package dev.noxclient.api;

import dev.noxclient.module.Module;
import dev.noxclient.module.ModuleManager;

/** Everything an addon is allowed to touch. */
public interface NoxAddonContext {
    /** Registers a module — dedup by name, shows in ClickGUI, settings persist. */
    void registerModule(Module module);
    ModuleManager modules();
    /** `/nox <name> <args…>` — args arrive 0-indexed. */
    void registerCommand(String name, java.util.function.Consumer<String[]> handler);

    /** Push a toast on the client overlay. */
    default void toast(String msg, int argb) { dev.noxclient.gui.Toasts.push(msg, argb); }
    default void toastInfo(String msg) { dev.noxclient.gui.Toasts.info(msg); }
    /** Send a local chat line ([NOX]-style); no-op before join. */
    default void chat(String msg) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null)
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§d[NOX] §f" + msg));
    }
}
