package dev.noxclient.module;

public enum Category {
    COMBAT("Combat"), MOVEMENT("Movement"), RENDER("Render"),
    DONUTSMP("DonutSMP"), MISC("Misc");

    public final String label;
    Category(String l) { label = l; }
}
