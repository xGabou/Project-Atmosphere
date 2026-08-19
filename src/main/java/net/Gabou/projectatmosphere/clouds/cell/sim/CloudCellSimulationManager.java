package net.Gabou.projectatmosphere.clouds.cell.sim;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.analytics.CloudCellAnalyticsReport;
import net.Gabou.projectatmosphere.clouds.api.CloudDensityQuery;
import net.Gabou.projectatmosphere.clouds.cell.CloudCell;
import net.Gabou.projectatmosphere.clouds.cell.CloudCellClassification;
import net.Gabou.projectatmosphere.clouds.cell.CloudCellDensityMath;
import net.Gabou.projectatmosphere.clouds.cell.CloudCellLifecyclePhase;
import net.Gabou.projectatmosphere.clouds.cell.network.CloudCellDeltaPacket;
import net.Gabou.projectatmosphere.clouds.cell.network.SyncCloudCellsPacket;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldSnapshot;
import net.Gabou.projectatmosphere.clouds.field.runtime.CloudFieldRuntimeManager;
import net.Gabou.projectatmosphere.platform.config.AtmosphereConfig;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.platform.network.AtmosphereNetwork;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CPU-authoritative cloud cell simulation. Owns cell identity, lifecycle
 * (form / mature / dissipate), altitude-sheared wind motion, merge/split with
 * hysteresis, shape-derived classification, and delta network sync.
 *
 * GPU analytics digests (from clients) are advisory evidence only: they can
 * accelerate a merge/split decision that CPU-side analytic checks already
 * consider plausible, but they never create, move, or destroy cells.
 */
public final class CloudCellSimulationManager {
    private static final CloudCellSimulationManager INSTANCE = new CloudCellSimulationManager();

    private static final int MAX_CELLS_PER_DIMENSION = 96;
    private static final int SIM_INTERVAL_TICKS = 10;
    private static final int MERGE_INTERVAL_TICKS = 30;
    private static final int DELTA_SYNC_INTERVAL_TICKS = 10;
    private static final int FULL_SYNC_INTERVAL_TICKS = 600;
    private static final int CLASSIFY_HYSTERESIS_TICKS = 100;
    private static final int MERGE_STREAK_REQUIRED = 3;
    private static final int SPLIT_STREAK_REQUIRED = 3;
    private static final float MERGE_SCORE_THRESHOLD = 0.52F;
    private static final double PLAYER_INTEREST_RADIUS = 3200.0D;
    private static final long ANALYTICS_MIN_INTERVAL_TICKS = 10L;
    private static final long ANALYTICS_STALE_TICKS = 80L;

    private final Map<String, DimensionSim> sims = new ConcurrentHashMap<>();

    private CloudCellSimulationManager() {
        CloudDensityQuery.setServerProvider((level, x, y, z) -> {
            DimensionSim sim = sims.get(level.dimension().location().toString());
            if (sim == null) {
                return 0.0F;
            }
            return CloudCellDensityMath.densityAt(sim.snapshotCells(), x, y, z);
        });
    }

    public static CloudCellSimulationManager getInstance() {
        return INSTANCE;
    }

    /** Immutable view of the live cells for a level, for commands/queries. */
    public List<CloudCell> cells(ServerLevel level) {
        DimensionSim sim = sims.get(dimensionId(level));
        return sim == null ? List.of() : sim.snapshotCells();
    }

    public void clear(ServerLevel level) {
        DimensionSim sim = sims.remove(dimensionId(level));
        if (sim != null) {
            broadcastFull(level, List.of());
        }
    }

    /** Full snapshot for a newly joined / dimension-changed player. */
    public void syncPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        DimensionSim sim = sims.get(dimensionId(level));
        if (sim == null) {
            AtmosphereNetwork.sendToPlayer(
                    player,
                    new SyncCloudCellsPacket(List.of(), level.getGameTime()));
        } else {
            sim.sendFull(player);
        }
    }

    public void forgetPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        for (DimensionSim sim : sims.values()) {
            sim.forgetPlayer(playerId);
        }
    }

    public void tick(ServerLevel level) {
        if (level == null || level.players().isEmpty()) {
            return;
        }
        DimensionSim sim = sims.computeIfAbsent(dimensionId(level), id -> new DimensionSim(id));
        List<CloudFieldSnapshot> fields = CloudFieldRuntimeManager.getInstance()
                .ensureCurrent(level)
                .fields();
        sim.tick(level, fields);
    }

    /** Returns currently active native funnel cells for commands and physics. */
    public List<CloudCell> nativeTornadoCells(ServerLevel level) {
        DimensionSim sim = sims.get(dimensionId(level));
        return sim == null ? List.of() : sim.nativeTornadoCells();
    }

    /**
     * Activates the nearest eligible simulated cumulonimbus. This is used by
     * the scheduled native tornado path and never fabricates a second weather
     * simulation.
     */
    public boolean activateNativeTornado(
            ServerLevel level, Vec3 position, float radius, WindVector wind, int stormLevel
    ) {
        if (level == null || position == null) {
            return false;
        }
        DimensionSim sim = sims.get(dimensionId(level));
        return sim != null && sim.activateEligibleFunnel(level, position, radius, wind, stormLevel);
    }

    /** Creates a command/debug native cumulonimbus with a real funnel state. */
    public boolean spawnNativeTornado(
            ServerLevel level, Vec3 position, float radius, WindVector wind, int stormLevel
    ) {
        if (level == null || position == null) {
            return false;
        }
        DimensionSim sim = sims.computeIfAbsent(dimensionId(level), DimensionSim::new);
        return sim.spawnForcedFunnel(level, position, radius, wind, stormLevel);
    }

    public boolean dissipateNearestNativeTornado(ServerLevel level, Vec3 position, double maxDistance) {
        if (level == null || position == null) {
            return false;
        }
        DimensionSim sim = sims.get(dimensionId(level));
        return sim != null && sim.dissipateNearestFunnel(level, position, maxDistance);
    }

    public int clearNativeTornadoes(ServerLevel level) {
        if (level == null) {
            return 0;
        }
        DimensionSim sim = sims.get(dimensionId(level));
        return sim == null ? 0 : sim.clearFunnels(level);
    }

    public boolean hasEligibleNativeTornadoCellNear(ServerLevel level, BlockPos position, double radius) {
        if (level == null || position == null) {
            return false;
        }
        DimensionSim sim = sims.get(dimensionId(level));
        return sim != null && sim.hasEligibleCellNear(position, radius, level.getGameTime());
    }

    /** Accepts advisory GPU analytics evidence from a client. */
    public void acceptAnalytics(ServerPlayer sender, List<CloudCellAnalyticsReport> reports) {
        if (sender == null || reports == null || reports.isEmpty()) {
            return;
        }
        ServerLevel level = sender.serverLevel();
        DimensionSim sim = sims.get(dimensionId(level));
        if (sim != null) {
            sim.acceptAnalytics(sender.getUUID(), reports, level.getGameTime());
        }
    }

    private static String dimensionId(ServerLevel level) {
        return level.dimension().location().toString();
    }

    private static void broadcastFull(ServerLevel level, List<CloudCell> cells) {
        SyncCloudCellsPacket packet = new SyncCloudCellsPacket(cells, level.getGameTime());
        for (ServerPlayer player : level.players()) {
            AtmosphereNetwork.sendToPlayer(player, packet);
        }
    }

    // =================================================================
    // Per-dimension simulation
    // =================================================================

    private static final class DimensionSim {
        private final String dimensionId;
        private final Map<UUID, MutableCell> cells = new LinkedHashMap<>();
        private final Map<UUID, MergeEvidence> mergeEvidence = new HashMap<>();
        private final Map<UUID, Integer> splitStreaks = new HashMap<>();
        private final Map<UUID, PendingClassification> pendingClassifications = new HashMap<>();
        private final Map<UUID, CloudCellAnalyticsReport> gpuEvidence = new HashMap<>();
        private final Map<UUID, Long> gpuEvidenceTime = new HashMap<>();
        private final Map<UUID, Long> analyticsSenderLastAccept = new HashMap<>();
        private final Map<UUID, PlayerCellSyncState> playerSyncStates = new HashMap<>();
        private final Set<UUID> removedSinceSync = new HashSet<>();
        private long lastFullSyncTime = Long.MIN_VALUE;
        private volatile List<CloudCell> snapshot = List.of();

        private DimensionSim(String dimensionId) {
            this.dimensionId = dimensionId;
        }

        private synchronized List<CloudCell> snapshotCells() {
            return snapshot;
        }

        private synchronized List<CloudCell> nativeTornadoCells() {
            List<CloudCell> active = new ArrayList<>();
            for (CloudCell cell : snapshot) {
                if (cell.funnelStrength() > 0.02F) {
                    active.add(cell);
                }
            }
            return List.copyOf(active);
        }

        private synchronized boolean hasEligibleCellNear(BlockPos position, double radius, long gameTime) {
            double radiusSq = Math.max(1.0D, radius * radius);
            for (MutableCell cell : cells.values()) {
                double dx = cell.x - position.getX();
                double dz = cell.z - position.getZ();
                if (dx * dx + dz * dz > radiusSq) {
                    continue;
                }
                CloudCell snapshot = cell.toCell(gameTime);
                if (CloudCellClassifier.isTornadoEligible(snapshot, CloudCellClassifier.classify(snapshot))) {
                    return true;
                }
            }
            return false;
        }

        private synchronized boolean activateEligibleFunnel(
                ServerLevel level, Vec3 position, float requestedRadius, WindVector wind, int stormLevel
        ) {
            MutableCell closest = null;
            double bestDistance = Double.MAX_VALUE;
            long gameTime = level.getGameTime();
            double searchRadius = Math.max(160.0D, requestedRadius * 18.0D);
            double searchRadiusSq = searchRadius * searchRadius;
            for (MutableCell cell : cells.values()) {
                double dx = cell.x - position.x;
                double dz = cell.z - position.z;
                double distance = dx * dx + dz * dz;
                if (distance > searchRadiusSq || distance >= bestDistance) {
                    continue;
                }
                CloudCell snapshot = cell.toCell(gameTime);
                if (!CloudCellClassifier.isTornadoEligible(snapshot, CloudCellClassifier.classify(snapshot))) {
                    continue;
                }
                closest = cell;
                bestDistance = distance;
            }
            if (closest == null) {
                return false;
            }
            configureFunnel(level, closest, requestedRadius, wind, stormLevel, 0.12F);
            rebuildSnapshot(gameTime);
            sendFull(level);
            return true;
        }

        private synchronized boolean spawnForcedFunnel(
                ServerLevel level, Vec3 position, float requestedRadius, WindVector wind, int stormLevel
        ) {
            if (cells.size() >= MAX_CELLS_PER_DIMENSION) {
                return false;
            }
            MutableCell cell = new MutableCell(UUID.randomUUID(), level.random.nextLong(), dimensionId);
            float radius = Mth.clamp(Math.max(24.0F, requestedRadius * 7.0F), 24.0F, 280.0F);
            int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Mth.floor(position.x), Mth.floor(position.z));
            cell.x = position.x;
            cell.z = position.z;
            cell.baseY = Math.max(surfaceY + 44.0F, level.getSeaLevel() + 28.0F);
            cell.topY = cell.baseY + Math.max(165.0F, radius * 1.05F);
            cell.radiusMajor = radius;
            cell.radiusMinor = radius * 0.78F;
            cell.targetRadiusMajor = cell.radiusMajor;
            cell.targetRadiusMinor = cell.radiusMinor;
            cell.targetVerticalExtent = cell.topY - cell.baseY;
            cell.density = 0.82F;
            cell.targetDensity = cell.density;
            cell.energy = 0.92F;
            cell.rotation = 0.86F;
            cell.edgeSoftness = 0.58F;
            cell.phase = CloudCellLifecyclePhase.MATURE;
            cell.classification = CloudCellClassification.CUMULONIMBUS;
            cell.ageTicks = cell.formationTicks;
            cell.lifetimeTicks = Math.max(cell.lifetimeTicks, 18_000);
            configureFunnel(level, cell, requestedRadius, wind, stormLevel, 0.18F);
            cells.put(cell.id, cell);
            rebuildSnapshot(level.getGameTime());
            sendFull(level);
            return true;
        }

        private synchronized boolean dissipateNearestFunnel(ServerLevel level, Vec3 position, double maxDistance) {
            MutableCell closest = null;
            double bestDistance = Math.max(1.0D, maxDistance * maxDistance);
            for (MutableCell cell : cells.values()) {
                if (cell.funnelStrength <= 0.02F) {
                    continue;
                }
                double dx = cell.x - position.x;
                double dz = cell.z - position.z;
                double distance = dx * dx + dz * dz;
                if (distance <= bestDistance) {
                    closest = cell;
                    bestDistance = distance;
                }
            }
            if (closest == null) {
                return false;
            }
            closest.funnelStrength = 0.0F;
            closest.dirty = true;
            rebuildSnapshot(level.getGameTime());
            sendFull(level);
            return true;
        }

        private synchronized int clearFunnels(ServerLevel level) {
            int cleared = 0;
            for (MutableCell cell : cells.values()) {
                if (cell.funnelStrength > 0.0F) {
                    cell.funnelStrength = 0.0F;
                    cell.dirty = true;
                    cleared++;
                }
            }
            if (cleared > 0) {
                rebuildSnapshot(level.getGameTime());
                sendFull(level);
            }
            return cleared;
        }

        private static void configureFunnel(
                ServerLevel level, MutableCell cell, float requestedRadius, WindVector wind, int stormLevel, float initialStrength
        ) {
            cell.funnelStrength = Math.max(cell.funnelStrength, Mth.clamp(initialStrength, 0.04F, 1.0F));
            cell.funnelGroundY = Math.max(
                    level.getMinBuildHeight(),
                    level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            Mth.floor(cell.x), Mth.floor(cell.z)) - 1.0F
            );
            if (wind != null) {
                float drift = Mth.clamp(wind.baseSpeed() * 0.05F, 0.0F, 0.45F);
                cell.windX = (float) Math.cos(wind.angleRadians()) * drift;
                cell.windZ = (float) Math.sin(wind.angleRadians()) * drift;
            }
            cell.rotation = Math.max(cell.rotation, Mth.clamp(0.55F + stormLevel * 0.05F, 0.60F, 1.0F));
            cell.energy = Math.max(cell.energy, Mth.clamp(0.74F + stormLevel * 0.03F, 0.78F, 1.0F));
            cell.dirty = true;
        }

        private synchronized void acceptAnalytics(UUID sender, List<CloudCellAnalyticsReport> reports, long gameTime) {
            Long last = analyticsSenderLastAccept.get(sender);
            if (last != null && gameTime - last < ANALYTICS_MIN_INTERVAL_TICKS) {
                return;
            }
            analyticsSenderLastAccept.put(sender, gameTime);
            for (CloudCellAnalyticsReport report : reports) {
                if (report == null || !cells.containsKey(report.cellId())) {
                    continue;
                }
                gpuEvidence.put(report.cellId(), report);
                gpuEvidenceTime.put(report.cellId(), gameTime);
            }
        }

        private synchronized void tick(ServerLevel level, List<CloudFieldSnapshot> fields) {
            long gameTime = level.getGameTime();
            boolean frozen = AtmosphereConfig.clouds().freezeCloudMovement();
            reconcileDerivedFields(level, fields, gameTime);

            // Motion every tick: keeps client extrapolation honest.
            if (!frozen && AtmosphereConfig.clouds().cloudMovementEnabled()) {
                for (MutableCell cell : cells.values()) {
                    if (!cell.derivedFromField) {
                        cell.x += cell.windX;
                        cell.z += cell.windZ;
                    }
                }
            }

            if (gameTime % SIM_INTERVAL_TICKS == 0L) {
                refreshWinds(level, gameTime);
                stepLifecycles(level, gameTime);
            }
            if (gameTime % MERGE_INTERVAL_TICKS == 0L) {
                evaluateMerges(gameTime);
                evaluateSplits(level, gameTime);
                expireGpuEvidence(gameTime);
            }
            if (gameTime % CLASSIFY_HYSTERESIS_TICKS == 0L) {
                reclassify(gameTime);
            }

            rebuildSnapshot(gameTime);

            if (gameTime - lastFullSyncTime >= FULL_SYNC_INTERVAL_TICKS) {
                lastFullSyncTime = gameTime;
                removedSinceSync.clear();
                for (MutableCell cell : cells.values()) {
                    cell.dirty = false;
                }
                sendFull(level);
            } else if (gameTime % DELTA_SYNC_INTERVAL_TICKS == 0L) {
                sendDelta(level, gameTime);
            }
        }

        /**
         * Reconciles the severe-convection cell view from the render-authority
         * fields. Normal cells no longer spawn as a second weather population;
         * only explicit command/debug cells remain autonomous.
         */
        private void reconcileDerivedFields(
                ServerLevel level,
                List<CloudFieldSnapshot> snapshots,
                long gameTime
        ) {
            List<CloudFieldSnapshot> candidates = new ArrayList<>();
            if (snapshots != null) {
                for (CloudFieldSnapshot snapshot : snapshots) {
                    if (snapshot != null && snapshot.hasVisibleClouds()
                            && dimensionId.equals(snapshot.dimensionId())) {
                        candidates.add(snapshot);
                    }
                }
            }
            candidates.sort((left, right) -> {
                int distance = Double.compare(
                        distanceToNearestPlayerSqr(level, left),
                        distanceToNearestPlayerSqr(level, right)
                );
                return distance != 0 ? distance : left.fieldId().compareTo(right.fieldId());
            });

            int autonomousCount = 0;
            for (MutableCell cell : cells.values()) {
                if (!cell.derivedFromField) {
                    autonomousCount++;
                }
            }
            int derivedLimit = Math.max(0, MAX_CELLS_PER_DIMENSION - autonomousCount);
            Set<UUID> retained = new HashSet<>();
            for (CloudFieldSnapshot snapshot : candidates) {
                if (retained.size() >= derivedLimit) {
                    break;
                }
                UUID id = snapshot.fieldId();
                MutableCell cell = cells.get(id);
                if (cell != null && !cell.derivedFromField) {
                    continue;
                }
                if (cell == null) {
                    cell = new MutableCell(id, snapshot.seed(), dimensionId);
                    cell.derivedFromField = true;
                    cell.funnelGroundY = level.getSeaLevel();
                    cells.put(id, cell);
                }
                retained.add(id);
                updateFromField(cell, snapshot, gameTime);
            }

            Iterator<Map.Entry<UUID, MutableCell>> iterator = cells.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, MutableCell> entry = iterator.next();
                MutableCell cell = entry.getValue();
                if (cell.derivedFromField && !retained.contains(entry.getKey())) {
                    iterator.remove();
                    noteRemoved(entry.getKey());
                }
            }
        }

        private static double distanceToNearestPlayerSqr(ServerLevel level, CloudFieldSnapshot snapshot) {
            double best = Double.POSITIVE_INFINITY;
            for (ServerPlayer player : level.players()) {
                double dx = snapshot.center().x() - player.getX();
                double dz = snapshot.center().z() - player.getZ();
                best = Math.min(best, dx * dx + dz * dz);
            }
            return best;
        }

        private static void updateFromField(
                MutableCell cell,
                CloudFieldSnapshot snapshot,
                long gameTime
        ) {
            float density = snapshot.effectiveDensity();
            float radiusMajor = Math.max(4.0F, snapshot.radius());
            float radiusMinor = Math.max(4.0F, radiusMajor * (0.68F + snapshot.effectiveCoverage() * 0.22F));
            CloudCellLifecyclePhase phase = snapshot.decay() > 0.62F
                    ? CloudCellLifecyclePhase.DISSIPATING
                    : (snapshot.growth() < 0.82F
                            ? CloudCellLifecyclePhase.FORMING
                            : CloudCellLifecyclePhase.MATURE);
            float rotation = Math.max(
                    cell.funnelStrength > 0.0F ? cell.rotation : 0.0F,
                    snapshot.stormPotential() * 0.86F
            );

            boolean changed = materiallyDifferent(cell.x, snapshot.center().x())
                    || materiallyDifferent(cell.z, snapshot.center().z())
                    || materiallyDifferent(cell.baseY, snapshot.baseY())
                    || materiallyDifferent(cell.topY, snapshot.topY())
                    || materiallyDifferent(cell.radiusMajor, radiusMajor)
                    || materiallyDifferent(cell.radiusMinor, radiusMinor)
                    || materiallyDifferent(cell.density, density)
                    || materiallyDifferent(cell.energy, snapshot.stormPotential())
                    || materiallyDifferent(cell.windX, (float) snapshot.windVector().x())
                    || materiallyDifferent(cell.windZ, (float) snapshot.windVector().z())
                    || cell.phase != phase;

            cell.x = snapshot.center().x();
            cell.z = snapshot.center().z();
            cell.baseY = snapshot.baseY();
            cell.topY = snapshot.topY();
            cell.radiusMajor = radiusMajor;
            cell.radiusMinor = radiusMinor;
            cell.targetRadiusMajor = radiusMajor;
            cell.targetRadiusMinor = radiusMinor;
            cell.targetVerticalExtent = Math.max(1.0F, snapshot.topY() - snapshot.baseY());
            cell.orientationRadians = (snapshot.seed() & 0x3FFL) * 0.006135923F;
            cell.density = density;
            cell.targetDensity = density;
            cell.edgeSoftness = Mth.clamp(1.0F - snapshot.effectiveCoverage() * 0.60F, 0.18F, 0.92F);
            cell.energy = snapshot.stormPotential();
            cell.rotation = rotation;
            cell.windX = (float) snapshot.windVector().x();
            cell.windZ = (float) snapshot.windVector().z();
            cell.phase = phase;
            cell.ageTicks = snapshot.fieldAgeTicks();
            cell.lifetimeTicks = (int) Math.min(Integer.MAX_VALUE, snapshot.lifetimeTicks());
            cell.classification = CloudCellClassifier.classify(cell.toCell(gameTime));
            cell.dirty |= changed;
        }

        private static boolean materiallyDifferent(double left, double right) {
            return Math.abs(left - right) > 0.002D;
        }

        // -------------------------------------------------------------
        // Wind: forecast wind with altitude shear (speed and veer).
        // -------------------------------------------------------------
        private void refreshWinds(ServerLevel level, long gameTime) {
            double driftScale = AtmosphereConfig.clouds().cloudWindDriftScale();
            for (MutableCell cell : cells.values()) {
                if (cell.derivedFromField) {
                    continue;
                }
                BlockPos pos = BlockPos.containing(cell.x, cell.baseY, cell.z);
                WindVector wind = ForecastOrchestrator.getWind(pos, gameTime);
                float speedMps = wind == null ? 1.0F : Math.max(0.0F, wind.baseSpeed());
                float angle = wind == null ? 0.0F : wind.angleRadians();

                // Altitude shear: faster and veered with height, referenced to
                // the cell's mid altitude above a 128m baseline.
                float midY = (cell.baseY + cell.topY) * 0.5F;
                float altitude = Math.max(0.0F, midY - 128.0F);
                float shearSpeed = 1.0F + altitude / 600.0F;
                float shearVeer = altitude / 900.0F;

                float blocksPerTick = (float) (speedMps * shearSpeed * 0.05D * driftScale);
                blocksPerTick = Mth.clamp(blocksPerTick, 0.0F, 0.45F);
                float sheared = angle + shearVeer;
                float newWindX = (float) (Math.cos(sheared) * blocksPerTick);
                float newWindZ = (float) (Math.sin(sheared) * blocksPerTick);

                // Smooth so wind changes never look like cells changing lanes.
                cell.windX = cell.windX * 0.9F + newWindX * 0.1F;
                cell.windZ = cell.windZ * 0.9F + newWindZ * 0.1F;
                cell.shear = shearVeer;
                cell.dirty = true;
            }
        }

        // -------------------------------------------------------------
        // Lifecycle
        // -------------------------------------------------------------
        private void stepLifecycles(ServerLevel level, long gameTime) {
            Iterator<Map.Entry<UUID, MutableCell>> iterator = cells.entrySet().iterator();
            while (iterator.hasNext()) {
                MutableCell cell = iterator.next().getValue();
                if (cell.derivedFromField) {
                    updateFunnelLifecycle(level, cell, gameTime);
                    continue;
                }
                cell.ageTicks += SIM_INTERVAL_TICKS;
                RegionAtmosphereState region = AtmosphericStateRegistry.getState(
                        RegionInstanceKey.from(BlockPos.containing(cell.x, cell.baseY, cell.z)));
                float regionHumidity = region == null ? 0.5F : region.getHumidity();
                float regionCover = region == null ? 0.5F : Mth.clamp(region.getCloudCover(), 0.0F, 1.0F);
                float regionEnergy = region == null ? 0.2F
                        : Mth.clamp(region.getRainIntensity() * 0.7F + (regionHumidity - 0.5F) * 0.6F, 0.0F, 1.0F);

                switch (cell.phase) {
                    case FORMING -> {
                        float t = Mth.clamp(cell.ageTicks / (float) cell.formationTicks, 0.0F, 1.0F);
                        float eased = t * t * (3.0F - 2.0F * t);
                        cell.radiusMajor = cell.targetRadiusMajor * (0.25F + 0.75F * eased);
                        cell.radiusMinor = cell.targetRadiusMinor * (0.25F + 0.75F * eased);
                        cell.density = cell.targetDensity * eased;
                        cell.topY = cell.baseY + cell.targetVerticalExtent * (0.3F + 0.7F * eased);
                        if (t >= 1.0F) {
                            cell.phase = CloudCellLifecyclePhase.MATURE;
                        }
                        cell.dirty = true;
                    }
                    case MATURE -> {
                        // Energy relaxes toward the regional value; convective
                        // towers grow while energy is high.
                        cell.energy += (regionEnergy - cell.energy) * 0.03F;
                        if (cell.energy > 0.45F) {
                            float growth = (cell.energy - 0.45F) * 0.9F;
                            cell.topY = Math.min(cell.baseY + 320.0F, cell.topY + growth);
                        } else {
                            cell.topY = Math.max(cell.baseY + 14.0F, cell.topY - 0.15F);
                        }
                        // Gentle radial evolution keeps footprints alive.
                        float radialDrift = (regionHumidity - 0.5F) * 0.25F;
                        cell.radiusMajor = Mth.clamp(cell.radiusMajor + radialDrift, 40.0F, 900.0F);
                        cell.radiusMinor = Mth.clamp(cell.radiusMinor + radialDrift * 0.8F,
                                30.0F, cell.radiusMajor);
                        // Rotation spins up in energetic, sheared cells and
                        // decays otherwise (phase-8 tornado precursor).
                        float spinInput = Math.max(0.0F, cell.energy - 0.55F) * (0.5F + cell.shear * 2.0F);
                        cell.rotation = Mth.clamp(cell.rotation * 0.995F + spinInput * 0.01F, 0.0F, 1.0F);

                        boolean starving = regionCover < 0.12F && regionHumidity < 0.35F;
                        if (cell.ageTicks > cell.lifetimeTicks || starving) {
                            cell.phase = CloudCellLifecyclePhase.DISSIPATING;
                        }
                        cell.dirty = true;
                    }
                    case DISSIPATING -> {
                        cell.density *= 0.97F;
                        cell.radiusMajor = Math.max(8.0F, cell.radiusMajor * 0.996F);
                        cell.radiusMinor = Math.max(6.0F, cell.radiusMinor * 0.996F);
                        cell.topY = Math.max(cell.baseY + 6.0F, cell.topY - 0.4F);
                        cell.rotation *= 0.98F;
                        cell.funnelStrength *= 0.95F;
                        cell.dirty = true;
                        if (cell.density < 0.02F) {
                            iterator.remove();
                            noteRemoved(cell.id);
                        }
                    }
                }
                updateFunnelLifecycle(level, cell, gameTime);
            }
        }

        private void updateFunnelLifecycle(ServerLevel level, MutableCell cell, long gameTime) {
            if (cell.funnelStrength <= 0.001F) {
                cell.funnelStrength = 0.0F;
                return;
            }
            CloudCell snapshot = cell.toCell(gameTime);
            CloudCellClassification derivedClassification = CloudCellClassifier.classify(snapshot);
            boolean eligible = cell.phase == CloudCellLifecyclePhase.MATURE
                    && CloudCellClassifier.isTornadoEligible(snapshot, derivedClassification);
            if (!eligible) {
                cell.funnelStrength = Math.max(0.0F, cell.funnelStrength - 0.075F);
                cell.dirty = true;
                return;
            }
            float target = Mth.clamp(
                    0.18F + (cell.energy - 0.75F) * 1.6F + (cell.rotation - 0.55F) * 1.25F,
                    0.18F,
                    1.0F
            );
            cell.funnelStrength += (target - cell.funnelStrength) * 0.18F;
            int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Mth.floor(cell.x), Mth.floor(cell.z));
            cell.funnelGroundY = Math.max(level.getMinBuildHeight(), surfaceY - 1.0F);
            cell.dirty = true;
        }

        // -------------------------------------------------------------
        // Merging with hysteresis: CPU overlap score is the primary
        // evidence; recent GPU bridge measurements add a bonus.
        // -------------------------------------------------------------
        private void evaluateMerges(long gameTime) {
            List<MutableCell> list = new ArrayList<>(cells.values());
            for (int i = 0; i < list.size(); i++) {
                MutableCell first = list.get(i);
                if (first.derivedFromField || first.phase == CloudCellLifecyclePhase.DISSIPATING) {
                    continue;
                }
                for (int j = i + 1; j < list.size(); j++) {
                    MutableCell second = list.get(j);
                    if (second.derivedFromField || second.phase == CloudCellLifecyclePhase.DISSIPATING
                            || !cells.containsKey(first.id) || !cells.containsKey(second.id)) {
                        continue;
                    }
                    float score = CloudCellDensityMath.overlapScore(first.toCell(gameTime), second.toCell(gameTime));
                    score += gpuOverlapBonus(first.id, second.id);
                    if (score < MERGE_SCORE_THRESHOLD) {
                        clearMergeEvidence(first.id, second.id);
                        continue;
                    }
                    MergeEvidence evidence = mergeEvidence.computeIfAbsent(first.id, id -> new MergeEvidence());
                    if (!second.id.equals(evidence.peer)) {
                        evidence.peer = second.id;
                        evidence.streak = 0;
                    }
                    evidence.streak++;
                    if (evidence.streak >= MERGE_STREAK_REQUIRED) {
                        mergeCells(first, second, gameTime);
                        mergeEvidence.remove(first.id);
                        break;
                    }
                }
            }
        }

        private float gpuOverlapBonus(UUID first, UUID second) {
            CloudCellAnalyticsReport report = gpuEvidence.get(first);
            if (report != null && second.equals(report.bestOverlapPeer())) {
                return Mth.clamp(report.bestOverlapScore(), 0.0F, 1.0F) * 0.15F;
            }
            report = gpuEvidence.get(second);
            if (report != null && first.equals(report.bestOverlapPeer())) {
                return Mth.clamp(report.bestOverlapScore(), 0.0F, 1.0F) * 0.15F;
            }
            return 0.0F;
        }

        private void clearMergeEvidence(UUID first, UUID second) {
            MergeEvidence evidence = mergeEvidence.get(first);
            if (evidence != null && second.equals(evidence.peer)) {
                mergeEvidence.remove(first);
            }
        }

        private void mergeCells(MutableCell survivor, MutableCell absorbed, long gameTime) {
            if (absorbed.footprintArea() > survivor.footprintArea()) {
                MutableCell swap = survivor;
                survivor = absorbed;
                absorbed = swap;
            }
            float areaA = survivor.footprintArea();
            float areaB = absorbed.footprintArea();
            float total = Math.max(1.0F, areaA + areaB);
            survivor.x = (survivor.x * areaA + absorbed.x * areaB) / total;
            survivor.z = (survivor.z * areaA + absorbed.z * areaB) / total;
            // Area-conserving footprint growth.
            float scale = (float) Math.sqrt(total / Math.max(1.0F, areaA));
            survivor.radiusMajor = Math.min(900.0F, survivor.radiusMajor * scale);
            survivor.radiusMinor = Math.min(survivor.radiusMajor, survivor.radiusMinor * scale);
            survivor.targetRadiusMajor = Math.max(survivor.targetRadiusMajor, survivor.radiusMajor);
            survivor.targetRadiusMinor = Math.max(survivor.targetRadiusMinor, survivor.radiusMinor);
            survivor.topY = Math.max(survivor.topY, absorbed.topY);
            survivor.baseY = Math.min(survivor.baseY, absorbed.baseY);
            survivor.energy = Math.max(survivor.energy, absorbed.energy);
            survivor.density = Math.max(survivor.density, absorbed.density * 0.9F);
            survivor.rotation = Math.max(survivor.rotation, absorbed.rotation * 0.8F);
            survivor.lifetimeTicks = Math.max(survivor.lifetimeTicks, absorbed.lifetimeTicks);
            survivor.dirty = true;
            cells.remove(absorbed.id);
            noteRemoved(absorbed.id);
        }

        // -------------------------------------------------------------
        // Splitting: GPU lobe evidence with streak, plus a CPU fallback
        // for oversized low-energy sheets.
        // -------------------------------------------------------------
        private void evaluateSplits(ServerLevel level, long gameTime) {
            List<MutableCell> list = new ArrayList<>(cells.values());
            for (MutableCell cell : list) {
                if (cell.derivedFromField
                        || cell.phase != CloudCellLifecyclePhase.MATURE
                        || cells.size() >= MAX_CELLS_PER_DIMENSION) {
                    splitStreaks.remove(cell.id);
                    continue;
                }
                CloudCellAnalyticsReport report = gpuEvidence.get(cell.id);
                boolean gpuSaysSplit = report != null && report.splitScore() > 0.45F;
                boolean cpuSaysSplit = cell.radiusMajor > 640.0F && cell.energy < 0.30F
                        && level.random.nextFloat() < 0.25F;
                if (gpuSaysSplit || cpuSaysSplit) {
                    int streak = splitStreaks.merge(cell.id, 1, Integer::sum);
                    if (streak >= SPLIT_STREAK_REQUIRED) {
                        splitStreaks.remove(cell.id);
                        splitCell(cell, level, gameTime);
                    }
                } else {
                    splitStreaks.remove(cell.id);
                }
            }
        }

        private void splitCell(MutableCell parent, ServerLevel level, long gameTime) {
            double offsetX = Math.cos(parent.orientationRadians) * parent.radiusMajor * 0.55D;
            double offsetZ = Math.sin(parent.orientationRadians) * parent.radiusMajor * 0.55D;
            for (int side = -1; side <= 1; side += 2) {
                MutableCell child = new MutableCell(UUID.randomUUID(), level.random.nextLong(), dimensionId);
                child.x = parent.x + offsetX * side;
                child.z = parent.z + offsetZ * side;
                child.baseY = parent.baseY;
                child.topY = parent.topY - (side > 0 ? 0.0F : parent.verticalExtent() * 0.15F);
                child.radiusMajor = parent.radiusMajor * 0.62F;
                child.radiusMinor = Math.min(child.radiusMajor, parent.radiusMinor * 0.7F);
                child.targetRadiusMajor = child.radiusMajor;
                child.targetRadiusMinor = child.radiusMinor;
                child.orientationRadians = parent.orientationRadians
                        + (level.random.nextFloat() - 0.5F) * 0.6F;
                child.density = parent.density * 0.92F;
                child.targetDensity = parent.targetDensity;
                child.targetVerticalExtent = parent.verticalExtent();
                child.edgeSoftness = parent.edgeSoftness;
                child.energy = parent.energy * (side > 0 ? 1.0F : 0.8F);
                child.rotation = parent.rotation * 0.6F;
                child.windX = parent.windX;
                child.windZ = parent.windZ;
                child.phase = CloudCellLifecyclePhase.MATURE;
                child.ageTicks = parent.ageTicks / 2;
                child.formationTicks = parent.formationTicks;
                child.lifetimeTicks = parent.lifetimeTicks;
                child.funnelGroundY = parent.funnelGroundY;
                child.dirty = true;
                cells.put(child.id, child);
            }
            cells.remove(parent.id);
            noteRemoved(parent.id);
        }

        // -------------------------------------------------------------
        // Classification with hysteresis: a new label must persist one
        // full classify interval before it is committed.
        // -------------------------------------------------------------
        private void reclassify(long gameTime) {
            for (MutableCell cell : cells.values()) {
                CloudCellClassification proposal = CloudCellClassifier.classify(cell.toCell(gameTime));
                if (proposal == cell.classification) {
                    pendingClassifications.remove(cell.id);
                    continue;
                }
                PendingClassification pending = pendingClassifications.get(cell.id);
                if (pending != null && pending.label == proposal) {
                    cell.classification = proposal;
                    cell.dirty = true;
                    pendingClassifications.remove(cell.id);
                } else {
                    pendingClassifications.put(cell.id, new PendingClassification(proposal, gameTime));
                }
            }
            pendingClassifications.keySet().retainAll(cells.keySet());
        }

        private void expireGpuEvidence(long gameTime) {
            gpuEvidenceTime.entrySet().removeIf(entry -> gameTime - entry.getValue() > ANALYTICS_STALE_TICKS);
            gpuEvidence.keySet().retainAll(gpuEvidenceTime.keySet());
            gpuEvidence.keySet().retainAll(cells.keySet());
            mergeEvidence.keySet().retainAll(cells.keySet());
        }

        // -------------------------------------------------------------
        // Sync
        // -------------------------------------------------------------
        private void rebuildSnapshot(long gameTime) {
            List<CloudCell> next = new ArrayList<>(cells.size());
            for (MutableCell cell : cells.values()) {
                next.add(cell.toCell(gameTime));
            }
            snapshot = List.copyOf(next);
        }

        private synchronized void sendFull(ServerPlayer player) {
            if (player == null) {
                return;
            }
            List<CloudCell> interested = interestedCells(player);
            AtmosphereNetwork.sendToPlayer(
                    player,
                    new SyncCloudCellsPacket(interested, player.serverLevel().getGameTime()));
            playerSyncStates.computeIfAbsent(player.getUUID(), ignored -> new PlayerCellSyncState())
                    .replace(interested);
        }

        private void sendFull(ServerLevel level) {
            for (ServerPlayer player : level.players()) {
                sendFull(player);
            }
            removedSinceSync.clear();
            for (MutableCell cell : cells.values()) {
                cell.dirty = false;
            }
        }

        private synchronized void forgetPlayer(UUID playerId) {
            playerSyncStates.remove(playerId);
            analyticsSenderLastAccept.remove(playerId);
        }

        private void sendDelta(ServerLevel level, long gameTime) {
            for (ServerPlayer player : level.players()) {
                List<CloudCell> interested = interestedCells(player);
                PlayerCellSyncState state = playerSyncStates.computeIfAbsent(
                        player.getUUID(), ignored -> new PlayerCellSyncState());
                if (!state.initialized) {
                    sendFull(player);
                    continue;
                }
                Map<UUID, Long> nextFingerprints = cellFingerprints(interested);
                List<CloudCell> updated = new ArrayList<>();
                for (CloudCell cell : interested) {
                    Long previous = state.fingerprints.get(cell.id());
                    long next = nextFingerprints.get(cell.id());
                    if (previous == null || previous.longValue() != next) {
                        updated.add(cell);
                    }
                }
                Set<UUID> removed = new HashSet<>(state.fingerprints.keySet());
                removed.removeAll(nextFingerprints.keySet());
                if (!updated.isEmpty() || !removed.isEmpty()) {
                    AtmosphereNetwork.sendToPlayer(
                            player,
                            new CloudCellDeltaPacket(updated, removed, gameTime));
                }
                state.fingerprints = Map.copyOf(nextFingerprints);
            }
            removedSinceSync.clear();
            for (MutableCell cell : cells.values()) {
                cell.dirty = false;
            }
        }

        private List<CloudCell> interestedCells(ServerPlayer player) {
            List<CloudCell> interested = new ArrayList<>();
            for (CloudCell cell : snapshot) {
                double radius = PLAYER_INTEREST_RADIUS + cell.radiusMajor() + 384.0D;
                double radiusSqr = radius * radius;
                double dx = cell.x() - player.getX();
                double dz = cell.z() - player.getZ();
                double futureX = cell.x() + cell.wind().x() * 200.0D;
                double futureZ = cell.z() + cell.wind().z() * 200.0D;
                double futureDx = futureX - player.getX();
                double futureDz = futureZ - player.getZ();
                if (dx * dx + dz * dz <= radiusSqr
                        || futureDx * futureDx + futureDz * futureDz <= radiusSqr) {
                    interested.add(cell);
                }
            }
            interested.sort((left, right) -> left.id().compareTo(right.id()));
            return List.copyOf(interested);
        }

        private static Map<UUID, Long> cellFingerprints(List<CloudCell> cells) {
            Map<UUID, Long> result = new LinkedHashMap<>();
            for (CloudCell cell : cells) {
                result.put(cell.id(), cellFingerprint(cell));
            }
            return result;
        }

        private static long cellFingerprint(CloudCell cell) {
            long hash = 0xcbf29ce484222325L;
            hash = fingerprintMix(hash, fingerprintQuantize(cell.x(), 4.0D));
            hash = fingerprintMix(hash, fingerprintQuantize(cell.z(), 4.0D));
            hash = fingerprintMix(hash, fingerprintQuantize(cell.baseY(), 8.0D));
            hash = fingerprintMix(hash, fingerprintQuantize(cell.topY(), 8.0D));
            hash = fingerprintMix(hash, fingerprintQuantize(cell.radiusMajor(), 8.0D));
            hash = fingerprintMix(hash, fingerprintQuantize(cell.radiusMinor(), 8.0D));
            hash = fingerprintMix(hash, fingerprintQuantize(cell.density(), 1024.0D));
            hash = fingerprintMix(hash, fingerprintQuantize(cell.energy(), 1024.0D));
            hash = fingerprintMix(hash, fingerprintQuantize(cell.rotation(), 1024.0D));
            hash = fingerprintMix(hash, fingerprintQuantize(cell.funnelStrength(), 2048.0D));
            hash = fingerprintMix(hash, fingerprintQuantize(cell.wind().x(), 4096.0D));
            hash = fingerprintMix(hash, fingerprintQuantize(cell.wind().z(), 4096.0D));
            hash = fingerprintMix(hash, cell.phase().ordinal());
            return fingerprintMix(hash, cell.classification().ordinal());
        }

        private static long fingerprintQuantize(double value, double scale) {
            return Double.isFinite(value) ? Math.round(value * scale) : 0L;
        }

        private static long fingerprintMix(long hash, long value) {
            return (hash ^ value) * 0x100000001b3L;
        }

        private void noteRemoved(UUID id) {
            removedSinceSync.add(id);
            mergeEvidence.remove(id);
            splitStreaks.remove(id);
            pendingClassifications.remove(id);
            gpuEvidence.remove(id);
            gpuEvidenceTime.remove(id);
        }

        private static final class PlayerCellSyncState {
            private Map<UUID, Long> fingerprints = Map.of();
            private boolean initialized;

            private void replace(List<CloudCell> cells) {
                fingerprints = Map.copyOf(cellFingerprints(cells));
                initialized = true;
            }
        }
    }

    // =================================================================
    // Mutable working representation of a cell
    // =================================================================

    private static final class MutableCell {
        private final UUID id;
        private final long seed;
        private final String dimensionId;
        private double x;
        private double z;
        private float baseY = 200.0F;
        private float topY = 240.0F;
        private float radiusMajor = 60.0F;
        private float radiusMinor = 50.0F;
        private float orientationRadians;
        private float density;
        private float edgeSoftness = 0.5F;
        private float energy;
        private float rotation;
        private float funnelStrength;
        private float funnelGroundY = 64.0F;
        private float windX;
        private float windZ;
        private float shear;
        private CloudCellLifecyclePhase phase = CloudCellLifecyclePhase.FORMING;
        private CloudCellClassification classification = CloudCellClassification.UNCLASSIFIED;
        private long ageTicks;
        private int formationTicks = 900;
        private int lifetimeTicks = 12000;
        private float targetRadiusMajor = 200.0F;
        private float targetRadiusMinor = 150.0F;
        private float targetDensity = 0.7F;
        private float targetVerticalExtent = 60.0F;
        private boolean derivedFromField;
        private boolean dirty = true;

        private MutableCell(UUID id, long seed, String dimensionId) {
            this.id = id;
            this.seed = seed;
            this.dimensionId = dimensionId;
        }

        private float verticalExtent() {
            return topY - baseY;
        }

        private float footprintArea() {
            return (float) (Math.PI * radiusMajor * radiusMinor);
        }

        private CloudCell toCell(long worldTime) {
            return new CloudCell(
                    id, seed, dimensionId, x, z, baseY, topY,
                    radiusMajor, radiusMinor, orientationRadians,
                    density, edgeSoftness, energy, rotation,
                    funnelStrength, funnelGroundY,
                    new Vec3(windX, 0.0D, windZ),
                    phase, classification, ageTicks, worldTime
            );
        }
    }

    private static final class MergeEvidence {
        private UUID peer;
        private int streak;
    }

    private record PendingClassification(CloudCellClassification label, long sinceTime) {
    }

    static {
        ProjectAtmosphere.LOGGER.debug("[CloudCells] simulation manager loaded");
    }
}
