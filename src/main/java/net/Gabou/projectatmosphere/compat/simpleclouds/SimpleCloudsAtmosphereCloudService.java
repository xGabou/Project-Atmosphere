package net.Gabou.projectatmosphere.compat.simpleclouds;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudGenerator;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import dev.nonamecrackers2.simpleclouds.common.world.ServerCloudManager;
import net.Gabou.projectatmosphere.blocks.BlockManager;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudService;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.event.SimpleCloudsEventListener;
import net.Gabou.projectatmosphere.manager.AtmosphereCloudRegionTracker;
import net.Gabou.projectatmosphere.manager.CloudRegionQueue;
import net.Gabou.projectatmosphere.manager.SimpleCloudSpawner;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;

/**
 * Service compat Simple Clouds.
 * Cette classe ne doit être chargée que lorsque Simple Clouds est présent.
 */
public final class SimpleCloudsAtmosphereCloudService implements AtmosphereCloudService {

    private static boolean eventListenerRegistered;

    @Override
    public void onServerStarting(ServerLevel level) {
        SimpleCloudsCompat.configureConstants();
        SimpleCloudsCompat.init(level);

        if (!eventListenerRegistered) {
            MinecraftForge.EVENT_BUS.register(SimpleCloudsEventListener.class);
            eventListenerRegistered = true;
        }
    }

    @Override
    public void onServerStarted(ServerLevel level) {
        AtmosphereCloudRegionTracker.reset(level);
        CloudRegionQueue.clear();
    }

    @Override
    public void onServerStopping(ServerLevel level) {
        CloudRegionQueue.clear();
        AtmosphereCloudRegionTracker.clear();
    }

    @Override
    public void clearForRegeneration(ServerLevel level) {
        CloudManager.get(level).getCloudGenerator().removeAllClouds();
        AtmosphereCloudRegionTracker.clear();
        CloudRegionQueue.clear();
    }

    @Override
    public void tick(ServerLevel level, int tickCount) {
        if (tickCount % 20 == 0) {
            AtmosphereCloudRegionTracker.reconcile(level);
        }
        AtmosphereCloudRegionTracker.pollQueue(level);
    }

    @Override
    public boolean shouldTrySpawn(ServerLevel level, int cloudBoosterTicks, boolean wasRegenerating) {
        CloudGenerator generator = getGenerator(level);
        return generator != null && (generator.getTicksTillNextGen() - cloudBoosterTicks <= 0 || wasRegenerating);
    }

    @Override
    public void trySpawnClouds(ServerLevel level) {
        CloudGenerator generator = getGenerator(level);
        if (generator != null) {
            SimpleCloudSpawner.trySpawnClouds(level, generator);
        }
    }

    @Override
    public int updateCloudBoosterTicks(ServerLevel level, int currentCloudBoosterTicks) {
        CloudGenerator generator = getGenerator(level);
        if (generator != null && generator.getClouds().size() <= 3) {
            return currentCloudBoosterTicks + 5;
        }
        return currentCloudBoosterTicks;
    }

    @Override
    public void simulateSevereCloudDebris(ServerLevel level) {
        CloudGenerator generator = getGenerator(level);
        if (generator == null) {
            return;
        }

        int cloudY = ((ServerCloudManager) CloudManager.get(level)).getCloudHeight();
        for (CloudRegion region : generator.getClouds()) {
            int severity = CloudLibrary.getSeverityFromRessourceLocation(region.getCloudTypeId());
            if (severity > 5) {
                BlockPos pos = new BlockPos((int) region.getWorldX(), cloudY, (int) region.getWorldZ());
                BlockManager.simulateTempesta(level, pos, (int) region.getRadius());
            }
        }
    }

    @Override
    public void ensureCloudAtPosition(BlockPos pos, ServerLevel level) {
        SimpleCloudsCompat.ensureCloudAtPosition(pos, level);
    }

    @Override
    public boolean hasSevereCloudNearby(ServerLevel level, BlockPos pos, int minimumSeverity) {
        CloudGenerator generator = getGenerator(level);
        if (generator == null || pos == null) {
            return false;
        }

        double radiusSq = 500.0D * 500.0D;
        for (CloudRegion region : generator.getClouds()) {
            int severity = CloudLibrary.getSeverityFromRessourceLocation(region.getCloudTypeId());
            if (severity < minimumSeverity) {
                continue;
            }
            double dx = region.getWorldX() - pos.getX();
            double dz = region.getWorldZ() - pos.getZ();
            if (dx * dx + dz * dz <= radiusSq) {
                return true;
            }
        }
        return false;
    }

    private CloudGenerator getGenerator(ServerLevel level) {
        if (level == null) {
            return null;
        }
        return ((ServerCloudManager) CloudManager.get(level)).getCloudGenerator();
    }
}
