package net.Gabou.projectatmosphere.manager;


import net.Gabou.projectatmosphere.command.DebugAtmoCommand;
import net.Gabou.projectatmosphere.event.TemperatureTickHandler;
import net.Gabou.projectatmosphere.modules.humidity.HumidityModule;
import net.Gabou.projectatmosphere.modules.humidity.util.HumidityProfileManager;
import net.Gabou.projectatmosphere.modules.pressure.PressureModule;

import net.Gabou.projectatmosphere.modules.pressure.forecast.PressureForecast;
import net.Gabou.projectatmosphere.modules.pressure.util.PressureProfileManager;
import net.Gabou.projectatmosphere.modules.storm.StormModule;
import net.Gabou.projectatmosphere.modules.temperature.TemperatureModule;
import net.Gabou.projectatmosphere.modules.temperature.util.TemperatureProfileManager;
import net.Gabou.projectatmosphere.modules.wind.WindModule;
import net.Gabou.projectatmosphere.modules.wind.util.WindProfileManager;
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
        BlockPos center = world.getSharedSpawnPos();
        TemperatureModule.onServerStarting(world,center);
        HumidityModule.onServerStarting(world, center);
        PressureModule.onServerStarting(world, center);
        WindModule.onServerStarting(world, center);
        StormModule.onServerStarting(world,center);
        refreshUnifiedForecast();
    }
    public static void updateForecastAround(ServerLevel world, BlockPos center) {
        TemperatureModule.updateForecastAround(world, center);
        HumidityModule.updateForecastAround(world, center);
        PressureModule.updateForecastAround(world, center);
        WindModule.updateForecastAround(world, center);
        StormModule.updateForecastAround(world,center);
    }

    public static void onRegisterCommands(final RegisterCommandsEvent event)
    {
        // Register commands here
        TemperatureModule.onRegisterCommands(event);
        HumidityModule.onRegisterCommands(event);
        PressureModule.onRegisterCommands(event);
        WindModule.onRegisterCommands(event);
        StormModule.onRegisterCommands(event);
        DebugAtmoCommand.register(event.getDispatcher());
    }

    public static void onPlayerJoined(ServerLevel world, ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        TemperatureModule.onPlayerJoined(world, pos);
        HumidityModule.onPlayerJoined(world, pos);
        PressureModule.onPlayerJoined(world, pos);
        WindModule.onPlayerJoined(world, pos);
        StormModule.onPlayerJoined(world, pos);
        refreshUnifiedForecast();
    }

    public static void onPrecomputeProfiles(ServerLevel world) {
        TemperatureModule.onPrecomputeProfiles(world);
        HumidityModule.onPrecomputeProfiles(world);
        PressureModule.onPrecomputeProfiles(world);
        WindModule.onPrecomputeProfiles(world);
        StormModule.onPrecomputeProfiles(world);
        refreshUnifiedForecast();
    }

    public static void onSwapProfiles(ServerLevel world) {
        TemperatureModule.onSwapProfiles(world);
        HumidityModule.onSwapProfiles(world);
        PressureModule.onSwapProfiles(world);
        WindModule.onSwapProfiles(world);
        StormModule.onSwapProfiles(world);
        refreshUnifiedForecast();
    }
    public static void onRegenerate(ServerLevel world) {
        TemperatureModule.onRegenerate(world);
        HumidityModule.onRegenerate(world);
        PressureModule.onRegenerate(world);
        WindModule.onRegenerate(world);
        StormModule.onRegenerate(world);
        refreshUnifiedForecast();
    }

    public static void onSeasonChange(ServerLevel world) {
        TemperatureModule.onSeasonChange(world);  // Only temperature responds to season
        HumidityModule.onSeasonChange(world);
        PressureModule.onSeasonChange(world);
        WindModule.onSeasonChange(world);
        StormModule.onSeasonChange(world);
        refreshUnifiedForecast();
    }

    public static void refreshUnifiedForecast() {
        FORECAST_MAP.clear();

        for (PressureForecast.BiomeInstanceKey key : PressureProfileManager.getAllBiomeKeys()) {
            float[] temp = TemperatureProfileManager.getDayProfile(key.biomeType());
            float[] pressure = PressureProfileManager.getTodayProfile(key);
            float[] humidity = HumidityProfileManager.getDayProfile(key.biomeType());
            float[] wind = WindProfileManager.getTodayProfile(key.biomeType());

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
        TemperatureModule.onServerStopping(world);
        PressureModule.onServerStopping(world);
        HumidityModule.onServerStopping(world);
        WindModule.onServerStopping(world);
    }

    public static void init() {
        TemperatureModule.init();
        HumidityModule.init();
        PressureModule.init();
        WindModule.init();
        StormModule.init();
    }

    public static void tick(ServerLevel level) {
        PressureModule.tick(level);
    }


    /** Central record to unify today's weather-like forecast */
    public static record BiomeForecast(float[] temperature, float[] pressure, float[] humidity, float[] wind) {}
}
