package dev.noxclient.api;

import dev.noxclient.module.Module;
import dev.noxclient.module.ModuleManager;

import java.util.List;

/** Everything an addon is allowed to touch. */
public interface NoxAddonContext {
    void registerModule(Module module);
    ModuleManager modules();
    void registerCommand(String name, java.util.function.Consumer<String[]> handler);
}
