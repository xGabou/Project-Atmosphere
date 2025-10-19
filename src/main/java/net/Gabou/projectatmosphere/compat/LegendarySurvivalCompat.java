package net.Gabou.projectatmosphere.compat;

import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureUtil;



public class LegendarySurvivalCompat {

    private static final float MIN_DAILY_SWING = 2.5F;  
    private static final float MAX_DAILY_SWING = 6.0F;  
    private static final float DAILY_JITTER = 0.5F;     

    /**
     * Generates a 7-day forecast using current LSO temp as base, with estimated min/max per day.
     * @return float[7][2] => [ [minDay1, maxDay1], [minDay2, maxDay2], ..., [minDay7, maxDay7] ]
     */
    public static float[][] injectForecastForLSO(BiomeInstanceKey key, ServerLevel level) {
        BlockPos sample = key.samplePos();
        float baseTemp = TemperatureUtil.getWorldTemperature(level, sample);
        return generateMinMaxCurve(baseTemp, level.getRandom());
    }

    /**
     * Creates a sinusoidal 7-day swing curve around a base temperature.
     * Each day has a generated min and max temperature.
     */
    private static float[][] generateMinMaxCurve(float base, RandomSource random) {
        float[][] result = new float[7][2];

        float overallSwing = Mth.lerp(random.nextFloat(), MIN_DAILY_SWING, MAX_DAILY_SWING);

        for (int i = 0; i < 7; i++) {
            
            float dayOffset = (float) Math.sin((i / 6.0F) * Math.PI); 

            
            float swing = dayOffset * overallSwing;

            float minTemp = (float) (base - (swing / 2.0F) + random.nextGaussian() * DAILY_JITTER);
            float maxTemp = (float) (base + (swing / 2.0F) + random.nextGaussian() * DAILY_JITTER);

            result[i][0] = Math.round(minTemp * 10f) / 10f;
            result[i][1] = Math.round(maxTemp * 10f) / 10f;
        }

        return result;
    }

    public static float getLiveTemperature(Level level, BlockPos pos) {
        return TemperatureUtil.getWorldTemperature(level, pos);
    }

    public static boolean isLoaded() {
        return CompatHandler.isLegendarySurvivalLoaded();
    }
}
