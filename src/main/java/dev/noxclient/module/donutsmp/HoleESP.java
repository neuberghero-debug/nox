package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Finds safe crystal-pvp holes: air pockets fully enclosed by obsidian/bedrock. */
public class HoleESP extends Module {
    public final Setting.Num radius = add(new Setting.Num("Radius", 24, 8, 48));
    public final Setting.Bool bedrockOnly = add(new Setting.Bool("Bedrock only", false));
    private long next;

    public static final Map<BlockPos, Integer> HITS = new ConcurrentHashMap<>();

    public HoleESP() { super("HoleESP", "Highlights safe pvp holes", Category.DONUTSMP); }

    private boolean solid(BlockPos p) {
        var b = mc.level.getBlockState(p).getBlock();
        return bedrockOnly.on() ? b == Blocks.BEDROCK
            : b == Blocks.BEDROCK || b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN;
    }

    @Override public void onTick() {
        if (mc.player == null || mc.level == null || System.currentTimeMillis() < next) return;
        next = System.currentTimeMillis() + 1500;
        int r = radius.intVal();
        var pp = mc.player.blockPosition();
        HITS.clear();
        for (BlockPos p : BlockPos.betweenClosed(
                pp.offset(-r, -6, -r), pp.offset(r, 6, r))) {
            if (!mc.level.getBlockState(p).isAir()
                || !mc.level.getBlockState(p.above()).isAir()) continue;
            // floor must be solid AND not lava — holes over lava aren't safe
            var below = mc.level.getBlockState(p.below());
            if (solid(p.north()) && solid(p.south()) && solid(p.east())
                && solid(p.west()) && !below.isAir()
                && below.getFluidState().isEmpty())
                HITS.put(p.immutable(), 0xFF40E080);
        }
    }

    @Override public void onDisable() { HITS.clear(); }
    @Override public String status() { return HITS.size() + " holes marked"; }

}
