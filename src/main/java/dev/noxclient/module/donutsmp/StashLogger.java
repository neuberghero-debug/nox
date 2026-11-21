package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Writes every StorageESP/finder hit to nox-stashes.txt — base hunt log. */
public class StashLogger extends Module {
    public final Setting.Num interval = add(new Setting.Num("Log interval s", 15, 5, 120));
    private final java.util.Set<String> seen = new java.util.HashSet<>();
    private long last;

    public StashLogger() { super("StashLogger", "Logs found stashes to file", Category.DONUTSMP); }

    @Override public void onTick() {
        if (mc.player == null || System.currentTimeMillis() - last < interval.get() * 1000) return;
        last = System.currentTimeMillis();
        StringBuilder out = new StringBuilder();
        for (var m : dev.noxclient.module.donutsmp.StorageESP.HITS.keySet()) {
            String k = m.getX() + "," + m.getY() + "," + m.getZ();
            if (seen.add(k)) {
                out.append(k).append("  @  ")
                    .append(java.time.LocalTime.now().withNano(0)).append("; ");
                dev.noxclient.util.StashIndex.add("storage", "chest", m);   // Modul 7
            }
        }
        for (var m : dev.noxclient.module.donutsmp.AntiESPBypass.MARKS.keySet()) {
            String k = "mark:" + m.getX() + "," + m.getY() + "," + m.getZ();
            if (seen.add(k)) {
                out.append(k).append("; ");
                dev.noxclient.util.StashIndex.add("anti-esp", "marked", m);
            }
        }
        for (var m : dev.noxclient.module.donutsmp.SoundFind.MARKS.values()) {
            String k = "sound:" + m.label + "@" + (int) m.x + "," + (int) m.y + "," + (int) m.z;
            if (seen.add(k)) {
                out.append(k)
                    .append(m.deep ? "(deep)" : "").append(" ×").append(m.count).append("; ");
                dev.noxclient.util.StashIndex.add("sound", "sound",
                    new net.minecraft.core.BlockPos((int) m.x, (int) m.y, (int) m.z));
            }
        }
        if (out.isEmpty()) return;
        // file write off the main thread — the log only grows, sync IO here
        // hitched the game once the file got large
        String text = out + System.lineSeparator();
        Path p = mc.gameDirectory.toPath().resolve("nox-stashes.txt");
        Thread t = new Thread(() -> {
            try {
                Files.writeString(p, text,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception ignored) {}
        }, "nox-stashlog");
        t.setDaemon(true); t.start();
        mc.player.sendSystemMessage(Component.literal(
            "§6[NOX] logged " + out.toString().split(";").length + " stash mark(s)"));
        if (seen.size() > 50_000) seen.clear();   // long AFK sessions — re-log
    }
}
