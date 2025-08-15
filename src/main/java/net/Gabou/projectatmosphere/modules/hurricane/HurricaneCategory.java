package net.Gabou.projectatmosphere.modules.hurricane;

/**
 * Saffir–Simpson hurricane wind scale categories with
 * sustained wind speed ranges in miles per hour.
 */
public enum HurricaneCategory {
    ONE(74, 95),
    TWO(96, 110),
    THREE(111, 129),
    FOUR(130, 156),
    FIVE(157, Integer.MAX_VALUE);

    public final int minMph;
    public final int maxMph;

    HurricaneCategory(int minMph, int maxMph) {
        this.minMph = minMph;
        this.maxMph = maxMph;
    }

    public static HurricaneCategory fromId(int id) {
        return switch (id) {
            case 1 -> ONE;
            case 2 -> TWO;
            case 3 -> THREE;
            case 4 -> FOUR;
            case 5 -> FIVE;
            default -> ONE;
        };
    }
}
