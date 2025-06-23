package net.Gabou.projectatmosphere.manager;


import net.Gabou.projectatmosphere.command.DebugAtmoCommand;
import net.Gabou.projectatmosphere.event.TemperatureTickHandler;
import net.Gabou.projectatmosphere.modules.humidity.manager.HumidityManager;
import net.Gabou.projectatmosphere.modules.humidity.util.HumidityProfileManager;
import net.Gabou.projectatmosphere.modules.humidity.util.HumidityStorageManager;
import net.Gabou.projectatmosphere.modules.pressure.forecast.PressureForecast;
import net.Gabou.projectatmosphere.modules.pressure.util.PressureProfileManager;
import net.Gabou.projectatmosphere.modules.pressure.util.PressureStorageManager;

import net.Gabou.projectatmosphere.modules.storm.util.StormStorageManager;

import net.Gabou.projectatmosphere.modules.temperature.util.ForecastStorageManager;
import net.Gabou.projectatmosphere.modules.temperature.util.TemperatureProfileManager;

import net.Gabou.projectatmosphere.modules.wind.manager.WindManager;
import net.Gabou.projectatmosphere.modules.temperature.manager.TemperatureManager;
import net.Gabou.projectatmosphere.modules.pressure.manager.PressureManager;
import net.Gabou.projectatmosphere.modules.storm.manager.StormManager;
import net.Gabou.projectatmosphere.modules.wind.util.WindProfileManager;
import net.Gabou.projectatmosphere.modules.wind.util.WindStorageManager;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;


import static net.Gabou.projectatmosphere.ProjectAtmosphere.DEFAULT_RADIUS;
public class AtmosphereManager {

    /** Master map that holds forecast data for each biome */
    private static final Map<ResourceLocation, BiomeForecast> FORECAST_MAP = new HashMap<>();

    private static final Set<BiomeInstanceKey> biomeSamples = ConcurrentHashMap.newKeySet();



    public static Set<BiomeInstanceKey> getBiomeSamples() {
        return biomeSamples;
    }

    private static void clearBiomeSamples() {
        biomeSamples.clear();
    }

    private static List<BlockPos> allCenterOfMap = new ArrayList<>();

    public static List<BlockPos> getAllCenterOfMap() {
        return allCenterOfMap;
    }

    public static void onServerStarting(ServerLevel world) {
       AsyncAtmosphereService.runWeather(() -> {
            ForecastStorageManager.loadAll(world);
            HumidityStorageManager.loadAll(world);
            PressureStorageManager.loadAll(world);
            WindStorageManager.loadAll(world);
            StormStorageManager.loadAll(world);
            refreshUnifiedForecast(biomeSamples);
        });
    }
    public static void updateForecastAround(ServerLevel world, BlockPos center) {
        AsyncAtmosphereService.runWeather(() -> {;
            biomeSamples.addAll(AtmosphereUtils.findBiomes(world, center, DEFAULT_RADIUS));
            allCenterOfMap.add(center);
            TemperatureManager.updateForecastAround(world, biomeSamples);
            PressureManager.updateForecastAround(world, biomeSamples);
            HumidityManager.updateForecastAround(world, biomeSamples);
            WindManager.updateForecastAround(world, biomeSamples);
            StormManager.updateForecastAround(world, biomeSamples);
            refreshUnifiedForecast(biomeSamples);
        });
    }

    public static void onRegisterCommands(final RegisterCommandsEvent event)
    {
        // Register commands here
        TemperatureManager.onRegisterCommands(event);
        HumidityManager.onRegisterCommands(event);
        PressureManager.onRegisterCommands(event);
        WindManager.onRegisterCommands(event);
        StormManager.onRegisterCommands(event);
        DebugAtmoCommand.register(event.getDispatcher());
    }

    public static void onPlayerJoined(ServerLevel world, ServerPlayer player) {
        AsyncAtmosphereService.runWeather(() -> {;
            BlockPos pos = player.blockPosition();
            allCenterOfMap.add(pos);
            biomeSamples.addAll(AtmosphereUtils.findBiomes(world, pos, DEFAULT_RADIUS));
            TemperatureManager.onPlayerJoined(world,biomeSamples);
            HumidityManager.onPlayerJoined(world,biomeSamples);
            PressureManager.onPlayerJoined(world, biomeSamples);
            WindManager.onPlayerJoined(world, biomeSamples);
            StormManager.onPlayerJoined(world, biomeSamples);
            SimpleCloudSpawner.onPlayerJoined(world, biomeSamples);
            refreshUnifiedForecast(biomeSamples);
        });
    }

    public static void onPrecomputeProfiles(ServerLevel world) {
        AsyncAtmosphereService.runWeather(() -> {
        TemperatureManager.onPrecomputeProfiles(world);
        HumidityManager.onPrecomputeProfiles(world);
        PressureManager.onPrecomputeProfiles(world);
        WindManager.onPrecomputeProfiles(world);
        StormManager.onPrecomputeProfiles(world);
        refreshUnifiedForecast(biomeSamples);
        });
    }

    public static void onSwapProfiles(ServerLevel world) {
        AsyncAtmosphereService.runWeather(() -> {
        TemperatureManager.onSwapProfiles(world);
        HumidityManager.onSwapProfiles(world);
        PressureManager.onSwapProfiles(world);
        WindManager.onSwapProfiles(world);
        StormManager.onSwapProfiles(world);
        refreshUnifiedForecast(biomeSamples);
        });
    }
    public static void onRegenerate(ServerLevel world) {
        AsyncAtmosphereService.runWeather(() -> {
            clearBiomeSamples();
            for (ServerPlayer player : world.players()) {
                BlockPos pos = player.blockPosition();
                allCenterOfMap.add(pos);
                biomeSamples.addAll(AtmosphereUtils.findBiomes(world, pos, DEFAULT_RADIUS));
                TemperatureManager.onRegenerate(world, biomeSamples);
                HumidityManager.onRegenerate(world, biomeSamples);
                PressureManager.onRegenerate(world, biomeSamples);
                WindManager.onRegenerate(world, biomeSamples);
                StormManager.onRegenerate(world, biomeSamples);
            }

            refreshUnifiedForecast(biomeSamples);
        });
    }


    public static void onSeasonChange(ServerLevel world) {
        onRegenerate(world);
    }

    public static void refreshUnifiedForecast(Set<BiomeInstanceKey> biomeSamples) {
        FORECAST_MAP.clear();
        for (BiomeInstanceKey key : biomeSamples) {
            float[] temp = TemperatureProfileManager.getDayProfile(key);
            float[] pressure = PressureProfileManager.getTodayProfile(key);
            float[] humidity = HumidityProfileManager.getDayProfile(key);
            float wind = WindProfileManager.getTodayProfile(key);
            FORECAST_MAP.put(key.biomeType(), new BiomeForecast(temp, pressure, humidity, wind));
        }
    }



    public static BiomeForecast getForecast(ResourceLocation biome) {
         return FORECAST_MAP.get(biome);
    }

    /** Optional: access to the full map */
    public static Map<ResourceLocation, BiomeForecast> getAllForecasts() {
        return FORECAST_MAP;
    }

    public static void onServerStopping(ServerLevel world) {
        TemperatureManager.onServerStopping(world);
        HumidityManager.onServerStopping(world);
        PressureManager.onServerStopping(world);
        WindManager.onServerStopping(world);
        StormManager.onServerStopping(world);
    }

    public static void tick(ServerLevel level) {
//        AsyncAtmosphereService.runWeather(() -> {
//            PressureManager.tickSystem(level);
//        });
    }


    /** Central record to unify today's weather-like forecast */
    public record BiomeForecast(float[] temperature, float[] pressure, float[] humidity, float wind) {}
}
