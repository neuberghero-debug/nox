package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import net.minecraft.network.chat.Component;

/**
 * Two-stage trigger: first enable arms it (chat warning), second enable within
 * 3 seconds executes the wipe. Prevents accidental activation.
 */
public class SelfDestruct extends Module {
    private long armedAt;

    public SelfDestruct() { super("SelfDestruct", "Removes NOX from RAM and disk", Category.MISC); }

    @Override
    public void onEnable() {
        long now = System.currentTimeMillis();
        if (now - armedAt < 3000) {
            dev.noxclient.util.SelfDestruct.execute();
        } else {
            armedAt = now;
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal(
                "§c§l[NOX] Self-destruct ARMED — toggle again within 3s to confirm."));
            setEnabled(false);
        }
    }
}
