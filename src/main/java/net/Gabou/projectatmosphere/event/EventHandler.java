package net.Gabou.projectatmosphere.event;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudGenerator;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import dev.nonamecrackers2.simpleclouds.common.world.ServerCloudManager;
import net.Gabou.projectatmosphere.blocks.BlockManager;
import net.Gabou.projectatmosphere.compat.CompatHandler;
import net.Gabou.projectatmosphere.compat.rainbows.RainbowRainBridge;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.Gabou.projectatmosphere.manager.AtmosphereWorldEffectsManager;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.manager.SimpleCloudSpawner;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.modules.wind.WindForces;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class EventHandler {
    private static final int MIN_TICKS_BETWEEN_TEMPESTA = 2000;
    private static int tickCounter = 0;

    private static boolean finishedRegenerating = true;
    private static boolean wasRegenerating = false;
    private static int cloudBoosterTicks = 0;

    private EventHandler() { }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide || !(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!AtmosphereManager.isInitialGenerationDone) {
            return;
        }
        if (serverLevel.players().isEmpty()) {
            return;
        }
        if (!serverLevel.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        // Daily profile swap check
        long t = serverLevel.getDayTime() % 24000L;
        if (t == 21000L) {
            AtmosphereManager.onSwapProfiles(serverLevel);
        }

        ServerCloudManager cloudManager = (ServerCloudManager) CloudManager.get(serverLevel);
        CloudGenerator generator = cloudManager.getCloudGenerator();

        AtmosphereManager.tick(serverLevel);
        AtmosphereWorldEffectsManager.tick(serverLevel);

        if (ForecastOrchestrator.isRegenerating()) {
            finishedRegenerating = false;
        } else if (!finishedRegenerating) {
            wasRegenerating = true;
        }

        if (generator.getTicksTillNextGen() - cloudBoosterTicks <= 0 || wasRegenerating) {
            SimpleCloudSpawner.trySpawnClouds(serverLevel, generator);
            wasRegenerating = false;
            finishedRegenerating = true;
            cloudBoosterTicks = 0;
        }

        if (CompatHandler.isRainbowsLoaded()) {
            RainbowRainBridge.sync(serverLevel, generator);
        }

        if (AtmoCommonConfig.ENABLE_STORM_DEBRIS.get()
                && tickCounter % MIN_TICKS_BETWEEN_TEMPESTA == 0) {
            int cloudY = cloudManager.getCloudHeight();
            for (CloudRegion region : generator.getClouds()) {
                int severity = CloudLibrary.getSeverityFromRessourceLocation(region.getCloudTypeId());
                if (severity > 5) {
                    BlockPos pos = new BlockPos((int) region.getWorldX(), cloudY, (int) region.getWorldZ());
                    BlockManager.simulateTempesta(serverLevel, pos, (int) region.getRadius());
                }
            }
        }

        if (generator.getClouds().size() <= 3) {
            cloudBoosterTicks += 5;
        }

        tickCounter++;
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (CompatHandler.isRainbowsLoaded() && level.dimension().equals(event.getTo())) {
            ServerCloudManager cloudManager = (ServerCloudManager) CloudManager.get(level);
            RainbowRainBridge.sendSnapshot(player, level, cloudManager.getCloudGenerator());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().isClientSide) {
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
    public static void onEntityTick(EntityTickEvent.Post event) {
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
        AtmosphereWorldEffectsManager.applyCloudCoverEffects(level, entity);
        if (entity instanceof ServerPlayer) {
            return;
        }
        WindForces.applyToEntity(level, entity, 1.0f);
    }

    public static void onRegenerate() {
        tickCounter = 0;
        finishedRegenerating = true;
        wasRegenerating = false;
        cloudBoosterTicks = 0;
    }
}
