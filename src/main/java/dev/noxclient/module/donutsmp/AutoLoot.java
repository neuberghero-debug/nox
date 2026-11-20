package dev.noxclient.module.donutsmp;

import dev.noxclient.NoxClient;
import dev.noxclient.gui.Toasts;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.util.Legitifier;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * AutoLoot — raid helper. When you stand next to found storage it opens each
 * container in reach, hands the contents to ChestStealer (which drains it
 * with legit pacing), closes, moves to the next. One toggle walks the whole
 * loot room instead of click-spamming chests yourself.
 * Arms ChestStealer while looting (restores its state after).
 * Right-clicks blocks like a normal player — MoveGuard rides the rotation
 * on vanilla packets, no extra movement traffic.
 */
public class AutoLoot extends Module {
    private final Setting.Num reach = add(new Setting.Num("Reach", 4.2, 2.5, 6));
    private final Setting.Num cps = add(new Setting.Num("Actions/s", 3, 1, 10));
    private final Setting.Bool armStealer = add(new Setting.Bool("Arm ChestStealer", true,
        "Temporarily enable ChestStealer to drain open containers"));
    private final Setting.Bool silentRot = add(new Setting.Bool("Silent rotate", true,
        "Face containers via MoveGuard — visible clientside only"));

    private final Deque<BlockPos> queue = new ArrayDeque<>();
    private final Set<BlockPos> done = new HashSet<>();
    private BlockPos opening;
    private long openedAt;
    private boolean stealerWas;
    private Module stealer;

    public AutoLoot() { super("AutoLoot", "Opens + drains every container in reach",
        Category.DONUTSMP); }

    @Override protected void onEnable() {
        queue.clear(); done.clear(); opening = null; openedAt = -1;
        stealer = NoxClient.INSTANCE.modules.get("ChestStealer");
        stealerWas = stealer != null && stealer.isEnabled();
        if (armStealer.on() && stealer != null && !stealerWas) stealer.setEnabled(true);
        Toasts.push("AutoLoot — near containers will be opened", 0xFF66D9A0);
    }

    @Override protected void onDisable() {
        if (stealer != null && !stealerWas && stealer.isEnabled()) stealer.setEnabled(false);
        queue.clear(); done.clear(); opening = null;
    }

    @Override public String status() {
        return queue.isEmpty() && opening == null ? "idle" :
            "queue " + queue.size() + (opening != null ? " — looting" : "");
    }

    @Override public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (mc.player.tickCount < 60) return;

        // a container screen is open → ChestStealer drains; wait for it to close
        if (mc.gui.screen() instanceof AbstractContainerScreen) return;
        // screen closed after a loot — mark done, next
        if (opening != null && openedAt > 0) {
            done.add(opening);
            opening = null; openedAt = -1;
            // brief settle between containers
            return;
        }

        if (queue.isEmpty() && !fillQueue()) return;
        BlockPos next = queue.peek();
        if (next == null) return;
        // out of reach or block gone → drop it
        var st = mc.level.getBlockState(next);
        if (!isLootable(st.getBlock())
            || mc.player.blockPosition().distSqr(next) > reach.get() * reach.get() + 1) {
            queue.poll(); done.add(next); return;
        }

        if (!Legitifier.ready(this, cps.get(), 0.35)) return;
        if (silentRot.on()) {
            Vec3 eye = mc.player.getEyePosition();
            Vec3 c = Vec3.atCenterOf(next);
            double dx = c.x - eye.x, dy = c.y - eye.y, dz = c.z - eye.z;
            float yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
            float pitch = (float) (Math.toDegrees(-Math.atan2(dy, Math.hypot(dx, dz))));
            dev.noxclient.util.MoveGuard.requestRot(yaw, pitch);
        }
        var hit = new BlockHitResult(Vec3.atCenterOf(next), Direction.UP, next, false);
        var res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        if (res.consumesAction()) {
            opening = next; openedAt = System.currentTimeMillis();
            queue.poll();
        } else {
            // couldn't open (claims/plugins) — skip it but keep going
            queue.poll(); done.add(next);
        }
    }

    private boolean fillQueue() {
        var p = mc.player.blockPosition();
        int r = (int) Math.ceil(reach.get());
        java.util.List<BlockPos> found = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(p.offset(-r, -r, -r), p.offset(r, r, r))) {
            if (done.contains(pos.immutable())) continue;
            if (isLootable(mc.level.getBlockState(pos).getBlock()))
                found.add(pos.immutable());
        }
        found.sort(Comparator.comparingDouble(p::distSqr));
        queue.addAll(found);
        return !queue.isEmpty();
    }

    private boolean isLootable(Block b) {
        return b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.BARREL
            || b == Blocks.SHULKER_BOX || b == Blocks.FURNACE || b == Blocks.BLAST_FURNACE
            || b == Blocks.SMOKER || b == Blocks.HOPPER || b == Blocks.BREWING_STAND;
    }
}
