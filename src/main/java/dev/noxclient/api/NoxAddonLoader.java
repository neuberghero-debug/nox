package dev.noxclient.api;

import net.fabricmc.loader.api.FabricLoader;

/** Discovers and initializes addons via the "nox:addon" entrypoint. */
public final class NoxAddonLoader {
    public static void load(ModuleManagerHolder holder) {
        for (var c : FabricLoader.getInstance().getEntrypointContainers("nox:addon", NoxAddon.class)) {
            try {
                c.getEntrypoint().register(holder.context());
            } catch (Throwable t) {
                System.err.println("[NOX] addon failed: " + c.getProvider().getMetadata().getId());
            }
        }
    }
    public interface ModuleManagerHolder { NoxAddonContext context(); }
}
