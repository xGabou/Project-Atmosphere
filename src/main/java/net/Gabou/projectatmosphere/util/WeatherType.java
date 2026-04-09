package net.Gabou.projectatmosphere.util;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents different weather types in Project Atmosphere
 * and provides a mapping from cloudId (ResourceLocation)
 * to the corresponding WeatherType.
 */
public enum WeatherType {
    NONE,
    RAIN,
    THUNDERSTORM;

    private static final Map<ResourceLocation, WeatherType> CLOUD_MAP = new HashMap<>();

    static {
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "balls"), NONE);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "cookie"), THUNDERSTORM);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "cumulus_noise"), NONE);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "custom_cumulonimbus"), THUNDERSTORM);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "dark_wall"), THUNDERSTORM);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "dense_cumulus"), NONE);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "dense_itty_bitty"), NONE);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "dense_stratocumulus"), RAIN);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "dense_tsegrus"), THUNDERSTORM);
//        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "dithering"), NONE);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "floating_farlands"), RAIN);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "heavy_stratus"), RAIN);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "islands"), NONE);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "itty_bitty_bigger"), NONE);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "mammatus_thin"), RAIN);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "matrix"), NONE);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "overcast"), RAIN);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "pathway"), NONE);
        //CLOUD_MAP.put(new ResourceLocation("simpleclouds", "pattern"), NONE);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "real_itty_bitty"), NONE);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "severe_cumulonimbus"), THUNDERSTORM);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "severe_nimbostratus"), THUNDERSTORM);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "smaller_stratocumulus"), NONE);
        //CLOUD_MAP.put(new ResourceLocation("simpleclouds", "snow"), NONE);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "spots"), NONE);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "spotted"), NONE);
//        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "stripe"), NONE);
//        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "stripe_side"), NONE);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "stronger_stratus"), THUNDERSTORM);
        //CLOUD_MAP.put(new ResourceLocation("simpleclouds", "tall_noise"), NONE);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "thicker_stratocumulus"), RAIN);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "tsegrus"), THUNDERSTORM);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "cumulonimbus"), THUNDERSTORM);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "cumulus"), NONE);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "itty_bitty"), NONE);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "nimbostratus"), THUNDERSTORM);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "small_cumulus"), NONE);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "stratocumulus"), NONE);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "stratus"), RAIN);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "cumulus_humilis"), NONE);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "cumulus_congestus"), RAIN);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "altostratus"), RAIN);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "cumulus_mediocris"), RAIN);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "altocumulus"), RAIN);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "stratocumulus_opacus"), NONE);
        CLOUD_MAP.put(new ResourceLocation("simpleclouds", "altostratus_dry"), NONE);
        CLOUD_MAP.put(new ResourceLocation("projectatmosphere", "hurricane"), THUNDERSTORM);
    }

    public static WeatherType getWeatherType(ResourceLocation id) {
        return CLOUD_MAP.getOrDefault(id, NONE);
    }

    public static boolean isRainy1(WeatherType type) {
        return type == RAIN || type == THUNDERSTORM;
    }

    public static boolean isRainy(ResourceLocation id) {
        return isRainy1(getWeatherType(id));
    }
}
