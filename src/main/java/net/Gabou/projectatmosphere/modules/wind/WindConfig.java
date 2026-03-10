package net.Gabou.projectatmosphere.modules.wind;

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

    public static float pushRampMps() {
        return AtmoCommonConfig.WIND_PUSH_RAMP_MPS.get().floatValue();
    }

    public static float playerPushScale() {
        return AtmoCommonConfig.WIND_PLAYER_PUSH_SCALE.get().floatValue();
    }

    public static float entityPushScale() {
        return AtmoCommonConfig.WIND_ENTITY_PUSH_SCALE.get().floatValue();
    }

    public static float particleBendStrength() {
        return AtmoCommonConfig.WIND_PARTICLE_BEND_STRENGTH.get().floatValue();
    }

    public static float playerWindThresholdMps() {
        return AtmoCommonConfig.PLAYER_WIND_THRESHOLD_MPS.get().floatValue();
    }

    public static float playerMaxGustBpt() {
        return AtmoCommonConfig.PLAYER_MAX_GUST_BPT.get().floatValue();
    }

    public static float playerGustChanceScale() {
        return AtmoCommonConfig.PLAYER_GUST_CHANCE_SCALE.get().floatValue();
    }

    public static float playerGustChanceDivider() {
        return AtmoCommonConfig.PLAYER_GUST_CHANCE_DIVIDER.get().floatValue();
    }

    public static float playerGustStrengthScale() {
        return AtmoCommonConfig.PLAYER_GUST_STRENGTH_SCALE.get().floatValue();
    }

    public static int playerGustDurationMin() {
        return AtmoCommonConfig.PLAYER_GUST_DURATION_MIN.get();
    }

    public static int playerGustDurationMax() {
        return AtmoCommonConfig.PLAYER_GUST_DURATION_MAX.get();
    }

    public static float playerGustAngleVarianceDeg() {
        return AtmoCommonConfig.PLAYER_GUST_ANGLE_VARIANCE_DEG.get().floatValue();
    }

    public static float playerGustExtremeThresholdMps() {
        return AtmoCommonConfig.PLAYER_GUST_EXTREME_THRESHOLD_MPS.get().floatValue();
    }

    public static float playerGustExtremeChanceMult() {
        return AtmoCommonConfig.PLAYER_GUST_EXTREME_CHANCE_MULT.get().floatValue();
    }

    public static float playerGustExtremeStrengthMult() {
        return AtmoCommonConfig.PLAYER_GUST_EXTREME_STRENGTH_MULT.get().floatValue();
    }
}

