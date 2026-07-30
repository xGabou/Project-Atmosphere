package net.Gabou.projectatmosphere.event;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.backend.CloudBackendMigrationManager;
import net.Gabou.projectatmosphere.clouds.backend.CloudVisualBackend;
import net.Gabou.projectatmosphere.clouds.cell.sim.CloudCellSimulationManager;
import net.Gabou.projectatmosphere.clouds.cell.sim.NativeTornadoEffects;
import net.Gabou.projectatmosphere.clouds.field.network.CloudFieldSyncManager;
import net.Gabou.projectatmosphere.clouds.field.runtime.CloudFieldRuntimeManager;
import net.Gabou.projectatmosphere.clouds.simulation.CloudRegionManager;
import net.Gabou.projectatmosphere.clouds.network.CloudRegionSyncManager;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudService;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.Gabou.projectatmosphere.manager.AtmosphereWorldEffectsManager;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphereStatusSyncManager;
import net.Gabou.projectatmosphere.modules.wind.WindForces;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;


@EventBusSubscriber(modid = ProjectAtmosphere.MODID)
public class EventHandler {

    private static final int MIN_TICKS_BETWEEN_DUST_SPAWN = 5000;

    private static final int MIN_TICKS_BETWEEN_TEMPESTA = 2000;

    private static final int LIVING_WEATHER_EFFECT_INTERVAL_TICKS = 5;

    private static int tickCounter = 0;

    private static boolean hasDisplayedMessage = false;

    private static boolean wasRegenerating = false;

    private static boolean finishedRegenerating = true;

    private static int cloudBoosterTicks = 0;

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!AtmosphereManager.isInitialGenerationDone) {
            return;
        }
        if (serverLevel.players().isEmpty()) {
            return;
        }

        AtmosphereCloudService cloudService = AtmosphereCloudServices.get();
        CloudVisualBackend activeBackend = CloudBackendMigrationManager.tick(serverLevel, cloudService);
        if (activeBackend == CloudVisualBackend.PA_NATIVE) {
            CloudRegionManager.getInstance().tickCloudRegions(serverLevel);
            CloudFieldRuntimeManager.getInstance().tick(serverLevel);
            if (AtmoCommonConfig.CLOUD_VOLUMETRIC_RENDERER_ENABLED.get()) {
                CloudCellSimulationManager.getInstance().tick(serverLevel);
                NativeTornadoEffects.tick(serverLevel);
            }
        }

        if (!serverLevel.dimension().equals(Level.OVERWORLD)) return;

        boolean eventsEnabled = AtmoCommonConfig.EVENTS_ENABLED.get();
        if (eventsEnabled) {
            AtmosphereManager.tick(serverLevel);
            AtmosphereWorldEffectsManager.tick(serverLevel);
            if (ForecastOrchestrator.isRegenerating()) {
                finishedRegenerating = false;
            } else if (!finishedRegenerating) {
                wasRegenerating = true;
            }

            if (cloudService.shouldTrySpawn(serverLevel, cloudBoosterTicks, wasRegenerating)) {
                cloudService.trySpawnClouds(serverLevel);
                wasRegenerating = false;
                finishedRegenerating = true;
                cloudBoosterTicks = 0;
            }
        }

        AtmosphereStatusSyncManager.syncPlayers(serverLevel);
        if (activeBackend == CloudVisualBackend.PA_NATIVE) {
            CloudRegionSyncManager.syncPlayers(serverLevel);
            CloudFieldSyncManager.syncPlayers(serverLevel);
        }
        if(!serverLevel.players().isEmpty() && !hasDisplayedMessage) {
            hasDisplayedMessage = true;
            serverLevel.players().forEach(player -> {player.sendSystemMessage(Component.literal(ForecastGenerator.message) );});
        }


        if (eventsEnabled && AtmoCommonConfig.ENABLE_STORM_DEBRIS.get()) {
            if (tickCounter % MIN_TICKS_BETWEEN_TEMPESTA == 0) {
                cloudService.simulateSevereCloudDebris(serverLevel);
            }
            cloudBoosterTicks = cloudService.updateCloudBoosterTicks(serverLevel, cloudBoosterTicks);
        }

        tickCounter++;
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (level.dimension().equals(event.getTo())) {
            AtmosphereStatusSyncManager.syncPlayer(player);
            if (CloudBackendMigrationManager.status(level).currentBackend() == CloudVisualBackend.PA_NATIVE) {
                CloudRegionSyncManager.syncPlayer(player);
                CloudFieldSyncManager.syncPlayer(player);
                CloudCellSimulationManager.getInstance().syncPlayer(player);
            }

        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!AtmosphereManager.isInitialGenerationDone || ForecastOrchestrator.isRegenerating()) {
            return;
        }
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        WindForces.applyToPlayer(level, player, 1.0f);
    }

    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        if (entity.level().isClientSide) {
            return;
        }
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        if (!AtmosphereManager.isInitialGenerationDone || ForecastOrchestrator.isRegenerating()) {
            return;
        }
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        if ((entity.tickCount + entity.getId()) % LIVING_WEATHER_EFFECT_INTERVAL_TICKS != 0) {
            return;
        }
        AtmosphereWorldEffectsManager.applyCloudCoverEffects(level, entity);
        if (entity instanceof ServerPlayer) {
            return;
        }
        WindForces.applyToEntity(level, entity, LIVING_WEATHER_EFFECT_INTERVAL_TICKS);
    }

    public static void onRegenerate() {
        tickCounter = 0;
    }
}
