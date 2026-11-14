package dev.noxclient.api;

import dev.noxclient.module.Module;
import dev.noxclient.module.ModuleManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Live implementation of the addon surface: modules + chat commands. */
public final class AddonContext implements NoxAddonContext {
    /** name → handler; dispatched by the /nox client command. */
    public static final Map<String, Consumer<String[]>> COMMANDS = new ConcurrentHashMap<>();
    private final ModuleManager modules;

    public AddonContext(ModuleManager modules) { this.modules = modules; }

    @Override
    public void registerModule(Module module) { modules.all().add(module); }

    @Override
    public ModuleManager modules() { return modules; }

    @Override
    public void registerCommand(String name, Consumer<String[]> handler) {
        COMMANDS.put(name.toLowerCase(), handler);
    }
}
