package net.Gabou.projectatmosphere.clouds.cell;

/**
 * Lifecycle phase of one cloud cell. Phases only describe where the cell is in
 * its life; they never drive shape directly. Shape is driven by the continuous
 * cell properties (radius, vertical extent, density, energy).
 */
public enum CloudCellLifecyclePhase {
    FORMING,
    MATURE,
    DISSIPATING;

    public static CloudCellLifecyclePhase byOrdinal(int ordinal) {
        CloudCellLifecyclePhase[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return MATURE;
        }
        return values[ordinal];
    }
}
