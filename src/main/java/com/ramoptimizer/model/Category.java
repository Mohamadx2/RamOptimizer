package com.ramoptimizer.model;

/**
 * Process categories used for grouping and applying soft memory limits.
 */
public enum Category {
    SYSTEM("System", "#c0392b"),
    BROWSER("Browser", "#2980b9"),
    GAMES("Games", "#8e44ad"),
    UTILITY("Utility", "#27ae60"),
    UNKNOWN("Unknown", "#7f8c8d");

    private final String displayName;
    private final String colorHex;

    Category(String displayName, String colorHex) {
        this.displayName = displayName;
        this.colorHex = colorHex;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorHex() {
        return colorHex;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
