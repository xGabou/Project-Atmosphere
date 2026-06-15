package net.Gabou.projectatmosphere.modules.atmosphere;

import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Shared server-side atmospheric scoring used by cloud birth and WeatherCells.
 * It keeps field normalization in one place so future weather systems do not
 * grow separate interpretations of the same live atmosphere values.
 */
public final class AtmosphericSupportEvaluator {
    public static final float WEATHER_RAIN_THRESHOLD = 0.18F;
    public static final float WEATHER_THUNDER_THRESHOLD = 0.42F;
    public static final float WEATHER_SEVERE_THRESHOLD = 0.70F;
    public static final float WEATHER_THUNDER_WEAKEN_THRESHOLD = (WEATHER_RAIN_THRESHOLD + WEATHER_THUNDER_THRESHOLD) * 0.5F;
    public static final float WEATHER_SUPERCELL_WEAKEN_THRESHOLD = (WEATHER_THUNDER_THRESHOLD + WEATHER_SEVERE_THRESHOLD) * 0.5F;
    public static final float RAIN_CELL_FORMATION_THRESHOLD = 0.58F;

    private AtmosphericSupportEvaluator() {
    }

    public static Support evaluate(RegionInstanceKey key, RegionAtmosphereState state) {
        if (key == null || state == null) {
            return Support.EMPTY;
        }

        float humidity = Mth.clamp(state.getHumidity(), 0.0F, 1.2F);
        float cloudWater = Mth.clamp(state.getCloudWater(), 0.0F, 1.2F);
        float pressure = state.getPressure();
        float temperature = state.getTemperature();
        float cloudCover = Mth.clamp(state.getCloudCover(), 0.0F, 1.0F);
        float rainIntensity = Mth.clamp(state.getRainIntensity(), 0.0F, 1.0F);
        float windStrength = Math.max(0.0F, state.getWindStrength());
        float gustStrength = state.getWind() == null ? windStrength : Math.max(windStrength, state.getWind().gustSpeed());
        float convergence = estimateWindConvergence(key, state);
        float humidityTransport = WindVector.estimateHumidityTransport(key);

        float cloudBirthHumiditySupport = ramp(humidity, 0.50F, 0.84F);
        float stormHumiditySupport = ramp(humidity, 0.64F, 1.00F);
        float rainFormationHumiditySupport = ramp(humidity, 0.68F, 1.00F);
        float cloudBirthWaterSupport = ramp(cloudWater, 0.04F, 0.34F);
        float rainFormationWaterSupport = ramp(cloudWater, 0.10F, 0.38F);
        float stormWaterSupport = ramp(cloudWater, 0.06F, 0.34F);
        float cloudBirthPressureSupport = Mth.clamp((1018.0F - pressure) / 30.0F, 0.0F, 1.0F);
        float rainFormationPressureSupport = Mth.clamp((1017.0F - pressure) / 28.0F, 0.0F, 1.0F);
        float stormPressureSupport = Mth.clamp((1018.0F - pressure) / 30.0F, 0.0F, 1.0F);
        float lowPressureSupport = Mth.clamp((1013.25F - pressure) / 45.0F, 0.0F, 1.0F);
        float temperatureCloudSupport = Mth.clamp((temperature + 8.0F) / 30.0F, 0.0F, 1.0F);
        float windTransportSupport = Mth.clamp(Math.max(0.0F, humidityTransport) / 0.035F, 0.0F, 1.0F);
        float rainFormationRainSupport = ramp(rainIntensity, 0.04F, 0.49F);
        float rainSupport = Mth.clamp(rainIntensity / 0.55F, 0.0F, 1.0F);
        float cloudCoverSupport = ramp(cloudCover, 0.35F, 0.85F);
        float weatherCloudSupport = Mth.clamp(cloudCover, 0.0F, 1.0F);
        float windStrengthSupport = Mth.clamp(windStrength / 18.0F, 0.0F, 1.0F);
        float gustSupport = Mth.clamp((gustStrength - 20.0F) / 40.0F, 0.0F, 1.0F);
        float highPressurePenalty = Mth.clamp((pressure - 1022.0F) / 24.0F, 0.0F, 1.0F);

        float rainCellSustain = Mth.clamp(
                stormHumiditySupport * 0.34F
                        + stormWaterSupport * 0.32F
                        + stormPressureSupport * 0.18F
                        + convergence * 0.08F
                        + rainSupport * 0.08F,
                0.0F,
                1.0F
        );

        float thunderstormSupport = Mth.clamp(
                stormHumiditySupport * 0.24F
                        + stormWaterSupport * 0.24F
                        + rainSupport * 0.14F
                        + weatherCloudSupport * 0.10F
                        + lowPressureSupport * 0.12F
                        + convergence * 0.10F
                        + windStrengthSupport * 0.06F,
                0.0F,
                1.0F
        );

        float supercellSupport = Mth.clamp(
                thunderstormSupport * 0.58F
                        + stormPressureSupport * 0.12F
                        + convergence * 0.12F
                        + gustSupport * 0.10F
                        + windStrengthSupport * 0.08F,
                0.0F,
                1.0F
        );

        return new Support(
                key,
                state,
                humidity,
                cloudWater,
                pressure,
                temperature,
                cloudCover,
                rainIntensity,
                windStrength,
                gustStrength,
                humidityTransport,
                convergence,
                cloudBirthHumiditySupport,
                stormHumiditySupport,
                rainFormationHumiditySupport,
                cloudBirthWaterSupport,
                rainFormationWaterSupport,
                stormWaterSupport,
                cloudBirthPressureSupport,
                rainFormationPressureSupport,
                stormPressureSupport,
                lowPressureSupport,
                temperatureCloudSupport,
                windTransportSupport,
                rainFormationRainSupport,
                rainSupport,
                cloudCoverSupport,
                weatherCloudSupport,
                windStrengthSupport,
                gustSupport,
                highPressurePenalty,
                rainCellSustain,
                thunderstormSupport,
                supercellSupport
        );
    }

    public static float estimateWindConvergence(RegionInstanceKey key, RegionAtmosphereState state) {
        if (key == null || state == null || state.getPosition() == null) {
            return 0.0F;
        }

        BlockPos center = state.getPosition();
        float incoming = 0.0F;
        int samples = 0;
        for (RegionInstanceKey neighborKey : AtmosphericStateRegistry.getNeighbors(key)) {
            RegionAtmosphereState neighbor = AtmosphericStateRegistry.getState(neighborKey);
            if (neighbor == null || neighbor.getWind() == null || neighbor.getPosition() == null) {
                continue;
            }
            Vec3 towardCenter = new Vec3(
                    center.getX() - neighbor.getPosition().getX(),
                    0.0D,
                    center.getZ() - neighbor.getPosition().getZ()
            );
            double length = towardCenter.length();
            if (length <= 0.0001D) {
                continue;
            }
            towardCenter = towardCenter.scale(1.0D / length);
            WindVector wind = neighbor.getWind();
            Vec3 windVector = new Vec3(Math.sin(wind.angleRadians()), 0.0D, Math.cos(wind.angleRadians()))
                    .scale(Math.max(0.0F, wind.baseSpeed()));
            incoming += Math.max(0.0F, (float) windVector.dot(towardCenter));
            samples++;
        }
        if (samples == 0) {
            return 0.0F;
        }
        return Mth.clamp(incoming / (samples * 8.0F), 0.0F, 1.0F);
    }

    private static float ramp(float value, float startsAt, float fullAt) {
        if (fullAt <= startsAt) {
            return value >= fullAt ? 1.0F : 0.0F;
        }
        return Mth.clamp((value - startsAt) / (fullAt - startsAt), 0.0F, 1.0F);
    }

    public record Support(
            RegionInstanceKey key,
            RegionAtmosphereState state,
            float humidity,
            float cloudWater,
            float pressure,
            float temperature,
            float cloudCover,
            float rainIntensity,
            float windStrength,
            float gustStrength,
            float humidityTransport,
            float windConvergence,
            float cloudBirthHumiditySupport,
            float stormHumiditySupport,
            float rainFormationHumiditySupport,
            float cloudBirthWaterSupport,
            float rainFormationWaterSupport,
            float stormWaterSupport,
            float cloudBirthPressureSupport,
            float rainFormationPressureSupport,
            float stormPressureSupport,
            float lowPressureSupport,
            float temperatureCloudSupport,
            float windTransportSupport,
            float rainFormationRainSupport,
            float rainSupport,
            float cloudCoverSupport,
            float weatherCloudSupport,
            float windStrengthSupport,
            float gustSupport,
            float highPressurePenalty,
            float rainCellSustain,
            float thunderstormSupport,
            float supercellSupport
    ) {
        private static final Support EMPTY = new Support(
                null,
                null,
                0.0F,
                0.0F,
                1013.25F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F
        );

        public boolean hasState() {
            return state != null && key != null;
        }

        public float cloudBirthBaseScore() {
            return Mth.clamp(
                    cloudBirthHumiditySupport * 0.28F
                            + cloudBirthWaterSupport * 0.24F
                            + cloudBirthPressureSupport * 0.14F
                            + temperatureCloudSupport * 0.08F
                            + windConvergence * 0.10F
                            + windTransportSupport * 0.08F
                            - highPressurePenalty * 0.18F,
                    0.0F,
                    1.0F
            );
        }

        public float cloudBirthScore(float existingCoverage) {
            return Mth.clamp(cloudBirthBaseScore() - Mth.clamp(existingCoverage, 0.0F, 1.2F) * 0.34F, -1.0F, 1.0F);
        }

        public float rainCellFormationScore(float coverage) {
            float saturationPenalty = Mth.clamp((coverage - 0.45F) / 0.35F, 0.0F, 1.0F);
            float instability = rainFormationHumiditySupport * 0.28F
                    + rainFormationWaterSupport * 0.28F
                    + rainFormationPressureSupport * 0.18F
                    + windConvergence * 0.14F
                    + cloudCoverSupport * 0.08F
                    + rainFormationRainSupport * 0.04F;
            return Mth.clamp(instability - saturationPenalty * 0.45F - highPressurePenalty * 0.20F, -1.0F, 1.0F);
        }
    }
}
