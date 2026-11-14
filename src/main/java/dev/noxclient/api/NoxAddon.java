package dev.noxclient.api;

/**
 * Public addon API. Addons put "nox:addon" in their fabric.mod.json entrypoints
 * and implement this interface; NOX calls register() after ModuleManager init.
 */
public interface NoxAddon {
    String name();
    String author();
    void register(NoxAddonContext ctx);
}
