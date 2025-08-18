package net.Gabou.projectatmosphere.wind;

import net.Gabou.projectatmosphere.config.AtmoCommonConfig;

public final class WindConfig {
    private WindConfig() { }

    public static float baseRetargetSec() {
        return AtmoCommonConfig.WIND_BASE_RETARGET_SEC.get().floatValue();
    }

    public static float dirRetargetSec() {
        return AtmoCommonConfig.WIND_DIR_RETARGET_SEC.get().floatValue();
    }

    public static float gustMeanSec() {
        return AtmoCommonConfig.WIND_GUST_MEAN_SEC.get().floatValue();
    }

    public static float gustDecayMps() {
        return AtmoCommonConfig.WIND_GUST_DECAY_MPS.get().floatValue();
    }

    public static float stormGustMult() {
        return AtmoCommonConfig.WIND_STORM_GUST_MULT.get().floatValue();
    }

    public static float pushThresholdMps() {
        return AtmoCommonConfig.WIND_PUSH_THRESHOLD_MPS.get().floatValue();
    }

    public static float playerPushScale() {
        return AtmoCommonConfig.WIND_PLAYER_PUSH_SCALE.get().floatValue();
    }

    public static float entityPushScale() {
        return AtmoCommonConfig.WIND_ENTITY_PUSH_SCALE.get().floatValue();
    }
}

