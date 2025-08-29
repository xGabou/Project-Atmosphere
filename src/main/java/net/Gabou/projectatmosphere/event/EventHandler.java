package net.Gabou.projectatmosphere.event;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudGenerator;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import dev.nonamecrackers2.simpleclouds.common.world.ServerCloudManager;
import net.Gabou.projectatmosphere.blocks.BlockManager;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.Gabou.projectatmosphere.manager.SimpleCloudSpawner;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public class EventHandler {

    private static final int MIN_TICKS_BETWEEN_DUST_SPAWN = 5000;
    private static final int MIN_TICKS_BETWEEN_TEMPESTA = 2000;

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide
                || !(event.getLevel() instanceof ServerLevel serverLevel)) {
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

        // ✅ Daily profile swap check
        long t = serverLevel.getDayTime() % 24000L;
        if (t == 21000L) {
            AtmosphereManager.onSwapProfiles(serverLevel);
        }

        // Cloud/weather tick logic
        ServerCloudManager cloudManager = (ServerCloudManager) CloudManager.get(serverLevel);
        CloudGenerator generator = cloudManager.getCloudGenerator();
        AtmosphereManager.tick(serverLevel);

        if (generator.getTicksTillNextGen() <= 0) {
            SimpleCloudSpawner.trySpawnClouds(serverLevel, generator);
        }

        if (!AtmoCommonConfig.ENABLE_STORM_DEBRIS.get()) {
            return;
        } else if (tickCounter % MIN_TICKS_BETWEEN_TEMPESTA == 0) {
            final int cloudY = cloudManager.getCloudHeight();
            for (CloudRegion region : generator.getClouds()) {
                int severity = CloudLibrary.getSeverityFromRessourceLocation(region.getCloudTypeId());
                if (severity > 5) {
                    BlockPos pos = new BlockPos((int) region.getWorldX(), cloudY, (int) region.getWorldZ());
                    BlockManager.simulateTempesta(serverLevel, pos, (int) region.getRadius());
                }
            }
        }

        tickCounter++;
    }

    public static void onRegenerate() {
        tickCounter = 0;
    }
}
