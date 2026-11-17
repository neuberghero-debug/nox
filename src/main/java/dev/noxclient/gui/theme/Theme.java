package dev.noxclient.gui.theme;

/** A GUI look-and-feel. Add new themes here — the picker reads this list. */
public abstract class Theme {
    public static final Theme[] ALL = { new NoxDark(), new Midnight(), new Snow(), new Custom(),
        new Winter(), new Spring(), new Summer(), new Autumn() };
    /** Season lookup by UI name — "" if not a season theme. */
    public static String seasonName() {
        String n = ALL[index].name();
        return switch (n) { case "Winter", "Spring", "Summer", "Autumn" -> n; default -> ""; };
    }
    /** Calendar season → theme index in ALL (Dec-Feb Winter … Sep-Nov Autumn). */
    public static int seasonIndex() {
        return switch (java.time.LocalDate.now().getMonthValue()) {
            case 12, 1, 2 -> indexOf("Winter");
            case 3, 4, 5 -> indexOf("Spring");
            case 6, 7, 8 -> indexOf("Summer");
            default -> indexOf("Autumn");
        };
    }
    public static int indexOf(String name) {
        for (int i = 0; i < ALL.length; i++) if (ALL[i].name().equals(name)) return i;
        return 0;
    }
    private static int index;
    public static Theme current() { return ALL[index]; }
    public static void cycle() { index = (index + 1) % ALL.length; }
    public static void set(int i) { index = Math.floorMod(i, ALL.length); }
    public static int index() { return index; }
    public static String currentName() { return ALL[index].name(); }

    public abstract String name();
    public abstract int background();   // ARGB
    public abstract int accent();
    public abstract int text();
    public abstract int moduleEnabled();
    public abstract int header();
    public float rounding() { return 4f; }
    public float animationSpeed() { return 8f; }

    public static class NoxDark extends Theme {
        public String name() { return "NOX Dark"; }
        public int background() { return 0xEE0D0F14; }
        public int accent() { return 0xFF7A2BE2; }
        public int text() { return 0xFFE8E8F0; }
        public int moduleEnabled() { return 0xFF9B4DFF; }
        public int header() { return 0xFF10141C; }
    }
    public static class Midnight extends Theme {
        public String name() { return "Midnight"; }
        public int background() { return 0xF0050A14; }
        public int accent() { return 0xFF1E90FF; }
        public int text() { return 0xFFDCEBFF; }
        public int moduleEnabled() { return 0xFF3FA9F5; }
        public int header() { return 0xFF0A1428; }
    }
    /** Fully user-driven look — fed by the Interface module / UICustom. */
    public static class Custom extends Theme {
        public String name() { return "Custom"; }
        public int background() { return dev.noxclient.gui.UICustom.bg; }
        public int accent() { return dev.noxclient.gui.UICustom.accent; }
        public int text() { return dev.noxclient.gui.UICustom.text; }
        public int moduleEnabled() { return dev.noxclient.gui.UICustom.accent; }
        public int header() { return dev.noxclient.gui.UICustom.header; }
    }
    public static class Winter extends Theme {
        public String name() { return "Winter"; }
        public int background() { return 0xF00A1322; }
        public int accent() { return 0xFF5AC8E8; }
        public int text() { return 0xFFDFF2FF; }
        public int moduleEnabled() { return 0xFF6FD8F5; }
        public int header() { return 0xFF0C1A2E; }
    }
    public static class Spring extends Theme {
        public String name() { return "Spring"; }
        public int background() { return 0xEE0B1510; }
        public int accent() { return 0xFF7CE38B; }
        public int text() { return 0xFFEAF6EC; }
        public int moduleEnabled() { return 0xFF8FE89B; }
        public int header() { return 0xFF10201A; }
    }
    public static class Summer extends Theme {
        public String name() { return "Summer"; }
        public int background() { return 0xF0161210; }
        public int accent() { return 0xFFFFB347; }
        public int text() { return 0xFFFFF2E2; }
        public int moduleEnabled() { return 0xFFFFC464; }
        public int header() { return 0xFF1E1810; }
    }
    public static class Autumn extends Theme {
        public String name() { return "Autumn"; }
        public int background() { return 0xF0140F0A; }
        public int accent() { return 0xFFD9742B; }
        public int text() { return 0xFFF6EADD; }
        public int moduleEnabled() { return 0xFFE8863C; }
        public int header() { return 0xFF1D140C; }
    }
    public static class Snow extends Theme {
        public String name() { return "Snow"; }
        public int background() { return 0xF0F2F4FA; }
        public int accent() { return 0xFF4B6BFB; }
        public int text() { return 0xFF1A1D26; }
        public int moduleEnabled() { return 0xFF4B6BFB; }
        public int header() { return 0xFFE4E8F2; }
    }
}
