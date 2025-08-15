package net.Gabou.projectatmosphere.modules.hurricane;

/**
 * Saffir–Simpson hurricane wind scale categories with
 * sustained wind speed ranges in km per hour.
 */
public enum HurricaneCategory {
    ONE(119, 153),
    TWO(154, 177),
    THREE(178, 208),
    FOUR(209, 251),
    FIVE(252, Integer.MAX_VALUE);

    public final int minKmh;
    public final int maxKmh;

    HurricaneCategory(int minKmh, int maxKmh) {
        this.minKmh = minKmh;
        this.maxKmh = maxKmh;
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
