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
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "balls"), NONE);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "cookie"), THUNDERSTORM);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "cumulus_noise"), NONE);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "custom_cumulonimbus"), THUNDERSTORM);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "dark_wall"), THUNDERSTORM);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "dense_cumulus"), NONE);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "dense_itty_bitty"), NONE);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "dense_stratocumulus"), RAIN);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "dense_tsegrus"), THUNDERSTORM);
//        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "dithering"), NONE);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "floating_farlands"), RAIN);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "heavy_stratus"), RAIN);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "islands"), NONE);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "itty_bitty_bigger"), NONE);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "mammatus_thin"), RAIN);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "matrix"), NONE);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "overcast"), RAIN);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "pathway"), NONE);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "pattern"), NONE);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "real_itty_bitty"), NONE);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "severe_cumulonimbus"), THUNDERSTORM);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "severe_nimbostratus"), THUNDERSTORM);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "smaller_stratocumulus"), NONE);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "snow"), NONE);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "spots"), NONE);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "spotted"), NONE);
//        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "stripe"), NONE);
//        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "stripe_side"), NONE);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "stronger_stratus"), THUNDERSTORM);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "tall_noise"), NONE);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "tall_weirdness"), NONE);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "thicker_stratocumulus"), RAIN);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "tsegrus"), THUNDERSTORM);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "cumulonimbus"), THUNDERSTORM);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "cumulus"), NONE);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "itty_bitty"), NONE);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "nimbostratus"), THUNDERSTORM);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "small_cumulus"), NONE);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "stratocumulus"), NONE);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "stratus"), RAIN);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "cumulus_humilis"), NONE);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "cumulus_congestus"), RAIN);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "altostratus"), RAIN);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "cumulus_mediocris"), RAIN);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "altocumulus"), RAIN);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "stratocumulus_opacus"), NONE);
        CLOUD_MAP.put(ResourceLocation.fromNamespaceAndPath("simpleclouds", "altostratus_dry"), NONE);


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
