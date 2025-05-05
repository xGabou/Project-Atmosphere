package net.Gabou.projectatmosphere.modules.wind;

import net.Gabou.projectatmosphere.modules.wind.manager.WindManager;
import net.Gabou.projectatmosphere.modules.wind.util.WindStorageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;

public class WindModule {
    public static void init() {
    }

    public static void onPlayerJoined(ServerLevel level, BlockPos pos) {
            WindManager.onPlayerJoined(level, pos);
    }

    public static void onServerStopping(ServerLevel event) {
        ServerLevel world = event.getServer().getLevel(ServerLevel.OVERWORLD);
        if (world != null) {
            WindStorageManager.saveAll(world);
        }
    }
    public static void onServerStarting(ServerLevel world, BlockPos center) {
        WindStorageManager.loadAll(world);
        WindManager.init(world, center);
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // Register commands here
        WindManager.onRegisterCommands(event);
    }

    public static void onSeasonChange(ServerLevel world) {
        WindManager.onSeasonChange(world);
    }
    public static void onSwapProfiles(ServerLevel world) {
        WindManager.onSwapProfiles(world);
    }

    public static void onPrecomputeProfiles(ServerLevel world) {
        WindManager.onPrecomputeProfiles(world);
    }

    public static void onRegenerate(ServerLevel world) {
        WindManager.onRegenerate(world, world.players());
    }

    public static void updateForecastAround(ServerLevel world, BlockPos center) {

    }
}
