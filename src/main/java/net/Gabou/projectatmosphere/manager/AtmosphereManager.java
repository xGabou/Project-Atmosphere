package net.Gabou.projectatmosphere.manager;


import net.Gabou.projectatmosphere.command.DebugAtmoCommand;
import net.Gabou.projectatmosphere.command.SpawnCloudCommand;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.modules.humidity.HumidityCommand;
import net.Gabou.projectatmosphere.modules.pressure.PressureCommand;
import net.Gabou.projectatmosphere.modules.temperature.command.TemperatureCommands;
import net.Gabou.projectatmosphere.modules.wind.WindCommand;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;


import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class AtmosphereManager {



    /**
     * Map to track player readiness for weather data
     * Key: Player UUID, Value: CompletableFuture that completes when the player is ready
     */
    private static final Map<UUID, CompletableFuture<Void>> playerReadyMap = new ConcurrentHashMap<>();

    public static boolean isInitialGenerationDone =false;

    public static void onPlayerLogout(ServerLevel world, ServerPlayer player) {
        playerReadyMap.remove(player.getUUID());
    }





    private static final List<BlockPos> allCenterOfMap = new ArrayList<>();

    public static List<BlockPos> getAllCenterOfMap() {
        return allCenterOfMap;
    }

    public static void onServerStarting(ServerLevel world) {
        playerReadyMap.clear();
        isInitialGenerationDone=ForecastOrchestrator.onServerStart(world);

    }
    public static void onServerStopping(ServerLevel world) {
        ForecastOrchestrator.onServerStop(world);
        playerReadyMap.clear();
        isInitialGenerationDone = false;

    }

    public static void updateForecastAround(ServerLevel world, BlockPos center) {
        AsyncAtmosphereService.runWeather(() -> {
            ForecastOrchestrator.updateForecast(world,center);
//            biomeSamples.addAll(AtmosphereUtils.findBiomes(world, center, DEFAULT_RADIUS));
//            allCenterOfMap.add(center);
//            TemperatureManager.updateForecastAround(world, biomeSamples);
//            PressureManager.updateForecastAround(world, biomeSamples);
//            HumidityManager.updateForecastAround(world, biomeSamples);
//            WindManager.updateForecastAround(world, biomeSamples);
//            StormManager.updateForecastAround(world, biomeSamples);
//            refreshUnifiedForecast(biomeSamples);
        });
    }

    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        // Register commands here
        TemperatureCommands.register(event.getDispatcher());
        HumidityCommand.register(event.getDispatcher());
        PressureCommand.register(event.getDispatcher());
        WindCommand.register(event.getDispatcher());
        SpawnCloudCommand.register(event.getDispatcher());
        DebugAtmoCommand.register(event.getDispatcher());
    }



    public static void onPlayerLogin(ServerLevel world, ServerPlayer player) {
        UUID uuid = player.getUUID();
        CompletableFuture<Void> future = new CompletableFuture<>();
        playerReadyMap.put(uuid, future);

        world.getServer().execute(() -> {
            ForecastOrchestrator.onPlayerLogin(player, world);
            future.complete(null);
        });
    }


    public static CompletableFuture<Void> getPlayerReadyFuture(ServerPlayer player) {
        return playerReadyMap.computeIfAbsent(player.getUUID(), uuid -> new CompletableFuture<>());
    }
    public static boolean isPlayerReady(UUID uuid) {
        CompletableFuture<Void> future = playerReadyMap.get(uuid);
        return future != null && future.isDone();
    }

    public static void onSwapProfiles(ServerLevel world) {
        AsyncAtmosphereService.runWeather(() -> {
            ForecastOrchestrator.onSwapDay(world);
        });
    }

    public static void onRegenerate(ServerLevel world) {
        AsyncAtmosphereService.runWeather(() -> {
            ForecastGenerator.clearBiomeSamples();
            for (ServerPlayer player : world.players()) {
                BlockPos pos = player.blockPosition();
                allCenterOfMap.add(pos);
                ForecastOrchestrator.regenerateAround(world, pos);
            }
        });
    }


    public static void onSeasonChange(ServerLevel world) {
        onRegenerate(world);
    }



    public static void tick(ServerLevel level) {
    }





}
