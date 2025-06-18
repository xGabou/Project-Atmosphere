package net.Gabou.projectatmosphere.manager;


import net.Gabou.projectatmosphere.command.DebugAtmoCommand;
import net.Gabou.projectatmosphere.event.TemperatureTickHandler;
import net.Gabou.projectatmosphere.modules.humidity.manager.HumidityManager;
import net.Gabou.projectatmosphere.modules.humidity.util.HumidityProfileManager;
import net.Gabou.projectatmosphere.modules.humidity.util.HumidityStorageManager;
import net.Gabou.projectatmosphere.modules.pressure.PressureModule;

import net.Gabou.projectatmosphere.modules.pressure.forecast.PressureForecast;
import net.Gabou.projectatmosphere.modules.pressure.util.PressureProfileManager;
import net.Gabou.projectatmosphere.modules.pressure.util.PressureStorageManager;
import net.Gabou.projectatmosphere.modules.storm.StormModule;
import net.Gabou.projectatmosphere.modules.storm.util.StormStorageManager;
import net.Gabou.projectatmosphere.modules.temperature.TemperatureModule;
import net.Gabou.projectatmosphere.modules.temperature.util.ForecastStorageManager;
import net.Gabou.projectatmosphere.modules.temperature.util.TemperatureProfileManager;
import net.Gabou.projectatmosphere.modules.wind.WindModule;
import net.Gabou.projectatmosphere.modules.wind.manager.WindManager;
import net.Gabou.projectatmosphere.modules.temperature.manager.TemperatureManager;
import net.Gabou.projectatmosphere.modules.pressure.manager.PressureManager;
import net.Gabou.projectatmosphere.modules.storm.manager.StormManager;
import net.Gabou.projectatmosphere.modules.wind.util.WindProfileManager;
import net.Gabou.projectatmosphere.modules.wind.util.WindStorageManager;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import java.util.HashMap;
import java.util.Map;
public class AtmosphereManager {

    /** Master map that holds forecast data for each biome */
    private static final Map<ResourceLocation, BiomeForecast> FORECAST_MAP = new HashMap<>();

    public static void onServerStarting(ServerLevel world) {
        AsyncAtmosphereService.runWeather(() -> {
            ForecastStorageManager.loadAll(world);
            HumidityStorageManager.loadAll(world);
            PressureStorageManager.loadAll(world);
            WindStorageManager.loadAll(world);
            StormStorageManager.loadAll(world);
            refreshUnifiedForecast();
        });


    }
    public static void updateForecastAround(ServerLevel world, BlockPos center) {
        AsyncAtmosphereService.runWeather(() -> {;
        TemperatureManager.updateForecastAround(world, center);
            HumidityManager.updateForecastAround(world, center);
            PressureManager.updateForecastAround(world, center);
            WindManager.updateForecastAround(world, center);
            StormManager.updateForecastAround(world,center);
            refreshUnifiedForecast();
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
        TemperatureManager.onPlayerJoined(world, pos);
        HumidityManager.onPlayerJoined(world, pos);
            PressureManager.onPlayerJoined(world, pos);
            WindManager.onPlayerJoined(world, pos);
            StormManager.onPlayerJoined(world, pos);
            refreshUnifiedForecast();
        });
    }

    public static void onPrecomputeProfiles(ServerLevel world) {
        AsyncAtmosphereService.runWeather(() -> {
        TemperatureManager.onPrecomputeProfiles(world);
        HumidityManager.onPrecomputeProfiles(world);
        PressureManager.onPrecomputeProfiles(world);
        WindManager.onPrecomputeProfiles(world);
        StormManager.onPrecomputeProfiles(world);
        refreshUnifiedForecast();
        });
    }

    public static void onSwapProfiles(ServerLevel world) {
        AsyncAtmosphereService.runWeather(() -> {
        TemperatureManager.onSwapProfiles(world);
        HumidityManager.onSwapProfiles(world);
        PressureManager.onSwapProfiles(world);
        WindManager.onSwapProfiles(world);
        StormManager.onSwapProfiles(world);
        refreshUnifiedForecast();
        });
    }
    public static void onRegenerate(ServerLevel world) {
        AsyncAtmosphereService.runWeather(() -> {
        TemperatureManager.onRegenerate(world,world.players());
        HumidityManager.onRegenerate(world,world.players());
        PressureManager.onRegenerate(world,world.players());
        WindManager.onRegenerate(world,world.players());
        StormManager.onRegenerate(world,world.players());
        refreshUnifiedForecast();
        });
    }

    public static void onSeasonChange(ServerLevel world) {
        AsyncAtmosphereService.runWeather(() -> {
        TemperatureManager.onSeasonChange(world);  // Only temperature responds to season
        HumidityManager.onSeasonChange(world);
        PressureManager.onSeasonChange(world);
        WindManager.onSeasonChange(world);
        StormManager.onSeasonChange(world);
        refreshUnifiedForecast();
        });
    }

    public static void refreshUnifiedForecast() {
        FORECAST_MAP.clear();

        for (BiomeInstanceKey key : PressureProfileManager.getAllBiomeKeys()) {
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
