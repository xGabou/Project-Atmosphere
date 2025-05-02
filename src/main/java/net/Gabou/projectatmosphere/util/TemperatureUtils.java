package net.Gabou.projectatmosphere.util;

import net.minecraft.world.level.biome.Biome;

public class TemperatureUtils {

    // Serene/vanilla input range: [-0.5, 2.0]
    private static final float IN_MIN = -0.5f;
    private static final float IN_MAX =  2.0f;
    private static final float DEN    = (IN_MAX - IN_MIN);  // 2.5f

    // Overworld sea level (top face of water block) in Java Edition
    private static final float SEA_LEVEL = 63f;

    // Typical environmental lapse rate (~–6.5°C per 1000 m ⇒ –0.0065°C per block)
    private static final float LAPSE_RATE = -0.0065f;

    /**
     * Pure mapping from baseTemp (–0.5→2.0) into per-biome Celsius,S
     * without altitude. Uses BiomeTempConfig.getRange(...) for min/max.
     */
    public static float toCelsius(Biome biome, float baseTemp) {
        // 1) clamp input
        if (baseTemp < IN_MIN) baseTemp = IN_MIN;
        else if (baseTemp > IN_MAX) baseTemp = IN_MAX;

        // 2) get this biome’s min/max Celsius range
        BiomeTempConfig.Range range = BiomeTempConfig.getRange(biome);

        // 3) normalize [0..1]
        float norm = (baseTemp - IN_MIN) / DEN;

        // 4) map to [minC..maxC]
        return range.minC() + norm * (range.maxC() - range.minC());
    }

    /**
     * Maps baseTemp → per-biome Celsius, then applies an altitude offset:
     *   temperature decreases by 0.0065°C per block above SEA_LEVEL,
     *   and increases if below SEA_LEVEL.
     *
     * @param biome    the current biome
     * @param baseTemp the Serene/vanilla temperature (–0.5→2.0)
     * @param y        the world Y coordinate (feet-level)
     * @return Adjusted Celsius at altitude y
     */
    public static float toCelsiusAtY(Biome biome, float baseTemp, float y) {
        float c = toCelsius(biome, baseTemp);
        return c + (y - SEA_LEVEL) * LAPSE_RATE;
    }
}