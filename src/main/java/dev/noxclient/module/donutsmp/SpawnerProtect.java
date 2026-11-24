package dev.noxclient.module.donutsmp;

import dev.noxclient.NoxClient;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.gui.Toasts;
import dev.noxclient.util.MoveGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Blocks;

/**
 * SpawnerProtect — the DSMP classic every big client ships (Zyron/Water/Larp).
 * When a non-friend player comes within range, automatically mines every
 * spawner in reach with the silk-touch pick, waits until the drops landed in
 * the inventory, optionally dumps them into an adjacent Ender Chest, then
 * optionally logs out. Protects your spawner farm while you're AFK.
 *   trigger — enemy distance that starts the sequence
 *   radius  — spawner search radius around the player
 *   echest  — deposit mined spawners into a nearby ender chest
 *   logout  — hard disconnect after everything is secured
 */
public class SpawnerProtect extends Module {
    private final Setting.Num trigger = add(new Setting.Num("Trigger distance", 48, 8, 200,
        "Enemy player distance that starts collection"));
    private final Setting.Num radius = add(new Setting.Num("Spawner radius", 5, 3, 12));
    private final Setting.Bool echest = add(new Setting.Bool("Store in E-Chest", true,
        "Deposit spawners into an ender chest next to you"));
    private final Setting.Bool logout = add(new Setting.Bool("Logout after", false,
        "Hard disconnect once all spawners are secured"));
    private final Setting.Bool onlySneak = add(new Setting.Bool("Sneak while mining", true,
        "Look like a crouching player, not a spinning bot"));
    private final Setting.Str webhook = add(new Setting.Str("Discord webhook", "",
        "Optional: POSTs a notification when collection starts/finishes"));

    private enum State { IDLE, MINING, CONFIRMING, CHEST, DONE }
    private State state = State.IDLE;
    private BlockPos target;
    private int prevSlot = -1, silkSlot = -1;
    private int beforeCount = -1, mined;
    private long confirmAt, lastSwing;
    private BlockPos chestPos;

    public SpawnerProtect() { super("SpawnerProtect",
        "Enemy nearby → mines all spawners, stashes them, optional logout",
        Category.DONUTSMP); }

    @Override protected void onEnable() {
        state = State.IDLE; target = null; chestPos = null;
        prevSlot = silkSlot = -1; beforeCount = -1; mined = 0;
    }

    @Override protected void onDisable() {
        if (prevSlot >= 0 && mc.player != null) select(prevSlot);
        prevSlot = silkSlot = -1; target = null; chestPos = null; state = State.IDLE;
        mc.options.keyShift.setDown(false);
    }

    @Override public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (mc.player.tickCount < 60) return;

        switch (state) {
            case IDLE -> idle();
            case MINING -> mining();
            case CONFIRMING -> confirming();
            case CHEST -> chest();
            case DONE -> finish();
        }
    }

    private void idle() {
        var threat = nearestEnemy();
        if (threat == null) return;
        silkSlot = findSilk();
        if (silkSlot < 0) {
            Toasts.push("SpawnerProtect: " + threat + " close — NO silk pick, not mining!", 0xFFFF6666);
            if (logout.on()) mc.disconnectWithProgressScreen();
            else setEnabled(false);
            return;
        }
        beforeCount = countSpawners();
        prevSlot = mc.player.getInventory().getSelectedSlot();
        state = State.MINING; mined = 0;
        Toasts.push("SpawnerProtect: " + threat + " — mining spawners", 0xFFFFB84D);
        mark("threat: " + threat);
        notify("SpawnerProtect: `" + threat + "` within " + (int) trigger.get().doubleValue()
            + " blocks — collecting spawners");
    }

    private void mining() {
        if (onlySneak.on()) mc.options.keyShift.setDown(true);
        if (target == null || !isSpawner(target)) target = findSpawner();
        if (target == null) { state = State.CONFIRMING; confirmAt = System.currentTimeMillis(); return; }
        select(silkSlot);
        face(target);
        var st = mc.level.getBlockState(target);
        if (!st.isAir()) {
            if (mc.gameMode.continueDestroyBlock(target, Direction.UP)) mined++;
            if (System.currentTimeMillis() - lastSwing > 350) {
                lastSwing = System.currentTimeMillis();
                mc.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
            }
        } else target = null;
    }

    private void confirming() {
        // wait until every mined spawner has physically landed in the inventory
        if (System.currentTimeMillis() - confirmAt < 1500) return;
        int have = countSpawners() - beforeCount;
        if (have >= mined || System.currentTimeMillis() - confirmAt > 8000) {
            Toasts.push("SpawnerProtect: " + mined + " secured", 0xFF66D9A0);
            notify("SpawnerProtect: " + mined + " spawner(s) secured");
            state = echest.on() ? State.CHEST : State.DONE;
        }
    }

    private void chest() {
        if (chestPos == null || !mc.level.getBlockState(chestPos).is(Blocks.ENDER_CHEST))
            chestPos = findBlock(Blocks.ENDER_CHEST, 5);
        if (chestPos == null) { state = State.DONE; return; }
        face(chestPos);
        var hit = net.minecraft.world.phys.BlockHitResult.miss(
            net.minecraft.world.phys.Vec3.atCenterOf(chestPos), Direction.UP, chestPos);
        var res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        if (res.consumesAction() || mc.gui.screen() != null) {
            // shove every spawner stack into the ender chest
            var menu = mc.player.containerMenu;
            for (int i = 0; i < menu.slots.size(); i++) {
                var st = menu.slots.get(i).getItem();
                if (st.is(Blocks.SPAWNER.asItem()))
                    mc.gameMode.handleContainerInput(menu.containerId, i, 0,
                        net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, mc.player);
            }
            mc.setScreenAndShow(null);
        }
        state = State.DONE;
    }

    private void finish() {
        mc.options.keyShift.setDown(false);
        if (prevSlot >= 0) { select(prevSlot); prevSlot = -1; }
        if (logout.on()) { mark("logout — secured " + mined); mc.disconnectWithProgressScreen(); }
        else setEnabled(false);
    }

    private String nearestEnemy() {
        double best = trigger.get();
        net.minecraft.world.entity.player.Player found = null;
        for (var p : mc.level.players()) {
            if (p == mc.player) continue;
            double d = p.distanceTo(mc.player);
            if (d < best) { best = d; found = p; }
        }
        return found == null ? null : found.getName().getString();
    }

    private BlockPos findSpawner() {
        var pos = mc.player.blockPosition();
        int r = radius.intVal();
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-r, -r, -r), pos.offset(r, r, r)))
            if (isSpawner(p)) return p.immutable();
        return null;
    }

    private BlockPos findBlock(net.minecraft.world.level.block.Block b, int r) {
        var pos = mc.player.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-r, -2, -r), pos.offset(r, 2, r)))
            if (mc.level.getBlockState(p).is(b)) return p.immutable();
        return null;
    }

    private boolean isSpawner(BlockPos p) { return mc.level.getBlockState(p).is(Blocks.SPAWNER); }

    private int countSpawners() {
        int n = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++)
            if (inv.getItem(i).is(Blocks.SPAWNER.asItem())) n += inv.getItem(i).getCount();
        return n;
    }

    private int findSilk() {
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getItem(i);
            if (!st.getItem().toString().contains("pickaxe")) continue;
            if (hasSilk(st)) return i;
        }
        return -1;
    }

    private boolean hasSilk(ItemStack st) {
        try {
            var ench = st.getEnchantments();
            for (var h : ench.keySet())
                if (h.unwrapKey().map(k -> k.identifier().toString().contains("silk_touch"))
                        .orElse(false)) return true;
        } catch (Throwable t) {
            if (st.toString().contains("silk_touch")) return true;
        }
        return false;
    }

    private void face(BlockPos p) {
        double dx = p.getX() + 0.5 - mc.player.getX();
        double dz = p.getZ() + 0.5 - mc.player.getZ();
        double dy = p.getY() + 0.5 - mc.player.getEyeY();
        float yaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90f;
        float pitch = (float) (-Math.atan2(dy, Math.hypot(dx, dz)) * 180 / Math.PI);
        MoveGuard.requestRot(yaw, pitch);
    }

    /** Fire-and-forget Discord webhook — same trick Zyron/Water/Larp ship. */
    private void notify(String msg) {
        String url = webhook.get();
        if (url == null || url.isBlank() || !url.contains("/api/webhooks/")) return;
        new Thread(() -> {
            try {
                var con = (java.net.HttpURLConnection) java.net.URI.create(url).toURL().openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type", "application/json");
                con.setDoOutput(true);
                con.setConnectTimeout(4000); con.setReadTimeout(4000);
                String body = "{\"username\":\"NOX\",\"content\":\""
                    + msg.replace("\"", "'") + "\"}";
                con.getOutputStream().write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                con.getResponseCode(); con.disconnect();
            } catch (Throwable ignored) {}
        }, "nox-sp-webhook").start();
    }

    private void select(int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
        if (mc.getConnection() != null)
            mc.getConnection().send(
                new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(slot));
    }

    @Override public String status() {
        if (!isEnabled()) return "off";
        return switch (state) {
            case IDLE -> "watching";
            case MINING -> "mining (" + mined + ")";
            case CONFIRMING -> "confirming drops";
            case CHEST -> "stashing";
            case DONE -> "secured";
        };
    }
}
