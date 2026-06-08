package net.Gabou.projectatmosphere.manager;



import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.backend.CloudRegionSyncManager;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
import net.Gabou.projectatmosphere.command.ProjectAtmosphereCommands;
import net.Gabou.projectatmosphere.client.loading.ForecastLoadingStage;
import net.Gabou.projectatmosphere.event.EventHandler;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphereStatusSyncManager;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneManager;
import net.Gabou.projectatmosphere.modules.snowstorm.SnowstormManager;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.Gabou.projectatmosphere.network.ForecastLoadingStatusPacket;
import net.Gabou.projectatmosphere.network.NetworkHandler;
import net.Gabou.projectatmosphere.seasons.SeasonTimeHelper;
import net.Gabou.projectatmosphere.seasons.SeasonStage;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;


import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraftforge.network.PacketDistributor;

public class AtmosphereManager {


    /**
     * Map to track player readiness for weather data
     * Key: Player UUID, Value: CompletableFuture that completes when the player is ready
     */
    private static final Map<UUID, CompletableFuture<Void>> playerReadyMap = new ConcurrentHashMap<>();
    private static final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, SeasonStage> lastSeasonStage = new ConcurrentHashMap<>();

    public static boolean isInitialGenerationDone = false;


    public static void onPlayerLogout(ServerLevel world, ServerPlayer player) {
        playerReadyMap.remove(player.getUUID());
    }


    private static final List<BlockPos> allCenterOfMap = new ArrayList<>();

    public static List<BlockPos> getAllCenterOfMap() {
        return allCenterOfMap;
    }

    public static void onServerStarted(ServerLevel world) {
        resetRuntimeState();
        isInitialGenerationDone = ForecastOrchestrator.onServerStart(world);
        AtmosphereCloudServices.get().onServerStarted(world);
        recordSeasonStage(world);

    }

    public static void onServerStopping(ServerLevel world) {
        ForecastOrchestrator.onServerStop(world);
        resetRuntimeState();
        AtmosphereCloudServices.get().onServerStopping(world);

    }

    public static void updateForecastAround(ServerLevel world, BlockPos center) {
        AsyncAtmosphereService.runWeather(() -> {
            ForecastOrchestrator.updateForecast(world, center);
        });
    }

    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        ProjectAtmosphereCommands.register(event.getDispatcher());
    }


    public static void onPlayerLogin(ServerLevel world, ServerPlayer player) {
        UUID uuid = player.getUUID();
        CompletableFuture<Void> future = new CompletableFuture<>();
        playerReadyMap.put(uuid, future);

        world.getServer().execute(() -> {
            ForecastOrchestrator.onPlayerLogin(player, world);
            sendPlayerForecastSnapshot(player);
            syncPlayerRuntimeState(player);
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
        ProjectAtmosphere.LOGGER.info("Regenerating weather data for all players");
        AsyncAtmosphereService.runWeather(() -> {
            EventHandler.onRegenerate();
            AtmosphereCloudServices.get().clearForRegeneration(world);
            if (AtmosphereCloudServices.isSimpleCloudsLoaded()) {
                TornadoManager.clearTornadoes();
                HurricaneManager.clearHurricanes();
            }
            ForecastOrchestrator.clearAndRegenerate(world);
        });
        AtmosphereCloudServices.get().clearForRegeneration(world);

    }


    public static void onSeasonChange(ServerLevel world) {
        recordSeasonStage(world);
        AsyncAtmosphereService.runWeather(() -> ForecastOrchestrator.regenerateForSeason(world));
    }


    private static int count;

    public static void tick(ServerLevel level) {
        checkSeasonTransition(level);
        // During regeneration, skip dependent ticks to avoid using transient/cleared state
        if (!ForecastOrchestrator.isRegenerating()) {
            ForecastOrchestrator.tick(level);
            SnowstormManager.tick(level);
            if (AtmosphereCloudServices.isSimpleCloudsLoaded()) {
                TornadoManager.tick(level);
                HurricaneManager.tick(level);
            }
        } else {
            // Still advance orchestrator's internal timing (e.g., tornado check scheduling) safely
            ForecastOrchestrator.tick(level);
        }
        AtmosphereCloudServices.get().tick(level, count);
        count++;
    }

    private static void checkSeasonTransition(ServerLevel level) {
        SeasonStage stage = SeasonTimeHelper.stage(level);
        if (stage == null) {
            return;
        }
        SeasonStage previous = lastSeasonStage.get(level.dimension());
        if (previous == null) {
            lastSeasonStage.put(level.dimension(), stage);
            return;
        }
        if (previous != stage) {
            onSeasonChange(level);
        }
    }

    private static void recordSeasonStage(ServerLevel level) {
        SeasonStage stage = SeasonTimeHelper.stage(level);
        if (stage != null) {
            lastSeasonStage.put(level.dimension(), stage);
        }
    }

    private static void resetRuntimeState() {
        playerReadyMap.clear();
        lastSeasonStage.clear();
        isInitialGenerationDone = false;
        count = 0;
    }

    private static void sendPlayerForecastSnapshot(ServerPlayer player) {
        Map<net.minecraft.resources.ResourceLocation, float[]> forecastSnapshot = ForecastGenerator.createDailyTemperatureSnapshotForSync();
        int forecastProfileCount = forecastSnapshot.size();
        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                ForecastLoadingStatusPacket.status(
                        ForecastLoadingStage.RECEIVING_FORECAST_DATA,
                        null,
                        forecastProfileCount > 0 ? forecastProfileCount + " biome profiles queued" : "Preparing forecast snapshot",
                        0.42F,
                        "player_login_forecast_snapshot"
                )
        );
        ForecastGenerator.sendDailyForecastsToPlayer(player, forecastSnapshot);
        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                ForecastLoadingStatusPacket.ready("player_login_ready")
        );
    }

    private static void syncPlayerRuntimeState(ServerPlayer player) {
        AtmosphereStatusSyncManager.syncPlayer(player);
        CloudRegionSyncManager.syncPlayer(player);
        if (AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            TornadoManager.syncToPlayer(player);
            HurricaneManager.syncToPlayer(player);
        }
    }

}
