package net.Gabou.projectatmosphere.manager;


import com.Gabou.sereneseasonsplus.api.SSPApi;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.command.DebugAtmoCommand;
import net.Gabou.projectatmosphere.command.SpawnCloudCommand;
import net.Gabou.projectatmosphere.event.EventHandler;
import net.Gabou.projectatmosphere.gameplay.WindPhysics;
import net.Gabou.projectatmosphere.modules.humidity.HumidityCommand;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneManager;
import net.Gabou.projectatmosphere.modules.pressure.PressureCommand;
import net.Gabou.projectatmosphere.modules.snowstorm.SnowstormManager;
import net.Gabou.projectatmosphere.modules.temperature.command.TemperatureCommands;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.Gabou.projectatmosphere.modules.wind.WindCommand;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.CloudRegionQueue;
import net.Gabou.projectatmosphere.util.ICloudRegionId;
import net.Gabou.projectatmosphere.util.WeatherType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;


import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AtmosphereManager {


    /**
     * Map to track player readiness for weather data
     * Key: Player UUID, Value: CompletableFuture that completes when the player is ready
     */
    private static final Map<UUID, CompletableFuture<Void>> playerReadyMap = new ConcurrentHashMap<>();

    private static final List<CloudRegion> cloudRegions = new ArrayList<>();

    public static boolean isInitialGenerationDone = false;


    public static void onPlayerLogout(ServerLevel world, ServerPlayer player) {
        playerReadyMap.remove(player.getUUID());
    }


    private static final List<BlockPos> allCenterOfMap = new ArrayList<>();

    public static List<BlockPos> getAllCenterOfMap() {
        return allCenterOfMap;
    }

    public static void onServerStarting(ServerLevel world) {
        playerReadyMap.clear();
        isInitialGenerationDone = ForecastOrchestrator.onServerStart(world);
        count = 0;

    }

    public static void onServerStopping(ServerLevel world) {
        ForecastOrchestrator.onServerStop(world);
        playerReadyMap.clear();
        isInitialGenerationDone = false;
        count = 0;
        CloudRegionQueue.clear();

    }

    public static void updateForecastAround(ServerLevel world, BlockPos center) {
        ProjectAtmosphere.LOGGER.info("Updating forecast Around");
        AsyncAtmosphereService.runWeather(() -> {
            ForecastOrchestrator.updateForecast(world, center);
        });
    }

    public static void onRegisterCommands(final RegisterCommandsEvent event) {

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
        ProjectAtmosphere.LOGGER.info("Swapping profiles and updating weather");
        AsyncAtmosphereService.runWeather(() -> {
            ForecastOrchestrator.onSwapDay(world);
        });
    }

    public static void onRegenerate(ServerLevel world) {
        ProjectAtmosphere.LOGGER.info("Regenerating weather data for all players");
        ForecastOrchestrator.clearActiveBiomeKeys();
        AsyncAtmosphereService.runWeather(() -> {
            ForecastGenerator.clearBiomeSamples();
            EventHandler.onRegenerate();
            CloudManager.get(world).getCloudGenerator().removeAllClouds();
            TornadoManager.clearTornadoes();
            for (ServerPlayer player : world.players()) {
                BlockPos pos = player.blockPosition();
                allCenterOfMap.add(pos);
                ForecastOrchestrator.regenerateAround(world, pos);
            }
        });
        CloudRegionQueue.clear();
    }


    public static void onSeasonChange(ServerLevel world) {
        onRegenerate(world);
    }


    private static int count;

    public static void tick(ServerLevel level) {

        ForecastOrchestrator.tick(level);
        WindPhysics.onServerTick(level);
        TornadoManager.tick(level);
        HurricaneManager.tick(level);
        SnowstormManager.tick(level);

        if (count % 20 != 0) {
            CloudManager<ServerLevel> manager = CloudManager.get(level);

            // Current clouds still existing in the manager
            List<CloudRegion> activeRegions = new ArrayList<>(manager.getClouds());

            // For matching IDs only
            Set<Integer> activeIds = activeRegions.stream()
                    .filter(r -> r instanceof ICloudRegionId)
                    .map(r -> ((ICloudRegionId) r).projectatmosphere$getId())
                    .collect(Collectors.toSet());

            for (CloudRegion cloudRegion : new ArrayList<>(cloudRegions)) {
                if (cloudRegion instanceof ICloudRegionId id) {
                    if (!activeIds.contains(id.projectatmosphere$getId())) {
                        queueRemoveCloudRegion(cloudRegion);
                    }
                }
            }
        }

        count++;
        if (CloudRegionQueue.isEmpty()) {
            CloudRegionQueue.shuffle();
            return;
        }
        CloudRegionQueue.Entry entry;
        while ((entry = CloudRegionQueue.poll()) != null) {
            switch (entry.type()) {
                case ADD -> {
                    handleCloudRegionToQueue(level, entry.region());
                }
                case REMOVE -> {
                    handleCloudRegionToRemove(level, entry.region());
                }

            }
        }
    }

    private static void handleCloudRegionToQueue(ServerLevel level, CloudRegion cloudRegion) {
        if (cloudRegions.contains(cloudRegion)) {
            return;
        }
        cloudRegions.add(cloudRegion);
        if (WeatherType.isRainy(cloudRegion.getCloudTypeId()))
            SSPApi.getINSTANCE().onSimpleCloudsSpawned(level, ((ICloudRegionId) cloudRegion).projectatmosphere$getId());

    }

    private static void handleCloudRegionToRemove(ServerLevel level, CloudRegion cloudRegion) {
        if (CloudManager.get(level).getClouds().contains(cloudRegion) && !cloudRegions.contains(cloudRegion)) {
            return;
        }

        cloudRegions.remove(cloudRegion);
        if (WeatherType.isRainy(cloudRegion.getCloudTypeId()))
            SSPApi.getINSTANCE().onCloudsDespawned(level, ((ICloudRegionId) cloudRegion).projectatmosphere$getId());

    }

    public static List<CloudRegion> getCloudRegions() {
        return Collections.unmodifiableList(cloudRegions);
    }


    public static void queueAddCloudRegion(CloudRegion cloudRegion) {
        CloudRegionQueue.enqueueAdd(cloudRegion);

    }

    public static void queueRemoveCloudRegion(CloudRegion cloudRegion) {
        CloudRegionQueue.enqueueRemove(cloudRegion);

    }
}
