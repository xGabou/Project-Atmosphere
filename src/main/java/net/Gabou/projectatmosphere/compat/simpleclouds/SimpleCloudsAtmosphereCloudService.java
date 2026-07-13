package net.Gabou.projectatmosphere.compat.simpleclouds;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudGenerator;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import dev.nonamecrackers2.simpleclouds.common.world.ServerCloudManager;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.blocks.BlockManager;
import net.Gabou.projectatmosphere.clouds.backend.CloudBackendBridgeSnapshot;
import net.Gabou.projectatmosphere.clouds.backend.CloudBackendMigrationManager;
import net.Gabou.projectatmosphere.clouds.backend.CloudVisualBackend;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudService;
import net.Gabou.projectatmosphere.clouds.service.OptionalCloudQueries;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.event.SimpleCloudsEventListener;
import net.Gabou.projectatmosphere.manager.AtmosphereCloudRegionTracker;
import net.Gabou.projectatmosphere.manager.CloudRegionQueue;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.Gabou.projectatmosphere.modules.tornado.TornadoInstance;
import net.Gabou.projectatmosphere.modules.tornado.TornadoSnapshot;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneManager;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneInstance;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneRenderSnapshot;
import net.Gabou.projectatmosphere.telemetry.SevereWeatherArchiveBridge;
import net.Gabou.projectatmosphere.telemetry.ServerStateArchiveWriter.HurricaneExport;
import net.Gabou.projectatmosphere.telemetry.ServerStateArchiveWriter.TornadoExport;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.Comparator;
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
        OptionalCloudQueries.install(SimpleCloudsCompat::isCloudAtPos, SimpleCloudsCompat::isRainningAt);
        OptionalCloudQueries.installSeverity(SimpleCloudsCompat::sampleSeverityAt);
        SevereWeatherArchiveBridge.install(
                SimpleCloudsAtmosphereCloudService::captureTornadoesForArchive,
                SimpleCloudsAtmosphereCloudService::captureHurricanesForArchive
        );
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
        clearSevereWeather(level);
        OptionalCloudQueries.reset();
        SevereWeatherArchiveBridge.reset();
        CloudRegionQueue.clear();
        AtmosphereCloudRegionTracker.clear();
        SimpleCloudsTrackingIdentity.clear();
        SimpleCloudsRollbackDebugger.clear();
    }

    @Override
    public void clearForRegeneration(ServerLevel level) {
        if (level == null) {
            return;
        }
        if (!level.getServer().isSameThread()) {
            ProjectAtmosphere.LOGGER.warn(
                    "[SimpleCloudsCompat] clearForRegeneration entered from {}; scheduling cloud removal on the server thread.",
                    Thread.currentThread().getName()
            );
            level.getServer().execute(() -> clearForRegenerationOnServerThread(level));
            return;
        }
        clearForRegenerationOnServerThread(level);
    }

    @Override
    public void tickSevereWeather(ServerLevel level) {
        TornadoManager.tick(level);
        HurricaneManager.tick(level);
    }

    @Override
    public void clearSevereWeather(ServerLevel level) {
        TornadoManager.clearTornadoes();
        HurricaneManager.clearHurricanes(level);
    }

    @Override
    public void syncSevereWeather(net.minecraft.server.level.ServerPlayer player) {
        TornadoManager.syncToPlayer(player);
        HurricaneManager.syncToPlayer(player);
    }

    @Override
    public int activeTornadoCount() {
        return TornadoManager.getActiveTornadoes().size();
    }

    @Override
    public int activeHurricaneCount() {
        return HurricaneManager.getActiveHurricanes().size();
    }

    @Override
    public void initializeForecastClouds(ServerLevel level) {
        net.Gabou.projectatmosphere.modules.atmosphere.CloudManager.initialize(level);
    }

    @Override
    public void updateForecastClouds(ServerLevel level) {
        net.Gabou.projectatmosphere.modules.atmosphere.CloudManager.update(level);
    }

    @Override
    public boolean hasActiveTornadoNear(ServerLevel level, BlockPos pos, double radius) {
        if (level == null || pos == null || radius <= 0.0D) {
            return false;
        }
        double radiusSq = radius * radius;
        return TornadoManager.getActiveTornadoes().stream().anyMatch(tornado -> {
            double dx = tornado.position.x - pos.getX();
            double dz = tornado.position.z - pos.getZ();
            return dx * dx + dz * dz <= radiusSq;
        });
    }

    @Override
    public void loadSevereWeather(
            ServerLevel level,
            List<net.minecraft.nbt.CompoundTag> tornadoes,
            List<net.minecraft.nbt.CompoundTag> hurricanes
    ) {
        if (net.Gabou.projectatmosphere.config.AtmoCommonConfig.ENABLE_TORNADOES.get()) {
            TornadoManager.loadPersistentTornadoes(level, tornadoes);
        } else {
            TornadoManager.clearTornadoes();
        }
        HurricaneManager.loadPersistentHurricanes(level, hurricanes);
    }

    @Override
    public void saveSevereWeather(
            List<net.minecraft.nbt.CompoundTag> tornadoes,
            List<net.minecraft.nbt.CompoundTag> hurricanes
    ) {
        if (net.Gabou.projectatmosphere.config.AtmoCommonConfig.ENABLE_TORNADOES.get()) {
            tornadoes.addAll(TornadoManager.savePersistentTornadoes());
        }
        hurricanes.addAll(HurricaneManager.savePersistentHurricanes());
    }

    @Override
    public boolean spawnExternalCloud(
            ServerLevel level,
            String cloudId,
            RegionInstanceKey regionKey,
            WindVector wind
    ) {
        return SimpleCloudsCompat.spawnCloudInRegion(cloudId, regionKey, level, null, wind) != null;
    }

    private void clearForRegenerationOnServerThread(ServerLevel level) {
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

    private static List<TornadoExport> captureTornadoesForArchive() {
        List<TornadoExport> out = new ArrayList<>();
        for (TornadoInstance tornado : TornadoManager.getActiveTornadoes()) {
            if (tornado == null) {
                continue;
            }
            TornadoSnapshot snapshot = tornado.snapshot();
            out.add(new TornadoExport(
                    snapshot.id().toString(),
                    snapshot.position().x,
                    snapshot.position().y,
                    snapshot.position().z,
                    snapshot.radius(),
                    snapshot.visualBottomY(),
                    snapshot.visualHeight(),
                    snapshot.windSpeed(),
                    snapshot.windAngle(),
                    snapshot.windGust(),
                    snapshot.normalizedIntensity(),
                    snapshot.stormLevel(),
                    snapshot.recentDebrisScore(),
                    snapshot.formationProgress(),
                    snapshot.phase().name(),
                    tornado.toPersistentTag().toString()
            ));
        }
        out.sort(Comparator.comparing(TornadoExport::id));
        return out;
    }

    private static List<HurricaneExport> captureHurricanesForArchive() {
        List<HurricaneExport> out = new ArrayList<>();
        for (HurricaneInstance hurricane : HurricaneManager.getActiveHurricanes()) {
            if (hurricane == null) {
                continue;
            }
            HurricaneRenderSnapshot snapshot = hurricane.createRenderSnapshot();
            out.add(new HurricaneExport(
                    snapshot.id().toString(),
                    snapshot.centerX(),
                    snapshot.centerZ(),
                    snapshot.anchorY(),
                    snapshot.coreRadius(),
                    snapshot.stormExtentRadius(),
                    snapshot.eyeRadius(),
                    snapshot.edgeFade(),
                    snapshot.bandCount(),
                    snapshot.bandWidth(),
                    snapshot.spiralTightness(),
                    snapshot.rotationPhase(),
                    snapshot.rotationSpeed(),
                    snapshot.transitionStart(),
                    snapshot.transitionEnd(),
                    snapshot.normalizedIntensity(),
                    snapshot.cloudTypeId().toString(),
                    snapshot.ageTicks(),
                    hurricane.toPersistentTag().toString()
            ));
        }
        out.sort(Comparator.comparing(HurricaneExport::id));
        return out;
    }

    private CloudGenerator getGenerator(ServerLevel level) {
        if (level == null) {
            return null;
        }
        return ((ServerCloudManager) CloudManager.get(level)).getCloudGenerator();
    }
}
