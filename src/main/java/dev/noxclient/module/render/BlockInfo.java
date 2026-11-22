package dev.noxclient.module.render;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.util.EspMarks;
import dev.noxclient.setting.Setting;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;

/** Highlights the block you look at + shows its name in chat on click-inspect. */
public class BlockInfo extends Module {
    public final Setting.Bool highlight = add(new Setting.Bool("Highlight", true));
    public final Setting.Bool chatName = add(new Setting.Bool("Log looked-at block", false));
    private Object last;

    public BlockInfo() { super("BlockInfo", "Highlights looked-at block", Category.RENDER); }

    @Override public void onTick() {
        if (mc.player == null || mc.level == null) return;
        if (last != null) { EspMarks.MARKS.remove(last); last = null; }
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) return;
        var pos = ((BlockHitResult) mc.hitResult).getBlockPos();
        if (highlight.on()) {
            EspMarks.MARKS.put(pos, 0xFFFFFFFF);
            last = pos;
        }
        if (chatName.on()) {
            var name = mc.level.getBlockState(pos).getBlock().getName().getString();
            dev.noxclient.gui.Toasts.push(name + " @ " + pos.toShortString(), 0xFF9C9CB8);
            chatName.set(false);
        }
    }
}
