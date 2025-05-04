package net.Gabou.projectatmosphere.manager;


import net.Gabou.projectatmosphere.command.DebugAtmoCommand;
import net.Gabou.projectatmosphere.command.SpawnCloudCommand;
import net.Gabou.projectatmosphere.modules.humidity.HumidityModule;
import net.Gabou.projectatmosphere.modules.humidity.manager.HumidityManager;
import net.Gabou.projectatmosphere.modules.humidity.util.HumidityProfileManager;
import net.Gabou.projectatmosphere.modules.pressure.PressureModule;
import net.Gabou.projectatmosphere.modules.pressure.manager.PressureManager;
import net.Gabou.projectatmosphere.modules.pressure.util.PressureProfileManager;
import net.Gabou.projectatmosphere.modules.temperature.TemperatureModule;
import net.Gabou.projectatmosphere.modules.temperature.manager.TemperatureManager;
import net.Gabou.projectatmosphere.modules.temperature.util.TemperatureProfileManager;
import net.Gabou.projectatmosphere.modules.wind.WindModule;
import net.Gabou.projectatmosphere.modules.wind.manager.WindManager;
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

        TemperatureModule.onServerStarting(world);
        PressureModule.onServerStarting(world, center);
        HumidityModule.onServerStarting(world, center);
        WindModule.onServerStarting(world, center);

        refreshUnifiedForecast(world);
    }

    public static void onRegisterCommands(final RegisterCommandsEvent event)
    {
        // Register commands here
        TemperatureModule.onRegisterCommands(event);
        SpawnCloudCommand.register(event.getDispatcher());
        DebugAtmoCommand.register(event.getDispatcher());
    }
    public static void onServerStarted(ServerLevel world) {
        TemperatureModule.onServerStarted(world);
        PressureModule.onServerStarted(world);
        HumidityModule.onServerStarted(world);
        WindModule.onServerStarted(world);
        refreshUnifiedForecast(world);
    }

    public static void onPlayerJoined(ServerLevel world, ServerPlayer player) {
        BlockPos pos = player.blockPosition();

        TemperatureManager.onPlayerJoined(world, pos);
        PressureManager.onPlayerJoined(world, pos);
        HumidityManager.onPlayerJoined(world, pos);
        WindManager.onPlayerJoined(world, pos);
        refreshUnifiedForecast(world);
    }

    public static void onPrecomputeProfiles(ServerLevel world) {
        TemperatureManager.onPrecomputeProfiles(world);
        PressureManager.onPrecomputeProfiles(world);
        HumidityManager.onPrecomputeProfiles(world);
        WindManager.onPrecomputeProfiles(world);
    }

    public static void onSwapProfiles(ServerLevel world) {
        TemperatureManager.onSwapProfiles(world);
        PressureManager.onSwapProfiles(world);
        HumidityManager.onSwapProfiles(world);
        WindManager.onSwapProfiles(world);
        refreshUnifiedForecast(world);
    }

    public static void onSeasonChange(ServerLevel world) {
        TemperatureManager.onSeasonChange(world);  // Only temperature responds to season
        refreshUnifiedForecast(world);
    }

    public static void refreshUnifiedForecast(ServerLevel world) {
        FORECAST_MAP.clear();
        for (String biome : TemperatureProfileManager.getAllBiomeKeys()) {
            float[] temp = TemperatureProfileManager.getDayProfile(ResourceLocation.parse(biome));
            float[] pressure = PressureProfileManager.getTodayProfile(ResourceLocation.parse(biome));
            float[] humidity = HumidityProfileManager.getDayProfile(ResourceLocation.parse(biome));
            float[] wind = WindProfileManager.getTodayProfile(ResourceLocation.parse(biome));

            FORECAST_MAP.put(ResourceLocation.parse(biome), new BiomeForecast(temp, pressure, humidity, wind));

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
        PressureModule.init();
        HumidityModule.init();
        WindModule.init();
    }

    /** Central record to unify today's weather-like forecast */
    public static record BiomeForecast(float[] temperature, float[] pressure, float[] humidity, float[] wind) {}
}
