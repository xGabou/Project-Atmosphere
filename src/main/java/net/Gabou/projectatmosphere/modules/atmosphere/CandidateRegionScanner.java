package net.Gabou.projectatmosphere.modules.atmosphere;

import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class CandidateRegionScanner {
    private CandidateRegionScanner() {
    }

    static ScanResult scan(ServerLevel level) {
        if (level == null) {
            return ScanResult.empty(scanRadiusRegions(), maxRegionsPerTick());
        }

        int radius = scanRadiusRegions();
        int maxRegions = maxRegionsPerTick();
        Map<RegionInstanceKey, ForecastRegion> forecasts = ForecastGenerator.getRegionForecasts();
        List<ServerPlayer> players = level.players();
        Set<RegionInstanceKey> seen = new HashSet<>();
        List<CandidateRegion> regions = new ArrayList<>(Math.min(maxRegions, 512));
        int skipped = 0;
        int duplicates = 0;
        int loaded = 0;
        int forecastOnly = 0;

        for (ServerPlayer player : players) {
            if (player == null) {
                continue;
            }
            RegionInstanceKey center = RegionInstanceKey.from(player.blockPosition());
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    RegionInstanceKey key = center.neighbor(dx, dz);
                    if (!seen.add(key)) {
                        duplicates++;
                        continue;
                    }

                    ForecastRegion forecast = forecasts.get(key);
                    if (forecast == null) {
                        skipped++;
                        continue;
                    }

                    if (regions.size() >= maxRegions) {
                        skipped++;
                        continue;
                    }

                    RegionAtmosphereState state = AtmosphericStateRegistry.getState(key);
                    boolean isForecastOnly = state == null;
                    if (isForecastOnly) {
                        state = createForecastOnlyState(key, forecast, level.getGameTime());
                        forecastOnly++;
                    } else {
                        loaded++;
                    }
                    regions.add(new CandidateRegion(key, state, isForecastOnly));
                }
            }
        }

        return new ScanResult(
                List.copyOf(regions),
                radius,
                maxRegions,
                players.size(),
                regions.size(),
                loaded,
                forecastOnly,
                skipped,
                duplicates
        );
    }

    static int scanRadiusRegions() {
        try {
            return AtmoCommonConfig.CYCLONE_CANDIDATE_SCAN_RADIUS_REGIONS.get();
        } catch (IllegalStateException exception) {
            return 10;
        }
    }

    static int scanIntervalTicks() {
        try {
            return AtmoCommonConfig.CYCLONE_CANDIDATE_SCAN_INTERVAL_TICKS.get();
        } catch (IllegalStateException exception) {
            return 600;
        }
    }

    static int maxRegionsPerTick() {
        try {
            return AtmoCommonConfig.CYCLONE_CANDIDATE_MAX_REGIONS_PER_TICK.get();
        } catch (IllegalStateException exception) {
            return 512;
        }
    }

    private static RegionAtmosphereState createForecastOnlyState(RegionInstanceKey key, ForecastRegion forecast, long gameTime) {
        forecast.finalizeAggregation();
        RegionAtmosphereState state = RegionAtmosphereState.fromForecast(key, forecast);
        Vec3 localCenter = new Vec3(key.regionSize() * 0.5D, 0.0D, key.regionSize() * 0.5D);
        float temperature = forecast.sampleTemperature(localCenter, gameTime);
        float humidity = normalizeHumidity(forecast.sampleHumidity(localCenter, gameTime));
        float pressure = forecast.samplePressure(gameTime);
        WindVector wind = forecast.sampleWind(gameTime);
        float storm = Mth.clamp(forecast.sampleStorm(gameTime), 0.0F, 1.0F);

        if (Float.isFinite(temperature) && temperature != 0.0F) {
            state.setTemperature(temperature);
        }
        if (Float.isFinite(humidity) && humidity > 0.0F) {
            state.setHumidity(humidity);
        }
        if (Float.isFinite(pressure) && pressure > 0.0F) {
            state.setPressure(pressure);
        }
        if (wind != null) {
            state.setWind(wind);
        }

        float lowPressureSupport = Mth.clamp((1012.0F - state.getPressure()) / 30.0F, 0.0F, 1.0F);
        float humidSupport = Mth.clamp((state.getHumidity() - 0.58F) / 0.34F, 0.0F, 1.0F);
        state.setCloudCover(Mth.clamp(storm * 0.55F + humidSupport * 0.28F + lowPressureSupport * 0.17F, 0.0F, 1.0F));
        state.setCloudWater(Mth.clamp(storm * 0.32F + humidSupport * lowPressureSupport * 0.34F, 0.0F, 1.0F));
        state.setRainIntensity(Mth.clamp(storm * 0.45F + Math.max(0.0F, state.getCloudWater() - 0.28F) * 0.35F, 0.0F, 1.0F));
        return state;
    }

    private static float normalizeHumidity(float humidity) {
        if (!Float.isFinite(humidity)) {
            return 0.0F;
        }
        return humidity > 1.5F ? humidity / 100.0F : humidity;
    }

    record CandidateRegion(RegionInstanceKey key, RegionAtmosphereState state, boolean forecastOnly) {
    }

    record ScanResult(
            List<CandidateRegion> regions,
            int scanRadiusRegions,
            int maxRegionsPerTick,
            int activePlayersIncluded,
            int checkedRegions,
            int loadedRegions,
            int forecastOnlyRegions,
            int skippedRegions,
            int duplicateRegionsSkipped
    ) {
        static ScanResult empty(int radius, int maxRegions) {
            return new ScanResult(List.of(), radius, maxRegions, 0, 0, 0, 0, 0, 0);
        }
    }
}
