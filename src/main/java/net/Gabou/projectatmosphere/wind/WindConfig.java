package net.Gabou.projectatmosphere.wind;

public final class WindConfig {
    private WindConfig() { }

    public static float BASE_RETARGET_SEC = 60f;
    public static float DIR_RETARGET_SEC = 90f;
    public static float GUST_MEAN_SEC = 15f;
    public static float GUST_DECAY_MPS = 1.0f;
    public static float STORM_GUST_MULT = 2.0f;
    public static float PUSH_THRESHOLD_MPS = 6.0f;
    public static float PLAYER_PUSH_SCALE = 0.04f;
    public static float ENTITY_PUSH_SCALE = 0.03f;
}

