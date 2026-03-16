package net.Gabou.projectatmosphere.modules.seasonaltrees.core;

public enum LeafState {
    FULL,
    PARTIAL,
    HIBERNATING,
    BARE;

    public boolean isDormant() {
        return this == HIBERNATING || this == BARE;
    }

    public static LeafState fromSerialized(String value) {
        if (value == null || value.isBlank()) {
            return FULL;
        }
        if ("BARE".equals(value)) {
            return HIBERNATING;
        }
        try {
            return LeafState.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return FULL;
        }
    }
}
