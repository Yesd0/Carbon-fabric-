package dev.carbon.client.core.module;

public enum Category {
    HUD("HUD"),
    VISUAL("Visual"),
    UTILITY("Utility"),
    PERFORMANCE("Performance");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
