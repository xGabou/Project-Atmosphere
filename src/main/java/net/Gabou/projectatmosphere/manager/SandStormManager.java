//TODO remove biomeInstanceKeys
package net.Gabou.projectatmosphere.manager;

import com.BreadRes.desertstormwarming.logic.SandstormPhase;
import com.BreadRes.desertstormwarming.sounds.SandstormSounds;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.event.BiomeChangeManager;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.Gabou.projectatmosphere.modules.sandStorm.SandStormAPI;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static net.Gabou.projectatmosphere.manager.ForecastGenerator.*;


public class SandStormManager {

    private static final float SANDSTORM_WIND_THRESHOLD_BASE = 10f;
    private static final float SANDSTORM_WIND_THRESHOLD_MIN = 6f;

    private static final float SANDSTORM_HUMIDITY_THRESHOLD_BASE = 20f;
    private static final float SANDSTORM_HUMIDITY_THRESHOLD_MAX = 35f;

    private static final float SANDSTORM_PRESSURE_THRESHOLD_BASE = 1005f;
    private static final float SANDSTORM_PRESSURE_THRESHOLD_MAX = 1015f;
    static RegionInstanceKey scheduledStormRegion = null;
    static BiomeInstanceKey scheduledStormBiome = null;
    static SandstormPhase scheduledStormPhase = null;
    static long scheduledStormTime = -1L;

    public static final Set<ResourceLocation> SANDSTORM_BIOMES = Set.of(
            ResourceLocation.fromNamespaceAndPath("minecraft", "desert"),
            ResourceLocation.fromNamespaceAndPath("minecraft", "badlands")
    );
    private static final Set<RegionInstanceKey> SANDSTORM_REGIONS = ConcurrentHashMap.newKeySet();
    private static final Set<BiomeInstanceKey> SANDSTORM_FORECASTS = ConcurrentHashMap.newKeySet();

    public static Set<BiomeInstanceKey> getSandstormForecasts() {
        return Collections.unmodifiableSet(SANDSTORM_FORECASTS);
    }

    static void clearSandstormForecasts() {
        SANDSTORM_REGIONS.clear();
        SANDSTORM_FORECASTS.clear();
        scheduledStormRegion = null;
        scheduledStormBiome = null;
        scheduledStormPhase = null;
        scheduledStormTime = -1L;
        tickCounter = 0;
    }

    static void dailyAndSand(ServerLevel level) {
        clearSandstormForecasts();

        REGION_FORECASTS.forEach((regionKey, region) -> {
            if (!supportsSandstorm(region)) {
                return;
            }
            if (!hasSandstormCurves(region)) {
                return;
            }
            WindVector forecastWind = ForecastOrchestrator.getForecastWind(regionKey, level.getDayTime());
            if (!shouldTriggerSandstorm(region, forecastWind)) {
                return;
            }
            SANDSTORM_REGIONS.add(regionKey);
            BiomeInstanceKey representative = resolveSandstormBiome(region);
            if (representative != null) {
                SANDSTORM_FORECASTS.add(representative);
                BiomeForecast forecast = FORECAST_MAP.get(representative);
                if (forecast != null) {
                    forecast.setSandstormExpected(true);
                }
            }
        });


        REGION_FORECASTS.forEach(AtmosphericStateRegistry::initializeState);

        computeAverageForecastsByBiomeType();

        if (!sandStormLoaded) return;
        if (!SandStormAPI.isSandstormActive() && scheduledStormRegion == null && !SANDSTORM_REGIONS.isEmpty()) {
            RegionInstanceKey selected = SANDSTORM_REGIONS.stream()
                    .skip(level.random.nextInt(SANDSTORM_REGIONS.size()))
                    .findFirst()
                    .orElse(null);

            if (selected != null) {
                ForecastRegion region = REGION_FORECASTS.get(selected);
                BiomeInstanceKey representative = region == null ? null : resolveSandstormBiome(region);
                if (region != null && representative != null) {
                    long baseTime = (level.getDayTime() / 24000L) * 24000L;
                    long randomOffset = 1000 + level.random.nextInt(9000);

                    scheduledStormRegion = selected;
                    scheduledStormBiome = representative;
                    scheduledStormPhase = computeStormPhase(region, ForecastOrchestrator.getForecastWind(selected, level.getDayTime()));
                    scheduledStormTime = baseTime + randomOffset;

                    if(ProjectAtmosphere.DEBUG_MODE)
                        ProjectAtmosphere.LOGGER.info("[Atmosphere] Scheduled sandstorm at tick {} in region {} via biome {} (phase: {})",
                            scheduledStormTime, selected, representative.biomeType(), scheduledStormPhase);
                    for (ServerPlayer player : level.players()) {
                        boolean lastBiomeFlag = BiomeChangeManager
                                .getLastBiome()
                                .getOrDefault(player.getUUID(), Pair.of(null, false))
                                .getValue();

                        if (!lastBiomeFlag) {
                            for (SoundEvent soundEvent : SandstormSounds.getSoundsForPhase(SandStormAPI.getSandstormPhase())) {
                                Minecraft.getInstance().getSoundManager().stop(soundEvent.getLocation(), null);
                            }
                        }
                    }
                }
            }
        }
    }

    private static int tickCounter = 0;

    static void tickSandstormScheduler(ServerLevel level) {

        if (scheduledStormRegion != null && scheduledStormBiome != null && level.getDayTime() >= scheduledStormTime) {
            SandStormAPI. startSandstorm(scheduledStormPhase, scheduledStormBiome);

            if(ProjectAtmosphere.DEBUG_MODE)
                ProjectAtmosphere.LOGGER.info("[Atmosphere] Triggered sandstorm in region {} via biome {} with phase {}",
                    scheduledStormRegion, scheduledStormBiome.biomeType(), scheduledStormPhase);

            scheduledStormRegion = null;
            scheduledStormBiome = null;
            scheduledStormTime = -1L;
            scheduledStormPhase = null;
        }
        if (SandStormAPI.isSandstormActive() && tickCounter % 50 == 0) {
            var sandStorms = SandStormAPI.getScheduledStormBiome();
            if (sandStorms.isEmpty()) {
                ProjectAtmosphere.LOGGER.warn("[Atmosphere] No sandstorm biomes found, but storm is active!");
                return;
            }
            if(ProjectAtmosphere.DEBUG_MODE)
                ProjectAtmosphere.LOGGER.info("[Atmosphere] Sandstorm active in {} biomes: {}", sandStorms.size(), sandStorms);
            AsyncAtmosphereService.runStorm(() -> {
                for (BiomeInstanceKey biome : sandStorms) {

                    SandStormAPI.blowSandInBiome(level,
                            biome,
                            ForecastOrchestrator.getWind(biome, level.getDayTime()));

                }
            });
            tickCounter = 0;

        }
        tickCounter++;
    }
    static boolean shouldTriggerSandstorm(ForecastRegion region, WindVector wind) {
        if (!supportsSandstorm(region)) return false;

        float[][] humidity = region.getHumidity();
        float[][] pressure = region.getPressure();

        if (humidity == null || humidity.length == 0 || humidity[0].length == 0) return false;
        if (pressure == null || pressure.length == 0 || pressure[0].length == 0) return false;
        if (wind == null) return false;

        float todayHumidityMin = humidity[0][0];
        float todayPressureMin = pressure[0][0];
        float windSpeed = wind.gustSpeed();

        float dryness = Math.max(0f, 1f - (todayHumidityMin / SANDSTORM_HUMIDITY_THRESHOLD_MAX));
        float gustiness = Math.max(0f, (windSpeed - SANDSTORM_WIND_THRESHOLD_MIN) /
                Math.max(1f, SANDSTORM_WIND_THRESHOLD_BASE - SANDSTORM_WIND_THRESHOLD_MIN));
        float pressureDrop = Math.max(0f, (SANDSTORM_PRESSURE_THRESHOLD_BASE - todayPressureMin) / 20f);

        float severity = (dryness * 0.5f) + (gustiness * 0.3f) + (pressureDrop * 0.2f);

        boolean dryEnough = todayHumidityMin < SANDSTORM_HUMIDITY_THRESHOLD_BASE || dryness > 0.6f;
        boolean windyEnough = windSpeed > SANDSTORM_WIND_THRESHOLD_BASE * 0.85f;
        boolean unstablePressure = todayPressureMin < SANDSTORM_PRESSURE_THRESHOLD_BASE + 2f;

        return dryEnough && windyEnough && unstablePressure && severity > 0.55f;
    }

    static SandstormPhase computeStormPhase(ForecastRegion region, WindVector windVector) {
        float wind = windVector.baseSpeed();
        float pressure = region.getPressure()[0][0];
        float humidity = region.getHumidity()[0][0];

        if (wind > 35 && pressure < 980 && humidity < 0.15f) return SandstormPhase.PHASE_5;
        if (wind > 30) return SandstormPhase.PHASE_4;
        if (wind > 25) return SandstormPhase.PHASE_3;
        if (wind > 20) return SandstormPhase.PHASE_2;
        return SandstormPhase.PHASE_1;
    }

    private static boolean supportsSandstorm(ForecastRegion region) {
        if (region == null) {
            return false;
        }
        for (ResourceLocation biome : region.getBiomeWeights().keySet()) {
            if (SANDSTORM_BIOMES.contains(biome)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSandstormCurves(ForecastRegion region) {
        return region.getHumidity() != null && region.getHumidity().length > 0
                && region.getPressure() != null && region.getPressure().length > 0
                && region.getWind() != null && region.getWind().length > 0;
    }

    private static BiomeInstanceKey resolveSandstormBiome(ForecastRegion region) {
        if (region == null) {
            return null;
        }
        List<BiomeInstanceKey> samples = region.getSamples();
        for (BiomeInstanceKey sample : samples) {
            if (sample != null && SANDSTORM_BIOMES.contains(sample.biomeType())) {
                return sample;
            }
        }
        ResourceLocation dominant = region.getBiomeWeights().keySet().stream()
                .filter(SANDSTORM_BIOMES::contains)
                .findFirst()
                .orElse(null);
        if (dominant == null) {
            return null;
        }
        BlockPos anchor = region.getAnchor() == null ? region.getKey().center() : region.getAnchor();
        return new BiomeInstanceKey(dominant, anchor);
    }
}
