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
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.network.NetworkHandler;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

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
    private static final double SPAWN_MIN_DISTANCE = 420.0D;
    private static final double SPAWN_MAX_DISTANCE = 2400.0D;
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
        List<CloudCell> cells = sim == null ? List.of() : sim.snapshotCells();
        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SyncCloudCellsPacket(cells, level.getGameTime())
        );
    }

    public void tick(ServerLevel level) {
        if (level == null || level.players().isEmpty()) {
            return;
        }
        DimensionSim sim = sims.computeIfAbsent(dimensionId(level), id -> new DimensionSim(id));
        sim.tick(level);
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
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
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
        private final Set<UUID> removedSinceSync = new HashSet<>();
        private long lastFullSyncTime = Long.MIN_VALUE;
        private volatile List<CloudCell> snapshot = List.of();

        private DimensionSim(String dimensionId) {
            this.dimensionId = dimensionId;
        }

        private synchronized List<CloudCell> snapshotCells() {
            return snapshot;
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

        private synchronized void tick(ServerLevel level) {
            long gameTime = level.getGameTime();
            boolean frozen = AtmoCommonConfig.FREEZE_CLOUD_MOVEMENT.get();

            // Motion every tick: keeps client extrapolation honest.
            if (!frozen && AtmoCommonConfig.ENABLE_CLOUD_MOVEMENT.get()) {
                for (MutableCell cell : cells.values()) {
                    cell.x += cell.windX;
                    cell.z += cell.windZ;
                }
            }

            if (gameTime % SIM_INTERVAL_TICKS == 0L) {
                refreshWinds(level, gameTime);
                stepLifecycles(level, gameTime);
                spawnCells(level, gameTime);
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
                broadcastFull(level, snapshot);
            } else if (gameTime % DELTA_SYNC_INTERVAL_TICKS == 0L) {
                sendDelta(level, gameTime);
            }
        }

        // -------------------------------------------------------------
        // Wind: forecast wind with altitude shear (speed and veer).
        // -------------------------------------------------------------
        private void refreshWinds(ServerLevel level, long gameTime) {
            double driftScale = AtmoCommonConfig.CLOUD_WIND_DRIFT_SCALE.get();
            for (MutableCell cell : cells.values()) {
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
            }
        }

        // -------------------------------------------------------------
        // Spawning: population follows regional cloud cover near players.
        // -------------------------------------------------------------
        private void spawnCells(ServerLevel level, long gameTime) {
            if (cells.size() >= MAX_CELLS_PER_DIMENSION) {
                return;
            }
            RandomSource random = level.random;
            for (ServerPlayer player : level.players()) {
                RegionAtmosphereState region = AtmosphericStateRegistry.getState(
                        RegionInstanceKey.from(player.blockPosition()));
                float cover = region == null ? 0.4F : Mth.clamp(region.getCloudCover(), 0.0F, 1.0F);
                float humidity = region == null ? 0.5F : region.getHumidity();
                int target = Math.round(cover * 30.0F);
                int nearby = countCellsNear(player.getX(), player.getZ());
                if (nearby >= target || cells.size() >= MAX_CELLS_PER_DIMENSION) {
                    continue;
                }

                double angle = random.nextDouble() * Math.PI * 2.0D;
                double distance = SPAWN_MIN_DISTANCE
                        + random.nextDouble() * (SPAWN_MAX_DISTANCE - SPAWN_MIN_DISTANCE);
                double x = player.getX() + Math.cos(angle) * distance;
                double z = player.getZ() + Math.sin(angle) * distance;
                if (isTooCloseToExisting(x, z)) {
                    continue;
                }

                MutableCell cell = new MutableCell(UUID.randomUUID(), random.nextLong(), dimensionId);
                cell.x = x;
                cell.z = z;
                float spawnHeight = AtmoCommonConfig.NATIVE_CLOUD_SPAWN_HEIGHT.get();
                cell.baseY = spawnHeight - 40.0F + random.nextFloat() * 60.0F;
                cell.targetRadiusMajor = 120.0F + random.nextFloat() * 320.0F;
                cell.targetRadiusMinor = cell.targetRadiusMajor * (0.55F + random.nextFloat() * 0.4F);
                cell.orientationRadians = random.nextFloat() * (float) Math.PI;
                cell.targetDensity = Mth.clamp(0.35F + humidity * 0.6F + random.nextFloat() * 0.15F, 0.2F, 1.0F);
                float instability = region == null ? 0.2F
                        : Mth.clamp(region.getRainIntensity() * 0.8F + (humidity - 0.5F) * 0.5F, 0.0F, 1.0F);
                cell.energy = instability * (0.6F + random.nextFloat() * 0.4F);
                cell.targetVerticalExtent = 26.0F + instability * 140.0F + random.nextFloat() * 30.0F;
                cell.edgeSoftness = 0.35F + random.nextFloat() * 0.3F;
                cell.formationTicks = 600 + random.nextInt(900);
                cell.lifetimeTicks = 6000 + random.nextInt(18000);
                cell.phase = CloudCellLifecyclePhase.FORMING;
                cell.radiusMajor = cell.targetRadiusMajor * 0.25F;
                cell.radiusMinor = cell.targetRadiusMinor * 0.25F;
                cell.topY = cell.baseY + cell.targetVerticalExtent * 0.3F;
                cell.density = 0.0F;
                cell.funnelGroundY = (float) level.getSeaLevel();
                cell.dirty = true;
                cells.put(cell.id, cell);
                if (cells.size() >= MAX_CELLS_PER_DIMENSION) {
                    return;
                }
            }
        }

        private int countCellsNear(double x, double z) {
            int count = 0;
            double radiusSqr = PLAYER_INTEREST_RADIUS * PLAYER_INTEREST_RADIUS;
            for (MutableCell cell : cells.values()) {
                double dx = cell.x - x;
                double dz = cell.z - z;
                if (dx * dx + dz * dz < radiusSqr) {
                    count++;
                }
            }
            return count;
        }

        private boolean isTooCloseToExisting(double x, double z) {
            for (MutableCell cell : cells.values()) {
                double dx = cell.x - x;
                double dz = cell.z - z;
                double minDistance = cell.radiusMajor * 0.9D + 60.0D;
                if (dx * dx + dz * dz < minDistance * minDistance) {
                    return true;
                }
            }
            return false;
        }

        // -------------------------------------------------------------
        // Merging with hysteresis: CPU overlap score is the primary
        // evidence; recent GPU bridge measurements add a bonus.
        // -------------------------------------------------------------
        private void evaluateMerges(long gameTime) {
            List<MutableCell> list = new ArrayList<>(cells.values());
            for (int i = 0; i < list.size(); i++) {
                MutableCell first = list.get(i);
                if (first.phase == CloudCellLifecyclePhase.DISSIPATING) {
                    continue;
                }
                for (int j = i + 1; j < list.size(); j++) {
                    MutableCell second = list.get(j);
                    if (second.phase == CloudCellLifecyclePhase.DISSIPATING
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
                if (cell.phase != CloudCellLifecyclePhase.MATURE
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

        private void sendDelta(ServerLevel level, long gameTime) {
            List<CloudCell> updated = new ArrayList<>();
            for (MutableCell cell : cells.values()) {
                if (cell.dirty) {
                    updated.add(cell.toCell(gameTime));
                    cell.dirty = false;
                }
            }
            if (updated.isEmpty() && removedSinceSync.isEmpty()) {
                return;
            }
            CloudCellDeltaPacket packet = new CloudCellDeltaPacket(
                    updated, new ArrayList<>(removedSinceSync), gameTime);
            removedSinceSync.clear();
            for (ServerPlayer player : level.players()) {
                NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
            }
        }

        private void noteRemoved(UUID id) {
            removedSinceSync.add(id);
            mergeEvidence.remove(id);
            splitStreaks.remove(id);
            pendingClassifications.remove(id);
            gpuEvidence.remove(id);
            gpuEvidenceTime.remove(id);
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
