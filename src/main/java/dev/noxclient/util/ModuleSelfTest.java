package dev.noxclient.util;

import dev.noxclient.NoxClient;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.client.Minecraft;
import net.fabricmc.loader.api.FabricLoader;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Whole-suite verification in ONE session — no per-module relaunches.
 * Runs on a worker thread; every module phase is dispatched onto the render
 * thread via mc.execute with a per-module timeout. A hanging module gets
 * named in the report instead of freezing the client silently.
 *
 * On a live server (level + server present) modules that produce real-world
 * side effects — commands, packets, purchases, teleports, disconnects,
 * process death — get a settings/status-only pass; they are never enabled.
 * SelfDestruct is never enabled anywhere.
 */
public final class ModuleSelfTest {
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    public static volatile String current = "";
    public static volatile String summary = "not run";

    /** Enable-last: mass-disablers must run after everything else. */
    private static final Set<String> LAST = Set.of("GhostMode", "Panic");
    /** Never enabled — not even on the title screen. */
    private static final Set<String> NEVER = Set.of("SelfDestruct");
    /** Side effects on a live server → settings/status only. */
    private static final Set<String> LIVE_UNSAFE = Set.of(
        "SelfDestruct", "GhostMode", "Panic", "OpSec", "StaffNotifier",
        "WaypointShare", "AutoReconnect", "AutoLog", "ChatTriggers",
        "TwitchChat", "DonutWheel", "Announcer", "AutoTPAccept",
        "SecurityAudit", "Replay", "MiddleClickExtra",
        "SkinChanger", "StreamerMode", "NoInteract");

    private static boolean safeLive(Module m, boolean live) {
        if (!live) return !NEVER.contains(m.name);
        if (NEVER.contains(m.name) || LIVE_UNSAFE.contains(m.name)) return false;
        return m.category != Category.COMBAT && m.category != Category.MOVEMENT
            && m.category != Category.DONUTSMP;
    }

    public static String run(Minecraft mc) {
        if (!RUNNING.compareAndSet(false, true))
            return "modtest running: " + current + " | last: " + summary;
        new Thread(() -> runAll(mc), "nox-modtest").start();
        return "modtest started (async, timeout-guarded) — `modtest` again = progress";
    }

    private static void runAll(Minecraft mc) {
        List<String> out = new ArrayList<>();
        int ok = 0, fail = 0, info = 0;
        long t0 = System.currentTimeMillis();
        try {
            Set<String> wasEnabled = new HashSet<>();
            for (Module m : NoxClient.INSTANCE.modules.all()) if (m.isEnabled()) wasEnabled.add(m.name);
            List<Module> mods = new ArrayList<>(NoxClient.INSTANCE.modules.all());
            mods.sort((a, b) -> Boolean.compare(LAST.contains(a.name), LAST.contains(b.name)));

            for (Module m : mods) {
                current = m.name;
                FutureTask<List<String>> task = new FutureTask<>(() -> testOne(m, mc));
                mc.execute(task);
                List<String> notes;
                String tag;
                try {
                    notes = task.get(20, TimeUnit.SECONDS);
                    tag = notes.stream().anyMatch(n -> n.startsWith("FAIL")) ? "FAIL" : "OK";
                } catch (TimeoutException te) {
                    notes = List.of("FAIL HANG >20s on render thread — module froze the client");
                    tag = "FAIL";
                } catch (Throwable t) {
                    notes = List.of("FAIL harness " + t);
                    tag = "FAIL";
                }
                if (notes.isEmpty()) ok++;
                else if (tag.equals("FAIL")) fail++;
                else info++;
                if (!notes.isEmpty()) out.add(tag + " " + m.name + "  — " + String.join(" | ", notes));
                else out.add("OK " + m.name);
                // restore pre-run state continuously so a hang leaves less residue
                try { mc.execute(() -> m.restore(wasEnabled.contains(m.name))); }
                catch (Throwable ignored) {}
            }
            try { mc.execute(() -> mc.setScreenAndShow(null)); } catch (Throwable ignored) {}
        } finally {
            summary = "modtest done — ok:" + ok + " info:" + info + " FAIL:" + fail
                + " in " + (System.currentTimeMillis() - t0) + "ms";
            out.add(0, summary);
            current = "";
            RUNNING.set(false);
            try {
                Path f = FabricLoader.getInstance().getGameDir().resolve("nox_selftest.log");
                try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(f)))
                { for (String l : out) pw.println(l); }
            } catch (Throwable ignored) {}
        }
    }

    /** Runs ON the render thread (mc.execute). Returns notes; FAIL-prefixed = broken. */
    private static List<String> testOne(Module m, Minecraft mc) {
        List<String> notes = new ArrayList<>();
        boolean live = mc.level != null && mc.getCurrentServer() != null;
        try { String st = m.status(); if (st == null) notes.add("status()=null"); }
        catch (Throwable t) { notes.add("FAIL status() " + t); }

        if (safeLive(m, live)) {
            try {
                m.restore(true);
                if (!m.isEnabled() && !LAST.contains(m.name))
                    notes.add("onEnable self-disabled (status-only?)");
            } catch (Throwable t) { notes.add("FAIL enable " + t); }

            for (int i = 0; i < 3; i++)
                try { m.onTick(); } catch (Throwable t) { notes.add("FAIL onTick#" + i + " " + t); break; }
        } else {
            notes.add("skip enable (live-unsafe/destructive)");
        }

        for (Setting<?> s : m.settings) {
            try {
                exercise(s);
                if (safeLive(m, live)) m.onTick();   // live-update under mutation
                s.reset();
            } catch (Throwable t) { notes.add("FAIL " + s.name + ": " + t); }
        }

        try { m.restore(false); } catch (Throwable t) { notes.add("FAIL disable " + t); }
        return notes;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void exercise(Setting<?> s) {
        ((Setting) s).set("!!bogus!!");          // coercion: typed get() must survive a wrong raw type
        s.get();                                   // must not throw
        if (s instanceof Setting.Num n) {
            n.set(n.min); n.get();
            n.set(n.max); n.get();
            n.set((n.min + n.max) / 2); n.get();
        } else if (s instanceof Setting.Bool b) {
            b.set(false); b.get(); b.set(true); b.get();
        } else if (s instanceof Setting.Mode mo) {
            for (String opt : mo.modes) { mo.set(opt); mo.get(); }
            mo.set("__invalid__"); mo.get();       // invalid mode must fall back
        } else if (s instanceof Setting.Str) {
            ((Setting) s).set("noxtest"); s.get();
        } else {
            s.reset(); s.get();
        }
    }
}
