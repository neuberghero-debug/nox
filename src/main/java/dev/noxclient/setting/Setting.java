package dev.noxclient.setting;

public abstract class Setting<T> {
    public final String name;
    protected final T def;
    private T value;
    protected Setting(String name, T def) { this.name = name; this.def = def; this.value = def; }
    public T get() { return value; }
    public void set(T v) { value = v; }

    protected static double numOr(Object v, double def) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) try { return Double.parseDouble(s.trim()); }
            catch (NumberFormatException ignored) {}
        return def;
    }
    protected static int intOr(Object v, int def) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) try { return (int) Long.parseLong(s.trim()); }
            catch (NumberFormatException ignored) {}
        return def;
    }

    public static class Bool extends Setting<Boolean> {
        public Bool(String n, boolean d) { super(n, d); }
        @Override public Boolean get() {
            Object v = super.get();
            if (v instanceof Boolean b) return b;
            if (v instanceof String s) return Boolean.parseBoolean(s);
            return def;
        }
        public boolean on() { return get(); }
    }
    public static class Num extends Setting<Double> {
        public final double min, max;
        public Num(String n, double d, double min, double max) { super(n, d); this.min = min; this.max = max; }
        @Override public Double get() { return numOr(super.get(), def); }
        public int intVal() { return get().intValue(); }
    }
    public static class Mode extends Setting<String> {
        public final String[] modes;
        public Mode(String n, String d, String... modes) { super(n, d); this.modes = modes; }
        @Override public String get() {
            Object v = super.get();
            if (!(v instanceof String s)) return def;
            for (String m : modes) if (m.equals(s)) return s;
            return def;   // stale/typo'd mode names fall back instead of confusing callers
        }
    }
    public static class Str extends Setting<String> {
        public Str(String n, String d) { super(n, d); }
        @Override public String get() {
            Object v = super.get();
            return v instanceof String s ? s : def;
        }
    }
    /** Keybind capture — SDL key code, -1 unbound. */
    public static class Bind extends Setting<Integer> {
        public Bind(String n, int d) { super(n, d); }
        @Override public Integer get() { return intOr(super.get(), def); }
    }
    public static class Col extends Setting<Integer> {
        public Col(String n, int argb) { super(n, argb); }
        @Override public Integer get() { return intOr(super.get(), def); }
    }
}
