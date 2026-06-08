package net.Gabou.projectatmosphere.event;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.backend.CloudRegionManager;
import net.Gabou.projectatmosphere.clouds.backend.CloudRegionSyncManager;
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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID)
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
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide || !(event.level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!AtmosphereManager.isInitialGenerationDone) {
            return;
        }
        if (serverLevel.players().isEmpty()) {
            return;
        }

        CloudRegionManager.getInstance().tickCloudRegions(serverLevel);

        if (!serverLevel.dimension().equals(Level.OVERWORLD)) return;

        AtmosphereCloudService cloudService = AtmosphereCloudServices.get();
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
        CloudRegionSyncManager.syncPlayers(serverLevel);
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
            CloudRegionSyncManager.syncPlayer(player);

        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side.isClient()) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
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
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
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
