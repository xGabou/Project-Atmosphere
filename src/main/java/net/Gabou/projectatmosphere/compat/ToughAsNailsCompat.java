package net.Gabou.projectatmosphere.compat;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.async.ThreadingDetector;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import toughasnails.api.temperature.TemperatureLevel;
import toughasnails.temperature.TemperatureHelperImpl;

import java.util.concurrent.CompletableFuture;

public class ToughAsNailsCompat {

    private static final float DAILY_JITTER = 0.5F;
    private static final float DAILY_SWING = 4.0F;

    /**
     * Generate a weekly forecast for TAN using mapped base temperature + fluctuation.
     * Each day contains [min, max] values.
     */
    public static float[][] injectForecastForTAN(BiomeInstanceKey key, ServerLevel level) {
        BlockPos sample = key.samplePos();
        RandomSource rng = RandomSource.create(sample.asLong() ^ level.getSeed());

        // If already on main thread → run inline
        if (ThreadingDetector.isMainThread(level)) {
            TemperatureLevel band = TemperatureHelperImpl.getTemperatureAtPosWithoutProximity(level, sample);
            float baseTemp = mapBandToTemperature(band);
            return generateMinMaxCurve(baseTemp, rng);
        }

        // If called from async → schedule the band lookup on main, block until it returns
        CompletableFuture<Float> future = new CompletableFuture<>();
        AsyncAtmosphereService.runOnMainThread(() -> {
            try {
                TemperatureLevel band = TemperatureHelperImpl.getTemperatureAtPosWithoutProximity(level, sample);
                float baseTemp = mapBandToTemperature(band);
                future.complete(baseTemp);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        try {
            float baseTemp = future.join();
            return generateMinMaxCurve(baseTemp, rng);
        } catch (Exception e) {
            ProjectAtmosphere.LOGGER.error("[TAN Compat] Failed to generate forecast", e);
            return new float[7][2]; // fallback safe array
        }
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
        return CompatHandler.isToughAsNailsLoaded();
    }

    public static float getLiveTemperatureTAN(Level level, BlockPos pos) {
        TemperatureLevel band = TemperatureHelperImpl.getTemperatureAtPosWithoutProximity(level, pos);
        return mapBandToTemperature(band);
    }
}
