package net.Gabou.projectatmosphere.modules.storm;

/**
 * Multiplies a base stormChance by a monotonic boost that grows with the number
 * of days since the last severe event (severity >= threshold), then clamps.
 */
public final class StormChanceAdjuster {
    private StormChanceAdjuster() {}

    /** Tunables */
    public static final int    SEVERITY_THRESHOLD   = 5;
    public static final float  PER_DAY_MULTIPLIER   = 0.08f;  // +8% per day without severe storms
    public static final float  MAX_BOOST_MULTIPLIER = 2.5f;   // cap at 2.5x
    public static final float  MIN_CHANCE_FLOOR     = 0.0f;   // keep if you want a floor
    public static final float  MAX_CHANCE_CEIL      = 1.0f;   // stormChance is expected in [0..1]

    /**
     * @param baseChance [0..1] raw chance before lull boost
     * @param daysSinceLastSevere days since severity >= threshold, or 0 if happened today
     * @return adjusted chance [0..1]
     */
    public static float adjust(float baseChance, int daysSinceLastSevere) {
        // multiplicative ramp with cap
        float boost = 1.0f + PER_DAY_MULTIPLIER * Math.max(0, daysSinceLastSevere);
        boost = Math.min(boost, MAX_BOOST_MULTIPLIER);
        float out = baseChance * boost;
        if (MIN_CHANCE_FLOOR > 0f) out = Math.max(out, MIN_CHANCE_FLOOR);
        return Math.min(out, MAX_CHANCE_CEIL);
    }
}
