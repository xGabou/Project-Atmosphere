package net.Gabou.projectatmosphere.manager;

import com.BreadRes.desertstormwarming.logic.SandstormPhase;
import com.BreadRes.desertstormwarming.sounds.SandstormSounds;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.event.BiomeChangeManager;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.sandStorm.SandStormAPI;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collections;
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
    static BiomeInstanceKey scheduledStormBiome = null;
    static SandstormPhase scheduledStormPhase = null;
    static long scheduledStormTime = -1L;

    public static BiomeInstanceKey getScheduledSandstormBiome() {
        return scheduledStormBiome;
    }

    public static final Set<ResourceLocation> SANDSTORM_BIOMES = Set.of(
            ResourceLocation.fromNamespaceAndPath("minecraft", "desert"),
            ResourceLocation.fromNamespaceAndPath("minecraft", "badlands")
    );
    private static final Set<BiomeInstanceKey> SANDSTORM_FORECASTS = ConcurrentHashMap.newKeySet();

    public static Set<BiomeInstanceKey> getSandstormForecasts() {
        return Collections.unmodifiableSet(SANDSTORM_FORECASTS);
    }

    static void clearSandstormForecasts() {
        SANDSTORM_FORECASTS.clear();
        scheduledStormBiome = null;
        scheduledStormPhase = null;
        scheduledStormTime = -1L;
        tickCounter = 0;
    }

    static void dailyAndSand(ServerLevel level) {
        clearSandstormForecasts();

        FORECAST_MAP.entrySet().stream()
                .filter(entry -> SANDSTORM_BIOMES.contains(entry.getKey().biomeType()))
                .filter(entry -> {
                    BiomeForecast f = entry.getValue();
                    return f.getHumidity() != null && f.getHumidity().length > 0
                            && f.getPressure() != null && f.getPressure().length > 0
                            && f.getWind() != null && f.getWind().length > 0;
                })
                .filter(entry -> shouldTriggerSandstorm(
                        entry.getKey(),
                        entry.getValue().getHumidity(),
                        entry.getValue().getPressure(),
                        entry.getValue().getWind()[0]
                ))
                .forEach(entry -> {
                    BiomeInstanceKey key = entry.getKey();
                    entry.getValue().setSandstormExpected(true);
                    SANDSTORM_FORECASTS.add(key);
                });


        FORECAST_MAP.forEach(AtmosphericStateRegistry::initializeState);

        computeAverageForecastsByBiomeType();
        FORECAST_MAP.forEach(ForecastPointerRegistry::setPointer);

        if (!sandStormLoaded) return;
        if (!SandStormAPI.isSandstormActive() && scheduledStormBiome == null && !SANDSTORM_FORECASTS.isEmpty()) {
            BiomeInstanceKey selected = SANDSTORM_FORECASTS.stream()
                    .skip(level.random.nextInt(SANDSTORM_FORECASTS.size()))
                    .findFirst()
                    .orElse(null);

            if (selected != null) {
                BiomeForecast forecast = FORECAST_MAP.get(selected);
                if (forecast != null) {
                    long baseTime = (level.getDayTime() / 24000L) * 24000L;
                    long randomOffset = 1000 + level.random.nextInt(9000);

                    scheduledStormBiome = selected;
                    scheduledStormPhase = computeStormPhase(forecast);
                    scheduledStormTime = baseTime + randomOffset;

                    if(ProjectAtmosphere.DEBUG_MODE)
                        ProjectAtmosphere.LOGGER.info("[Atmosphere] Scheduled sandstorm at tick {} in biome {} (phase: {})",
                            scheduledStormTime, selected.biomeType(), scheduledStormPhase);
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

        if (scheduledStormBiome != null && level.getDayTime() >= scheduledStormTime) {
            SandStormAPI. startSandstorm(scheduledStormPhase, scheduledStormBiome);

            if(ProjectAtmosphere.DEBUG_MODE)
                ProjectAtmosphere.LOGGER.info("[Atmosphere] Triggered sandstorm in biome {} with phase {}",
                    scheduledStormBiome.biomeType(), scheduledStormPhase);

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
                            getWindValue(biome, level.getDayTime()));

                }
            });
            tickCounter = 0;

        }
        tickCounter++;
    }
    static boolean shouldTriggerSandstorm(
            BiomeInstanceKey key,
            float[][] humidity,
            float[][] pressure,
            WindVector wind
    ) {
        if (!SANDSTORM_BIOMES.contains(key.biomeType())) return false;

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

    static SandstormPhase computeStormPhase(BiomeForecast forecast) {
        float wind = forecast.getWind()[0].baseSpeed();
        float pressure = forecast.getPressure()[0][0];
        float humidity = forecast.getHumidity()[0][0];

        if (wind > 35 && pressure < 980 && humidity < 0.15f) return SandstormPhase.PHASE_5;
        if (wind > 30) return SandstormPhase.PHASE_4;
        if (wind > 25) return SandstormPhase.PHASE_3;
        if (wind > 20) return SandstormPhase.PHASE_2;
        return SandstormPhase.PHASE_1;
    }
}
