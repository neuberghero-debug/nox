package dev.noxclient.util;

import dev.noxclient.NoxClient;
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

/**
 * Whole-suite verification in ONE session — no per-module relaunches.
 * For every registered module: status() → enable (via restoreEnabled so the
 * premium gate screen never steals focus) → onTick() ×3 → every setting is
 * exercised (wrong-type coercion, Num min/mid/max, Mode cycle, Bool flip,
 * Str write) → onTick() again → disable + onDisable(). Mass-disablers
 * (GhostMode/Panic) run in a last pass; the pre-test enabled set is
 * restored afterwards. Every Throwable is caught per step — one bad module
 * can't abort the suite. Report lands in <gameDir>/nox_selftest.log.
 */
public final class ModuleSelfTest {
    private static final Set<String> LAST = Set.of("GhostMode", "Panic");

    public static String run(Minecraft mc) {
        int ok = 0, fail = 0, info = 0;
        List<String> out = new ArrayList<>();
        Set<Module> wasEnabled = new HashSet<>();
        for (Module m : NoxClient.INSTANCE.modules.all()) if (m.isEnabled()) wasEnabled.add(m);
        long t0 = System.currentTimeMillis();

        List<Module> mods = new ArrayList<>(NoxClient.INSTANCE.modules.all());
        mods.sort((a, b) -> Boolean.compare(LAST.contains(a.name), LAST.contains(b.name)));

        for (Module m : mods) {
            String tag = "OK";
            List<String> notes = new ArrayList<>();
            try { String st = m.status(); if (st == null) notes.add("status()=null"); }
            catch (Throwable t) { tag = "FAIL"; notes.add("status() " + t); }

            // enable → tick → settings → tick → disable
            boolean stayed;
            try {
                m.restore(true);
                stayed = m.isEnabled();
                if (!stayed && !LAST.contains(m.name))
                    notes.add("onEnable self-disabled (status-only?)");
            } catch (Throwable t) { tag = "FAIL"; notes.add("enable " + t); stayed = false; }

            for (int i = 0; i < 3; i++)
                try { m.onTick(); } catch (Throwable t) { tag = "FAIL"; notes.add("onTick#" + i + " " + t); break; }

            for (Setting<?> s : m.settings) {
                try {
                    exercise(s);
                    m.onTick();           // live-update path under mutation
                    s.reset();
                } catch (Throwable t) { tag = "FAIL"; notes.add(s.name + ": " + t); }
            }

            try { m.restore(false); } catch (Throwable t) { tag = "FAIL"; notes.add("disable " + t); }

            if (notes.isEmpty()) ok++;
            else if (tag.equals("FAIL")) fail++;
            else info++;
            out.add(tag + " " + m.name + (notes.isEmpty() ? "" : "  — " + String.join(" | ", notes)));
        }

        // restore whatever was on before the run + close stray screens
        for (Module m : NoxClient.INSTANCE.modules.all())
            try { m.restore(wasEnabled.contains(m)); } catch (Throwable ignored) {}
        try { mc.setScreenAndShow(null); } catch (Throwable ignored) {}

        String head = "selftest " + mods.size() + " modules in "
            + (System.currentTimeMillis() - t0) + "ms — ok:" + ok + " info:" + info + " FAIL:" + fail;
        out.add(0, head);
        try {
            Path f = FabricLoader.getInstance().getGameDir().resolve("nox_selftest.log");
            try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(f)))
            { for (String l : out) pw.println(l); }
            return head + " → nox_selftest.log";
        } catch (Exception e) { return head; }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void exercise(Setting<?> s) {
        ((Setting) s).set("!!bogus!!");          // coercion: typed get() must survive a wrong raw type
        Object v = s.get();                        // must not throw
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
