package net.Gabou.projectatmosphere.compat;

import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import toughasnails.api.temperature.TemperatureLevel;
import toughasnails.temperature.TemperatureHelperImpl;

public class ToughAsNailsCompat {

    private static final float DAILY_JITTER = 0.5F;
    private static final float DAILY_SWING = 4.0F;

    /**
     * Generate a weekly forecast for TAN using mapped base temperature + fluctuation.
     * Each day contains [min, max] values.
     */
    public static float[][] injectForecastForTAN(BiomeInstanceKey key, ServerLevel level) {
        BlockPos sample = key.samplePos();
        TemperatureLevel band = TemperatureHelperImpl.getTemperatureAtPosWithoutProximity(level, sample);
        float baseTemp = mapBandToTemperature(band);
        return generateMinMaxCurve(baseTemp, level.getRandom());
    }

    private static float[][] generateMinMaxCurve(float base, RandomSource random) {
        float[][] result = new float[7][2];

        for (int i = 0; i < 7; i++) {
            float swing = DAILY_SWING * (float) Math.sin((i / 6.0F) * Math.PI); 
            float min = (float) (base - swing / 2 + random.nextGaussian() * DAILY_JITTER);
            float max = (float) (base + swing / 2 + random.nextGaussian() * DAILY_JITTER);

            result[i][0] = Math.round(min * 10f) / 10f;
            result[i][1] = Math.round(max * 10f) / 10f;
        }

        return result;
    }

    private static float mapBandToTemperature(TemperatureLevel level) {
        return switch (level) {
            case ICY     -> -20.0F;
            case COLD    -> 0.0F;
            case NEUTRAL -> 15.0F;
            case WARM    -> 28.0F;
            case HOT     -> 38.0F;
        };
    }

    public static boolean isLoaded() {
        return CompatHandler.isToughAsNailsLoaded;
    }
}
