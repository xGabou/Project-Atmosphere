package net.Gabou.projectatmosphere.compat.simpleclouds;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudGenerator;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import dev.nonamecrackers2.simpleclouds.common.world.ServerCloudManager;
import net.Gabou.projectatmosphere.blocks.BlockManager;
import net.Gabou.projectatmosphere.clouds.backend.CloudBackendBridgeSnapshot;
import net.Gabou.projectatmosphere.clouds.backend.CloudBackendMigrationManager;
import net.Gabou.projectatmosphere.clouds.backend.CloudVisualBackend;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudService;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.event.SimpleCloudsEventListener;
import net.Gabou.projectatmosphere.manager.AtmosphereCloudRegionTracker;
import net.Gabou.projectatmosphere.manager.CloudRegionQueue;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Service compat Simple Clouds.
 * Cette classe ne doit être chargée que lorsque Simple Clouds est présent.
 */
public final class SimpleCloudsAtmosphereCloudService implements AtmosphereCloudService {

    private static boolean eventListenerRegistered;
    private static final int SNAPSHOT_CAPTURE_INTERVAL_TICKS = 200;

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
        SimpleCloudsTrackingIdentity.clear();
        SimpleCloudsRollbackDebugger.clear();
    }

    @Override
    public void onServerStopping(ServerLevel level) {
        CloudRegionQueue.clear();
        AtmosphereCloudRegionTracker.clear();
        SimpleCloudsTrackingIdentity.clear();
        SimpleCloudsRollbackDebugger.clear();
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
        if (tickCount % SNAPSHOT_CAPTURE_INTERVAL_TICKS == 0) {
            captureBridgeSnapshots(level);
        }
    }

    @Override
    public boolean shouldTrySpawn(ServerLevel level, int cloudBoosterTicks, boolean wasRegenerating) {
        return false;
    }

    @Override
    public void trySpawnClouds(ServerLevel level) {
    }

    @Override
    public int updateCloudBoosterTicks(ServerLevel level, int currentCloudBoosterTicks) {
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

    @Override
    public int mirrorPaNativeClouds(ServerLevel level, List<CloudBackendBridgeSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return 0;
        }
        if (CloudBackendMigrationManager.status(level).currentBackend() != CloudVisualBackend.SIMPLE_CLOUDS) {
            return 0;
        }

        int mirrored = 0;
        for (CloudBackendBridgeSnapshot snapshot : snapshots) {
            if (snapshot == null) {
                continue;
            }
            CloudRegion region = spawnMappedSimpleCloud(level, snapshot);
            if (region != null) {
                SimpleCloudsTrackingIdentity.Entry identity = SimpleCloudsTrackingIdentity.resolve(region, level);
                SimpleCloudsRollbackDebugger.markSimpleCloudWrite("mirror_pa_native_to_simple_clouds", identity.trackingKey(), level);
                mirrored++;
            }
        }
        return mirrored;
    }

    private void captureBridgeSnapshots(ServerLevel level) {
        CloudGenerator generator = getGenerator(level);
        if (generator == null) {
            return;
        }

        int cloudY = ((ServerCloudManager) CloudManager.get(level)).getCloudHeight();
        List<CloudBackendBridgeSnapshot> snapshots = new ArrayList<>();
        for (CloudRegion region : generator.getClouds()) {
            if (region == null || region.getCloudTypeId() == null) {
                continue;
            }
            SimpleCloudsTrackingIdentity.Entry identity = SimpleCloudsTrackingIdentity.resolve(region, level);
            int severity = CloudLibrary.getSeverityFromRessourceLocation(region.getCloudTypeId());
            float stormStrength = Math.max(0.0F, Math.min(1.0F, severity / 7.0F));
            CloudBackendBridgeSnapshot snapshot = new CloudBackendBridgeSnapshot(
                    "SIMPLE_CLOUDS",
                    region.getCloudTypeId().toString(),
                    "",
                    identity.trackingKey(),
                    region.getWorldX(),
                    cloudY,
                    region.getWorldZ(),
                    Math.max(32.0F, region.getWorldRadius()),
                    estimateHeight(severity, region.getCloudTypeId().getPath()),
                    estimateDensity(severity),
                    estimateCoverage(severity),
                    stormStrength,
                    level.getGameTime()
            );
            snapshots.add(snapshot);
            SimpleCloudsRollbackDebugger.logSevereCloudSample(level, region, snapshot, identity);
        }

        CloudBackendMigrationManager.captureSimpleCloudSnapshots(level, snapshots);
    }

    private CloudRegion spawnMappedSimpleCloud(ServerLevel level, CloudBackendBridgeSnapshot snapshot) {
        BlockPos anchor = BlockPos.containing(snapshot.x(), snapshot.y(), snapshot.z());
        RegionInstanceKey key = RegionInstanceKey.from(anchor);
        WindVector wind = ForecastOrchestrator.getWind(key, level.getGameTime());
        if (wind == null) {
            wind = WindVector.fromBase(1.0F, 0.0F);
        }

        for (String cloudId : mapPaSnapshotToSimpleCloudIds(snapshot)) {
            CloudRegion region = SimpleCloudsCompat.spawnCloudAt(cloudId, anchor, level, null, wind);
            if (region == null) {
                continue;
            }
            float worldRadius = (float) Math.max(64.0D, Math.min(2200.0D, snapshot.radius()));
            region.setWorldRadius(worldRadius);
            region.setRadius(Math.max(8, Math.round(worldRadius / 32.0F)));
            return region;
        }
        return null;
    }

    private List<String> mapPaSnapshotToSimpleCloudIds(CloudBackendBridgeSnapshot snapshot) {
        String type = snapshot.sourceTypeId() == null ? "" : snapshot.sourceTypeId().toLowerCase(Locale.ROOT);
        String morphology = snapshot.sourceMorphologyFamily() == null ? "" : snapshot.sourceMorphologyFamily().toLowerCase(Locale.ROOT);
        if (type.contains("cumulonimbus") || morphology.contains("storm_anvil")) {
            return List.of("severe_cumulonimbus", "custom_cumulonimbus", "cumulonimbus", "heavy_stratus", "cumulus");
        }
        if (type.contains("congestus") || morphology.contains("tower")) {
            return List.of("cumulus_congestus", "tall_noise", "dense_cumulus", "cumulus");
        }
        if (type.contains("stratocumulus") || morphology.contains("cellular")) {
            return List.of("stratocumulus", "dense_stratocumulus", "thicker_stratocumulus", "smaller_stratocumulus");
        }
        if (type.contains("stratus") || type.contains("nimbostratus") || morphology.contains("sheet")) {
            return List.of("stratus", "nimbostratus", "heavy_stratus", "overcast");
        }
        if (type.contains("cirrus") || morphology.contains("filament")) {
            return List.of("spots", "pathway", "spotted", "real_itty_bitty");
        }
        return List.of("small_cumulus", "cumulus", "cumulus_noise", "itty_bitty");
    }

    private static float estimateHeight(int severity, String path) {
        String id = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (severity >= 6 || id.contains("cumulonimbus")) {
            return 220.0F;
        }
        if (severity >= 4 || id.contains("congestus")) {
            return 140.0F;
        }
        if (id.contains("stratus")) {
            return 48.0F;
        }
        return 72.0F;
    }

    private static float estimateDensity(int severity) {
        return Math.max(0.35F, Math.min(1.0F, 0.35F + severity * 0.08F));
    }

    private static float estimateCoverage(int severity) {
        return Math.max(0.30F, Math.min(1.0F, 0.40F + severity * 0.07F));
    }

    private CloudGenerator getGenerator(ServerLevel level) {
        if (level == null) {
            return null;
        }
        return ((ServerCloudManager) CloudManager.get(level)).getCloudGenerator();
    }
}
