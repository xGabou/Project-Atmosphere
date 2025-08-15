package net.Gabou.projectatmosphere.tornado;

public final class TornadoConfig {
    private TornadoConfig() {}

    public static float CHECK_INTERVAL_SEC = 60f;
    public static float BASE_SPAWN_RADIUS_M = 64f;

    // Instability
    public static float MIN_TEMP_CONTRAST_C = 6f;
    public static float HUMIDITY_MIN_PERCENT = 65f;

    // Pressure
    public static float PRESSURE_GRADIENT_GAIN = 10f;
    public static float PRESSURE_GRADIENT_CAP = 3f;

    // Shear
    public static float SHEAR_MIN_SPEED_DIFF_MPS = 5f;
    public static float SHEAR_MIN_DIR_DIFF_DEG = 45f;

    // Storm gating
    public static float STORM_MULTIPLIER = 1.5f;

    // Risk thresholds
    public static float RISK_MIN_TO_CONSIDER = 4.0f;
    public static float BASE_TRIGGER_CHANCE = 0.05f;

    // Aloft proxy
    public static float LAPSE_RATE_C_PER_100M = 0.65f;
    public static float ALOFT_DELTA_H_M = 1500f;

    // Intensity scaling
    public static float INTENSITY_MIN = 0.4f;
    public static float INTENSITY_MAX = 1.0f;

    // Cooldown
    public static int CELL_COOLDOWN_MINUTES = 20;
}

