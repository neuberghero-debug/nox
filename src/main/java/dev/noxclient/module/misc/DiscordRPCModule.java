package dev.noxclient.module.misc;

import dev.noxclient.NoxClient;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;

public class DiscordRPCModule extends Module {
    public DiscordRPCModule() { super("DiscordRPC", "Discord Rich Presence", Category.MISC); }

    @Override
    protected void onEnable() { NoxClient.INSTANCE.rpc.setEnabled(true); }
    @Override
    protected void onDisable() { NoxClient.INSTANCE.rpc.setEnabled(false); }
}
