package net.Gabou.projectatmosphere.modules.wind.manager;

import net.Gabou.projectatmosphere.modules.wind.forecast.WindForecast;
import net.Gabou.projectatmosphere.modules.wind.util.WindProfileManager;
import net.Gabou.projectatmosphere.modules.wind.util.WindStorageManager;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.Map;

import static net.Gabou.projectatmosphere.ProjectAtmosphere.DEFAULT_RADIUS;
import static net.Gabou.projectatmosphere.ProjectAtmosphere.LOGGER;

public class WindManager {

    public static final int WIND_SPEED = 10;  // Default speed in m/s

    public static void init(ServerLevel world, BlockPos center) {

            try {
                WindForecast.generateForecastAround(world, center, DEFAULT_RADIUS);
                WindProfileManager.generateTodayAndTomorrowProfiles(world);
            } catch (Exception e) {
                LOGGER.error("Failed to generate wind forecast around " + center, e);
            }

    }

    public static void onPlayerJoined(ServerLevel world, BlockPos pos) {
        init(world, pos);
    }

    public static float[][] getWeeklyForecast(BiomeInstanceKey biome) {
        return WindProfileManager.getWeeklyForecast(biome);
    }

    public static float getCurrentWind(BiomeInstanceKey biome, long worldTick) {
        return WindProfileManager.getCurrentWindSpeed(biome, worldTick);
    }

    public static void onPrecomputeProfiles(ServerLevel world) {

            WindProfileManager.generateTodayAndTomorrowProfiles(world);


    }

    public static void onSwapProfiles(ServerLevel world) {

            for (BiomeInstanceKey key : WindProfileManager.getAllBiomeKeys()) {
                float[] tomorrow = WindProfileManager.getTomorrowProfile(key);
                if (tomorrow != null) {
                    WindProfileManager.putDayProfile(key, tomorrow);
                }
            }
            WindProfileManager.generateTodayAndTomorrowProfiles(world);

    }


    public static void onSeasonChange(ServerLevel world) {
        onRegenerate(world, world.players());
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {

    }

    public static void onRegenerate(ServerLevel world, List<ServerPlayer> players) {
        WindProfileManager.clearAll();
        init(world, world.getSharedSpawnPos());

    }
}
