package net.Gabou.projectatmosphere.modules.atmosphere;

import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionStateStore;
import net.Gabou.projectatmosphere.clouds.type.CloudFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.ocean.OceanBasinManager;
import net.Gabou.projectatmosphere.modules.weathercell.WeatherCellManager;
import net.Gabou.projectatmosphere.modules.weathercell.WeatherCellState;
import net.Gabou.projectatmosphere.modules.weathercell.WeatherCellType;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WeakLowManager {
    static final int MAX_LIFETIME_TICKS = 20 * 60 * 45;
    static final float SUSTAIN_SUPPORT_THRESHOLD = 0.45F;

    private static final int TICK_INTERVAL = 100;
    private static final int MAX_ACTIVE_LOWS = 8;
    private static final int MAX_CANDIDATES = 24;
    private static final double LOW_MERGE_RADIUS = 700.0D;
    private static final float FORMATION_SUPPORT_THRESHOLD = 0.52F;

    private static final Map<UUID, WeakLowState> ACTIVE_LOWS = new ConcurrentHashMap<>();
    private static long nextTick;
    private static long nextScanTick;
    private static long lastWeakLowSpawnTick = -1L;

    private WeakLowManager() {
    }

    public static void initialize(ServerLevel level) {
        ACTIVE_LOWS.clear();
        nextTick = 0L;
        nextScanTick = 0L;
        lastWeakLowSpawnTick = -1L;
    }

    public static void tick(ServerLevel level) {
        if (level == null || AtmosphericStateRegistry.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        if (now < nextTick) {
            return;
        }
        nextTick = now + TICK_INTERVAL;

        boolean removed = false;
        for (WeakLowState low : new ArrayList<>(ACTIVE_LOWS.values())) {
            WeakLowCandidate candidate = evaluateNearestCandidate(level, low);
            low.applyCandidate(candidate, TICK_INTERVAL);
            if (!low.isActive()) {
                ACTIVE_LOWS.remove(low.getId());
                removed = true;
            }
        }

        if (now >= nextScanTick) {
            nextScanTick = now + CandidateRegionScanner.scanIntervalTicks();
            scanForNewLows(level);
        }
    }

    public static CompoundTag savePersistentState() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("NextTick", nextTick);
        tag.putLong("NextScanTick", nextScanTick);
        tag.putLong("LastWeakLowSpawnTick", lastWeakLowSpawnTick);
        ListTag lows = new ListTag();
        for (WeakLowState low : ACTIVE_LOWS.values()) {
            if (low != null && low.isActive()) {
                lows.add(low.save());
            }
        }
        tag.put("Lows", lows);
        return tag;
    }

    public static void loadPersistentState(CompoundTag tag) {
        ACTIVE_LOWS.clear();
        nextTick = 0L;
        nextScanTick = 0L;
        if (tag == null || tag.isEmpty()) {
            return;
        }
        nextTick = tag.getLong("NextTick");
        nextScanTick = tag.getLong("NextScanTick");
        lastWeakLowSpawnTick = tag.contains("LastWeakLowSpawnTick", Tag.TAG_LONG) ? tag.getLong("LastWeakLowSpawnTick") : -1L;
        ListTag lows = tag.getList("Lows", Tag.TAG_COMPOUND);
        for (int i = 0; i < lows.size(); i++) {
            WeakLowState low = WeakLowState.load(lows.getCompound(i));
            if (low != null && low.isActive()) {
                ACTIVE_LOWS.put(low.getId(), low);
            }
        }
    }

    public static List<WeakLowSnapshot> getActiveSnapshots() {
        if (ACTIVE_LOWS.isEmpty()) {
            return List.of();
        }
        return ACTIVE_LOWS.values().stream()
                .filter(WeakLowState::isActive)
                .map(WeakLowManager::snapshot)
                .sorted(Comparator.comparing(WeakLowSnapshot::intensity).reversed())
                .toList();
    }

    public static List<WeakLowCandidate> evaluateCandidates(ServerLevel level) {
        return evaluateCandidateDiagnostics(level).candidates();
    }

    public static WeakLowCandidateDiagnostics evaluateCandidateDiagnostics(ServerLevel level) {
        if (level == null) {
            return new WeakLowCandidateDiagnostics(
                    List.of(),
                    CandidateRegionScanner.scanRadiusRegions(),
                    CandidateRegionScanner.maxRegionsPerTick(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    Map.of(),
                    lastWeakLowSpawnTick,
                    nextScanTick
            );
        }
        List<WeakLowCandidate> candidates = new ArrayList<>();
        CandidateRegionScanner.ScanResult scan = CandidateRegionScanner.scan(level);
        Map<String, Integer> blockedReasons = new ConcurrentHashMap<>();
        for (CandidateRegionScanner.CandidateRegion region : scan.regions()) {
            RegionInstanceKey key = region.key();
            RegionAtmosphereState state = region.state();
            WeakLowCandidate candidate = evaluateCandidate(level, key, state);
            candidates.add(candidate);
            blockedReasons.merge(candidate.blockedReason(), 1, Integer::sum);
        }
        candidates.sort(Comparator.comparing(WeakLowCandidate::supportScore).reversed());
        if (candidates.size() > MAX_CANDIDATES) {
            candidates = new ArrayList<>(candidates.subList(0, MAX_CANDIDATES));
        }
        return new WeakLowCandidateDiagnostics(
                List.copyOf(candidates),
                scan.scanRadiusRegions(),
                scan.maxRegionsPerTick(),
                scan.activePlayersIncluded(),
                scan.checkedRegions(),
                scan.loadedRegions(),
                scan.forecastOnlyRegions(),
                scan.skippedRegions(),
                scan.duplicateRegionsSkipped(),
                Map.copyOf(blockedReasons),
                lastWeakLowSpawnTick,
                nextScanTick
        );
    }

    public static WeakLowCandidate bestCandidate(ServerLevel level) {
        List<WeakLowCandidate> candidates = evaluateCandidates(level);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    public static long lastWeakLowSpawnTick() {
        return lastWeakLowSpawnTick;
    }

    public static float organizationFor(RegionInstanceKey key) {
        RegionAtmosphereState state = AtmosphericStateRegistry.getState(key);
        if (state == null) {
            return 0.0F;
        }
        return organizationFor(state.getPosition());
    }

    public static float organizationFor(BlockPos position) {
        if (position == null || ACTIVE_LOWS.isEmpty()) {
            return 0.0F;
        }
        float best = 0.0F;
        for (WeakLowState low : ACTIVE_LOWS.values()) {
            if (low == null || !low.isActive()) {
                continue;
            }
            double dx = low.getCenter().x() - position.getX();
            double dz = low.getCenter().z() - position.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            float proximity = Mth.clamp(1.0F - (float) (distance / Math.max(1.0F, low.getRadius())), 0.0F, 1.0F);
            best = Math.max(best, proximity * low.getIntensity());
        }
        return Mth.clamp(best, 0.0F, 1.0F);
    }

    public static WeatherCellBoost weatherCellBoost(RegionInstanceKey key, BlockPos position) {
        float organization = Math.max(organizationFor(key), organizationFor(position));
        if (organization <= 0.0F) {
            return WeatherCellBoost.NONE;
        }
        return new WeatherCellBoost(
                organization,
                Mth.clamp(organization * 0.12F, 0.0F, 0.12F),
                Mth.clamp(organization * 0.05F, 0.0F, 0.05F),
                Mth.clamp(organization * 0.18F, 0.0F, 0.18F),
                Mth.clamp(organization * 0.06F, 0.0F, 0.06F)
        );
    }

    public static void markPromoted(RegionAtmosphereState state) {
        if (state == null || state.getPosition() == null) {
            return;
        }
        WeakLowState nearest = nearestLow(state.getPosition());
        if (nearest != null) {
            nearest.markPromotedToCycloneSeed();
        }
    }

    public static WeakLowSnapshot nearestSnapshot(BlockPos position) {
        WeakLowState nearest = nearestLow(position);
        return nearest == null ? null : snapshot(nearest);
    }

    private static void scanForNewLows(ServerLevel level) {
        if (ACTIVE_LOWS.size() >= MAX_ACTIVE_LOWS) {
            return;
        }
        for (WeakLowCandidate candidate : evaluateCandidates(level)) {
            if (ACTIVE_LOWS.size() >= MAX_ACTIVE_LOWS) {
                break;
            }
            if (!candidate.eligible() || isNearExistingLow(candidate.position())) {
                continue;
            }
            WeakLowState low = new WeakLowState(
                    UUID.randomUUID(),
                    candidate.regionKey(),
                    Vec3.atCenterOf(candidate.position()),
                    Mth.clamp(280.0F + candidate.supportScore() * 580.0F, 260.0F, 920.0F),
                    Mth.clamp(0.10F + candidate.supportScore() * 0.34F, 0.10F, 0.46F),
                    candidate.supportScore(),
                    0,
                    20 * 60 * 18,
                    true
            );
            low.applyCandidate(candidate, 0);
            ACTIVE_LOWS.put(low.getId(), low);
            lastWeakLowSpawnTick = level.getGameTime();
        }
    }

    private static WeakLowCandidate evaluateNearestCandidate(ServerLevel level, WeakLowState low) {
        if (low == null || low.getCenter() == null) {
            return null;
        }
        RegionAtmosphereState state = AtmosphericStateRegistry.findNearest(low.getCenter().x(), low.getCenter().z());
        if (state == null || state.getRegionId() == null) {
            return null;
        }
        return evaluateCandidate(level, state.getRegionId(), state);
    }

    private static WeakLowCandidate evaluateCandidate(ServerLevel level, RegionInstanceKey key, RegionAtmosphereState state) {
        AtmosphericSupportEvaluator.Support support = AtmosphericSupportEvaluator.evaluate(key, state);
        long gameTime = level == null ? 0L : level.getGameTime();
        float targetPressure = state.getTargetPressure(gameTime);
        float pressureAnomaly = Math.max(Math.max(0.0F, targetPressure - state.getPressure()), Math.max(0.0F, 1013.25F - state.getPressure()));
        float pressureSupport = ramp(pressureAnomaly, 4.0F, 13.0F);
        float humiditySupport = ramp(support.humidity(), 0.62F, 0.84F);
        float cloudWaterSupport = ramp(support.cloudWater(), 0.12F, 0.48F);
        float cloudCoverSupport = ramp(support.cloudCover(), 0.22F, 0.72F);
        float convergenceSupport = support.windConvergence();
        float shearSupport = estimateShear(key, state);
        float humidityTransportSupport = Mth.clamp(Math.max(0.0F, support.humidityTransport()) / 0.035F, 0.0F, 1.0F);
        float oceanFlux = OceanBasinManager.estimateHumidityFlux(key, state.getHumidity());
        float oceanPressure = OceanBasinManager.estimatePressureDelta(key, state.getPressure());
        float oceanSupport = Mth.clamp(Math.max(0.0F, oceanFlux) * 80.0F + Math.max(0.0F, -oceanPressure) * 45.0F, 0.0F, 1.0F);
        float instability = Mth.clamp(support.rainCellFormationScore(0.0F) * 0.55F + support.thunderstormSupport() * 0.45F, 0.0F, 1.0F);
        CloudSupports cloudSupports = estimateCloudSupports(level, state.getPosition());
        float weatherCellSupport = estimateWeatherCellSupport(level, state.getPosition());
        float incipientOrganization = max(
                convergenceSupport,
                shearSupport,
                instability,
                humidityTransportSupport,
                oceanSupport
        );
        boolean incipientLowBridge = pressureAnomaly >= 6.0F
                && support.humidity() >= 0.68F
                && incipientOrganization >= 0.10F;
        float incipientBridgeSupport = incipientLowBridge
                ? Mth.clamp(pressureSupport * 0.16F + humiditySupport * 0.12F + incipientOrganization * 0.18F, 0.0F, 0.22F)
                : 0.0F;

        float supportScore = Mth.clamp(
                pressureSupport * 0.20F
                        + humiditySupport * 0.18F
                        + cloudWaterSupport * 0.16F
                        + cloudCoverSupport * 0.08F
                        + convergenceSupport * 0.14F
                        + shearSupport * 0.06F
                        + instability * 0.10F
                        + cloudSupports.cumulonimbusSupport() * 0.04F
                        + cloudSupports.nimbostratusSupport() * 0.025F
                        + weatherCellSupport * 0.035F
                        + incipientBridgeSupport,
                0.0F,
                1.0F
        );

        String blockedReason = "none";
        boolean moistureOrCloudSupport = support.cloudWater() >= 0.12F
                || support.cloudCover() >= 0.24F
                || cloudSupports.nimbostratusSupport() >= 0.18F
                || cloudSupports.cumulonimbusSupport() >= 0.12F
                || weatherCellSupport >= 0.16F
                || incipientLowBridge;
        boolean organizingSource = convergenceSupport >= 0.12F
                || shearSupport >= 0.16F
                || instability >= 0.18F
                || weatherCellSupport >= 0.16F
                || cloudSupports.cumulonimbusSupport() >= 0.12F
                || humidityTransportSupport >= 0.18F
                || oceanSupport >= 0.12F
                || incipientLowBridge;
        if (pressureAnomaly < 4.0F) {
            blockedReason = "pressure anomaly too weak";
        } else if (support.humidity() < 0.62F) {
            blockedReason = "humidity too low";
        } else if (!moistureOrCloudSupport) {
            blockedReason = "no moisture or rain-cloud support";
        } else if (!organizingSource) {
            blockedReason = "no organizing source";
        } else if (supportScore < FORMATION_SUPPORT_THRESHOLD) {
            blockedReason = "support score too low";
        }
        boolean eligible = "none".equals(blockedReason);

        return new WeakLowCandidate(
                key,
                state.getPosition(),
                supportScore,
                pressureAnomaly,
                support.humidity(),
                support.cloudWater(),
                support.cloudCover(),
                convergenceSupport,
                shearSupport,
                instability,
                cloudSupports.cumulonimbusSupport(),
                cloudSupports.nimbostratusSupport(),
                weatherCellSupport,
                humidityTransportSupport,
                oceanSupport,
                incipientLowBridge,
                blockedReason,
                eligible
        );
    }

    private static CloudSupports estimateCloudSupports(ServerLevel level, BlockPos position) {
        if (level == null || position == null) {
            return CloudSupports.EMPTY;
        }
        float cumulonimbus = 0.0F;
        float nimbostratus = 0.0F;
        for (CloudRegionState region : CloudRegionStateStore.getActiveRegions(level)) {
            if (region == null || !region.isActive() || region.getCenter() == null) {
                continue;
            }
            double dx = region.getCenter().x() - position.getX();
            double dz = region.getCenter().z() - position.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            float range = Math.max(450.0F, region.getRadius() + 420.0F);
            if (distance > range) {
                continue;
            }
            float proximity = Mth.clamp(1.0F - (float) (distance / range), 0.0F, 1.0F);
            float strength = Mth.clamp((region.getCoverage() * 0.55F + region.getDensity() * 0.45F) * proximity, 0.0F, 1.0F);
            CloudFamily family = CloudTypeRegistry.getOrDefault(region.getCloudTypeId()).getFamily();
            if (family == CloudFamily.CUMULONIMBUS) {
                cumulonimbus = Math.max(cumulonimbus, strength);
            } else if (family == CloudFamily.NIMBOSTRATUS) {
                nimbostratus = Math.max(nimbostratus, strength);
            }
        }
        return new CloudSupports(cumulonimbus, nimbostratus);
    }

    private static float estimateWeatherCellSupport(ServerLevel level, BlockPos position) {
        if (level == null || position == null) {
            return 0.0F;
        }
        float support = 0.0F;
        for (WeatherCellState cell : WeatherCellManager.getCells(level)) {
            if (cell == null || !cell.isActive() || cell.getCenter() == null) {
                continue;
            }
            double dx = cell.getCenter().x() - position.getX();
            double dz = cell.getCenter().z() - position.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            float range = Math.max(1.0F, cell.getRadius() + 360.0F);
            if (distance > range) {
                continue;
            }
            float proximity = Mth.clamp(1.0F - (float) (distance / range), 0.0F, 1.0F);
            float typeScale = switch (cell.getType()) {
                case SUPERCELL -> 1.0F;
                case THUNDERSTORM -> 0.82F;
                case RAIN_CELL -> 0.55F;
                case CYCLONE -> 0.65F;
                case BLIZZARD -> 0.20F;
            };
            support = Math.max(support, proximity * Math.max(0.20F, cell.getIntensity()) * typeScale);
        }
        return Mth.clamp(support, 0.0F, 1.0F);
    }

    private static float estimateShear(RegionInstanceKey key, RegionAtmosphereState state) {
        if (key == null || state == null || state.getWind() == null) {
            return 0.0F;
        }
        WindVector centerWind = state.getWind();
        float centerX = (float) Math.sin(centerWind.angleRadians()) * centerWind.baseSpeed();
        float centerZ = (float) Math.cos(centerWind.angleRadians()) * centerWind.baseSpeed();
        float total = 0.0F;
        int samples = 0;
        for (RegionInstanceKey neighborKey : AtmosphericStateRegistry.getNeighbors(key)) {
            RegionAtmosphereState neighbor = AtmosphericStateRegistry.getState(neighborKey);
            if (neighbor == null || neighbor.getWind() == null) {
                continue;
            }
            WindVector wind = neighbor.getWind();
            float neighborX = (float) Math.sin(wind.angleRadians()) * wind.baseSpeed();
            float neighborZ = (float) Math.cos(wind.angleRadians()) * wind.baseSpeed();
            float diff = (float) Math.sqrt(Math.pow(centerX - neighborX, 2.0D) + Math.pow(centerZ - neighborZ, 2.0D));
            total += Mth.clamp(diff / 16.0F, 0.0F, 1.0F);
            samples++;
        }
        return samples == 0 ? 0.0F : Mth.clamp(total / samples, 0.0F, 1.0F);
    }

    private static boolean isNearExistingLow(BlockPos position) {
        if (position == null) {
            return false;
        }
        for (WeakLowState low : ACTIVE_LOWS.values()) {
            if (low == null || !low.isActive()) {
                continue;
            }
            double dx = low.getCenter().x() - position.getX();
            double dz = low.getCenter().z() - position.getZ();
            if (Math.sqrt(dx * dx + dz * dz) <= LOW_MERGE_RADIUS) {
                return true;
            }
        }
        return false;
    }

    private static WeakLowState nearestLow(BlockPos position) {
        if (position == null || ACTIVE_LOWS.isEmpty()) {
            return null;
        }
        WeakLowState nearest = null;
        double best = Double.MAX_VALUE;
        for (WeakLowState low : ACTIVE_LOWS.values()) {
            if (low == null || !low.isActive()) {
                continue;
            }
            double dx = low.getCenter().x() - position.getX();
            double dz = low.getCenter().z() - position.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance < best) {
                best = distance;
                nearest = low;
            }
        }
        return nearest;
    }

    private static WeakLowSnapshot snapshot(WeakLowState low) {
        return new WeakLowSnapshot(
                low.getId(),
                low.getRegionKey(),
                low.getCenter().x(),
                low.getCenter().z(),
                low.getRadius(),
                low.getIntensity(),
                low.getSupportScore(),
                low.getPressureAnomaly(),
                low.getHumidity(),
                low.getCloudWater(),
                low.getCloudCover(),
                low.getConvergence(),
                low.getShear(),
                low.getInstability(),
                low.getCumulonimbusSupport(),
                low.getNimbostratusSupport(),
                low.getWeatherCellSupport(),
                low.getBlockedReason(),
                low.getAgeTicks(),
                low.getLifetimeTicks(),
                low.getDecayReason(),
                low.isPromotedToCycloneSeed()
        );
    }

    private static float ramp(float value, float startsAt, float fullAt) {
        if (fullAt <= startsAt) {
            return value >= fullAt ? 1.0F : 0.0F;
        }
        return Mth.clamp((value - startsAt) / (fullAt - startsAt), 0.0F, 1.0F);
    }

    private static float max(float... values) {
        float max = 0.0F;
        if (values == null) {
            return max;
        }
        for (float value : values) {
            max = Math.max(max, value);
        }
        return max;
    }

    private record CloudSupports(float cumulonimbusSupport, float nimbostratusSupport) {
        private static final CloudSupports EMPTY = new CloudSupports(0.0F, 0.0F);
    }

    public record WeatherCellBoost(
            float organization,
            float formationBoost,
            float chanceBoost,
            float evolutionBoost,
            float severeBoost
    ) {
        public static final WeatherCellBoost NONE = new WeatherCellBoost(0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    public record WeakLowCandidate(
            RegionInstanceKey regionKey,
            BlockPos position,
            float supportScore,
            float pressureAnomaly,
            float humidity,
            float cloudWater,
            float cloudCover,
            float convergence,
            float shear,
            float instability,
            float cumulonimbusSupport,
            float nimbostratusSupport,
            float weatherCellSupport,
            float humidityTransportSupport,
            float oceanSupport,
            boolean incipientLowBridge,
            String blockedReason,
            boolean eligible
    ) {
    }

    public record WeakLowCandidateDiagnostics(
            List<WeakLowCandidate> candidates,
            int scanRadiusRegions,
            int maxRegionsPerTick,
            int activePlayersIncluded,
            int checkedRegionCount,
            int loadedRegionCount,
            int forecastOnlyRegionCount,
            int skippedRegionCount,
            int duplicateRegionSkippedCount,
            Map<String, Integer> blockedReasonCounts,
            long lastWeakLowSpawnTick,
            long nextScanTick
    ) {
    }

    public record WeakLowSnapshot(
            UUID id,
            RegionInstanceKey regionKey,
            double centerX,
            double centerZ,
            float radius,
            float intensity,
            float supportScore,
            float pressureAnomaly,
            float humidity,
            float cloudWater,
            float cloudCover,
            float convergence,
            float shear,
            float instability,
            float cumulonimbusSupport,
            float nimbostratusSupport,
            float weatherCellSupport,
            String blockedReason,
            int ageTicks,
            int lifetimeTicks,
            String decayReason,
            boolean promotedToCycloneSeed
    ) {
    }
}
