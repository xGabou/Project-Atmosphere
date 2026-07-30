package net.Gabou.projectatmosphere.modules.atmosphere;

import net.Gabou.projectatmosphere.async.PoolType;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.ocean.OceanBasinManager;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

public final class CycloneManager {
    private static final List<Cyclone> ACTIVE_CYCLONES = new CopyOnWriteArrayList<>();
    private static final Map<UUID, CycloneSnapshot> ACTIVE_SNAPSHOTS = new ConcurrentHashMap<>();
    private static final long COOLDOWN_TICKS = 24000L * 4;
    private static final int MAX_ACTIVE_CYCLONES = 4;
    private static final float MIN_SEED_DISTANCE_BLOCKS = 900f;
    private static final float SEED_SUPPORT_THRESHOLD = 0.52f;
    private static final float ORGANIZED_SUPPORT_THRESHOLD = 0.62f;
    private static final float SEVERE_SUPPORT_THRESHOLD = 0.70f;
    private static long lastSpawnTick = -COOLDOWN_TICKS;
    private static long lastMidnightTick = -1L;

    private CycloneManager() {
    }

    public static void initialize(ServerLevel level) {
        ACTIVE_CYCLONES.clear();
        ACTIVE_SNAPSHOTS.clear();
        lastSpawnTick = level.getDayTime();
        lastMidnightTick = -1L;
        spawnInitialCyclones(level);
    }

    public static List<CycloneSnapshot> getActiveCycloneSnapshots() {
        if (ACTIVE_SNAPSHOTS.isEmpty()) {
            return List.of();
        }
        return List.copyOf(ACTIVE_SNAPSHOTS.values());
    }

    public static int maxActiveCyclones() {
        return MAX_ACTIVE_CYCLONES;
    }

    public static long spawnCooldownRemainingTicks(long dayTime) {
        return Math.max(0L, COOLDOWN_TICKS - (dayTime - lastSpawnTick));
    }

    public static long lastCycloneSeedTick() {
        return lastSpawnTick;
    }

    public static float minSeedDistanceBlocks() {
        return MIN_SEED_DISTANCE_BLOCKS;
    }

    public static SeedSpawnCheck evaluateSeedSpawn(RegionAtmosphereState state, long gameTime, long dayTime) {
        CycloneSupport support = evaluateCycloneSupport(state, gameTime);
        List<String> blockers = new ArrayList<>();
        if (state == null) {
            blockers.add("no live region state");
            return new SeedSpawnCheck(false, support, blockers);
        }
        AtmosphericSupportEvaluator.Support atmospheric = AtmosphericSupportEvaluator.evaluate(state.getRegionId(), state);
        if (atmospheric.humidity() < 0.70f) {
            blockers.add("humidity too low");
        }
        if (atmospheric.cloudWater() < 0.25f) {
            blockers.add("cloud water too low");
        }
        if (support.pressureAnomalyHpa() < 8f) {
            blockers.add("pressure anomaly too weak");
        }
        if (support.seedSupport() < SEED_SUPPORT_THRESHOLD) {
            blockers.add("support score too low");
        }
        if (support.convergenceSupport() < 0.18f
                && atmospheric.stormPressureSupport() < 0.22f
                && support.oceanMoistureBonus() < 0.12f
                && support.weakLowSupport() < 0.25f) {
            blockers.add("no organizing source");
        }
        if (ACTIVE_CYCLONES.size() >= MAX_ACTIVE_CYCLONES) {
            blockers.add("regional cyclone cap reached");
        }
        if (spawnCooldownRemainingTicks(dayTime) > 0L) {
            blockers.add("cooldown active");
        }
        if (isTooCloseToActiveCyclone(state)) {
            blockers.add("too close to another cyclone");
        }
        return new SeedSpawnCheck(blockers.isEmpty() && support.seedEligible(), support, blockers);
    }

    public static CycloneSnapshot nearestCyclone(BlockPos pos) {
        if (pos == null || ACTIVE_SNAPSHOTS.isEmpty()) {
            return null;
        }
        CycloneSnapshot nearest = null;
        double best = Double.MAX_VALUE;
        for (CycloneSnapshot snapshot : ACTIVE_SNAPSHOTS.values()) {
            double dx = pos.getX() - snapshot.centerX();
            double dz = pos.getZ() - snapshot.centerZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance < best) {
                best = distance;
                nearest = snapshot;
            }
        }
        return nearest;
    }

    public static double distanceTo(CycloneSnapshot snapshot, BlockPos pos) {
        if (snapshot == null || pos == null) {
            return Double.NaN;
        }
        double dx = pos.getX() - snapshot.centerX();
        double dz = pos.getZ() - snapshot.centerZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static float estimatePressureDelta(RegionAtmosphereState state, long gameTime) {
        if (state == null || state.getPosition() == null || ACTIVE_SNAPSHOTS.isEmpty()) {
            return 0f;
        }
        return estimatePressureDelta(state.getPosition(), state.getPressure(), state.getTargetPressure(gameTime));
    }

    public static float estimatePressureDelta(BlockPos position, float currentPressure, float targetPressure) {
        if (position == null || ACTIVE_SNAPSHOTS.isEmpty()) {
            return 0f;
        }
        float total = 0f;
        for (CycloneSnapshot cyclone : ACTIVE_SNAPSHOTS.values()) {
            double dx = position.getX() - cyclone.centerX();
            double dz = position.getZ() - cyclone.centerZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            float influence = (float) (1d - (distance / Math.max(1f, cyclone.radius())));
            if (influence <= 0f) {
                continue;
            }
            float scaledInfluence = influence * cyclone.intensity();
            float currentDrop = Math.max(0.0f, targetPressure - currentPressure);
            float allowedDrop = Mth.lerp(scaledInfluence, 12.0f, 38.0f);
            float remainingDrop = Math.max(0.0f, allowedDrop - currentDrop);
            float pressurePulse = Math.min(8f, cyclone.corePressureDrop()) * scaledInfluence * 0.045f;
            total -= Math.min(remainingDrop, pressurePulse);
        }
        return total;
    }

    public static CycloneSupport evaluateCycloneSupport(RegionAtmosphereState state, long gameTime) {
        if (state == null || state.getRegionId() == null) {
            return CycloneSupport.empty();
        }
        AtmosphericSupportEvaluator.Support support = AtmosphericSupportEvaluator.evaluate(state.getRegionId(), state);
        float targetPressure = state.getTargetPressure(gameTime);
        float deficitToTarget = Math.max(0f, targetPressure - state.getPressure());
        float deficitToNormal = Math.max(0f, 1013.25f - state.getPressure());
        float pressureAnomaly = Math.max(deficitToTarget, deficitToNormal);
        float pressureScore = ramp(pressureAnomaly, 6f, 18f);
        float humidityScore = ramp(support.humidity(), 0.70f, 0.88f);
        float cloudWaterScore = ramp(support.cloudWater(), 0.25f, 0.65f);
        float convergenceScore = Mth.clamp(
                support.windConvergence() * 0.72f
                        + support.windStrengthSupport() * 0.18f
                        + Math.max(0f, support.humidityTransport()) * 3.0f,
                0f,
                1f
        );
        float oceanFlux = OceanBasinManager.estimateHumidityFlux(state.getRegionId(), state.getHumidity());
        float oceanPressureDelta = OceanBasinManager.estimatePressureDelta(state.getRegionId(), state.getPressure());
        float oceanMoistureBonus = Mth.clamp(
                Math.max(0f, oceanFlux) * 80f + Math.max(0f, -oceanPressureDelta) * 45f,
                0f,
                1f
        );
        float weakLowSupport = WeakLowManager.organizationFor(state.getRegionId());

        float seedSupport = Mth.clamp(
                humidityScore * 0.22f
                        + cloudWaterScore * 0.22f
                        + pressureScore * 0.26f
                        + support.stormPressureSupport() * 0.14f
                        + convergenceScore * 0.12f
                        + oceanMoistureBonus * 0.04f
                        + weakLowSupport * 0.08f,
                0f,
                1f
        );
        float intensificationSupport = Mth.clamp(
                humidityScore * 0.20f
                        + cloudWaterScore * 0.22f
                        + pressureScore * 0.16f
                        + convergenceScore * 0.14f
                        + support.rainSupport() * 0.08f
                        + support.thunderstormSupport() * 0.16f
                        + oceanMoistureBonus * 0.04f
                        + weakLowSupport * 0.10f,
                0f,
                1f
        );
        float severeSupport = Mth.clamp(
                support.thunderstormSupport() * 0.42f
                        + support.supercellSupport() * 0.34f
                        + pressureScore * 0.10f
                        + convergenceScore * 0.14f
                        + weakLowSupport * 0.04f,
                0f,
                1f
        );
        boolean seedEligible = support.humidity() >= 0.70f
                && support.cloudWater() >= 0.25f
                && pressureAnomaly >= 8f
                && seedSupport >= SEED_SUPPORT_THRESHOLD
                && (convergenceScore >= 0.18f || support.stormPressureSupport() >= 0.22f || oceanMoistureBonus >= 0.12f || weakLowSupport >= 0.25f);
        return new CycloneSupport(
                seedEligible,
                seedSupport,
                intensificationSupport,
                severeSupport,
                pressureAnomaly,
                convergenceScore,
                oceanMoistureBonus,
                support.thunderstormSupport(),
                support.supercellSupport(),
                weakLowSupport
        );
    }

    public static CompoundTag savePersistentState() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("LastSpawnTick", lastSpawnTick);
        tag.putLong("LastMidnightTick", lastMidnightTick);
        ListTag cyclones = new ListTag();
        for (Cyclone cyclone : ACTIVE_CYCLONES) {
            if (cyclone != null) {
                cyclones.add(cyclone.save());
            }
        }
        tag.put("Cyclones", cyclones);
        return tag;
    }

    public static void loadPersistentState(ServerLevel level, CompoundTag tag) {
        ACTIVE_CYCLONES.clear();
        ACTIVE_SNAPSHOTS.clear();
        if (tag == null || tag.isEmpty()) {
            lastSpawnTick = level == null ? -COOLDOWN_TICKS : level.getDayTime();
            lastMidnightTick = -1L;
            return;
        }
        lastSpawnTick = tag.getLong("LastSpawnTick");
        lastMidnightTick = tag.getLong("LastMidnightTick");
        ListTag cyclones = tag.getList("Cyclones", Tag.TAG_COMPOUND);
        for (int i = 0; i < cyclones.size(); i++) {
            Cyclone cyclone = Cyclone.load(cyclones.getCompound(i));
            if (cyclone != null) {
                ACTIVE_CYCLONES.add(cyclone);
                ACTIVE_SNAPSHOTS.put(cyclone.id, cyclone.snapshot());
            }
        }
    }

    public static void update(ServerLevel level) {
        if (AtmosphericStateRegistry.isEmpty()) {
            return;
        }

        long dayTime = level.getDayTime();
        if (dayTime % 24000L == 0 && lastMidnightTick != dayTime) {
            onMidnight(level);
            lastMidnightTick = dayTime;
        }

        List<RegionAtmosphereState> snapshot = AtmosphericStateRegistry.snapshot();
        for (Cyclone cyclone : new ArrayList<>(ACTIVE_CYCLONES)) {
            AsyncAtmosphereService.runWithCallback(
                    PoolType.WEATHER,
                    () -> cyclone.tick(snapshot, dayTime),
                    result -> {
                        if (result == null) {
                            return;
                        }
                        CycloneImpactApplier.apply(result);
                        if (result.remove()) {
                            ACTIVE_CYCLONES.remove(cyclone);
                            ACTIVE_SNAPSHOTS.remove(cyclone.id);
                        } else {
                            ACTIVE_SNAPSHOTS.put(cyclone.id, cyclone.snapshot());
                        }
                    }
            );
        }
    }

    public static void onMidnight(ServerLevel level) {
        if (ACTIVE_CYCLONES.size() >= MAX_ACTIVE_CYCLONES) {
            return;
        }
        long now = level.getDayTime();
        if (!cooldownPassed(now)) {
            return;
        }
        // Precompute nearby region candidates
        List<RegionAtmosphereState> candidates = findCycloneSupportStates(level);

        if (candidates.isEmpty()) {
            return; // no valid region nearby → skip all
        }
        spawnCyclone(level, candidates);
    }

    private static boolean cooldownPassed(long now) {
        return now - lastSpawnTick >= COOLDOWN_TICKS;
    }

    private static void spawnInitialCyclones(ServerLevel level) {
        RandomSource random = level.random;

        // Precompute nearby region candidates
        List<RegionAtmosphereState> candidates = findCycloneSupportStates(level);

        if (candidates.isEmpty()) {
            return; // no valid region nearby → skip all
        }

        if (random.nextFloat() > 0.35F) {
            return;
        }
        spawnCyclone(level, candidates);
    }


    private static void spawnCyclone(ServerLevel level, List<RegionAtmosphereState> candidates) {
        RandomSource random = level.random;

        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        RegionAtmosphereState state = candidates.get(Math.min(random.nextInt(Math.min(3, candidates.size())), candidates.size() - 1));
        CycloneSupport support = evaluateCycloneSupport(state, level.getGameTime());

        float radius = Mth.lerp(support.seedSupport(), 220f, 520f) + random.nextFloat() * 80f;
        float intensity = Mth.clamp(0.16f + support.seedSupport() * 0.26f + random.nextFloat() * 0.05f, 0.16f, 0.48f);
        float pressureDrop = Mth.clamp(3.0f + support.seedSupport() * 7.0f + support.intensificationSupport() * 4.0f, 3.0f, 14.0f);
        long lifetime = 24000L + random.nextInt(24000);

        Cyclone cyclone = new Cyclone(
                UUID.randomUUID(),
                new Vec2(state.getPosition().getX(), state.getPosition().getZ()),
                radius,
                intensity,
                pressureDrop,
                lifetime,
                0
        );
        ACTIVE_CYCLONES.add(cyclone);
        ACTIVE_SNAPSHOTS.put(cyclone.id, cyclone.snapshot());
        WeakLowManager.markPromoted(state);

        lastSpawnTick = level.getDayTime();
    }
    private static List<RegionAtmosphereState> findCycloneSupportStates(ServerLevel level) {
        long gameTime = level.getGameTime();
        return CandidateRegionScanner.scan(level).regions().stream()
                .map(CandidateRegionScanner.CandidateRegion::state)
                .filter(state -> {
                    CycloneSupport support = evaluateCycloneSupport(state, gameTime);
                    return support.seedEligible() && !isTooCloseToActiveCyclone(state);
                })
                .sorted((a, b) -> Float.compare(
                        evaluateCycloneSupport(b, gameTime).seedSupport(),
                        evaluateCycloneSupport(a, gameTime).seedSupport()
                ))
                .toList();
    }

    public static List<CycloneCandidateDebug> evaluateCycloneCandidates(ServerLevel level) {
        return evaluateCycloneCandidateDiagnostics(level).candidates();
    }

    public static CycloneCandidateDiagnostics evaluateCycloneCandidateDiagnostics(ServerLevel level) {
        if (level == null) {
            return new CycloneCandidateDiagnostics(
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
                    lastSpawnTick,
                    WeakLowManager.lastWeakLowSpawnTick()
            );
        }
        long gameTime = level.getGameTime();
        long dayTime = level.getDayTime();
        CandidateRegionScanner.ScanResult scan = CandidateRegionScanner.scan(level);
        Map<String, Integer> blockedReasons = new ConcurrentHashMap<>();
        List<CycloneCandidateDebug> candidates = new ArrayList<>();
        for (CandidateRegionScanner.CandidateRegion region : scan.regions()) {
            RegionAtmosphereState state = region.state();
            CycloneSupport support = evaluateCycloneSupport(state, gameTime);
            SeedSpawnCheck spawnCheck = evaluateSeedSpawn(state, gameTime, dayTime);
            AtmosphericSupportEvaluator.Support atmospheric = AtmosphericSupportEvaluator.evaluate(state.getRegionId(), state);
            CycloneCandidateDebug candidate = new CycloneCandidateDebug(
                    state.getRegionId(),
                    state.getPosition(),
                    spawnCheck.canSpawn(),
                    support.seedSupport(),
                    support.intensificationSupport(),
                    support.severeSupport(),
                    support.pressureAnomalyHpa(),
                    atmospheric.humidity(),
                    atmospheric.cloudWater(),
                    atmospheric.cloudCover(),
                    support.convergenceSupport(),
                    support.oceanMoistureBonus(),
                    support.thunderstormSupport(),
                    support.supercellSupport(),
                    support.weakLowSupport(),
                    spawnCheck.blockedReasonSummary()
            );
            candidates.add(candidate);
            blockedReasons.merge(candidate.blockedReason(), 1, Integer::sum);
        }
        candidates.sort(Comparator.comparing(CycloneCandidateDebug::seedSupport).reversed());
        if (candidates.size() > 24) {
            candidates = new ArrayList<>(candidates.subList(0, 24));
        }
        return new CycloneCandidateDiagnostics(
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
                lastSpawnTick,
                WeakLowManager.lastWeakLowSpawnTick()
        );
    }

    private static boolean isTooCloseToActiveCyclone(RegionAtmosphereState state) {
        if (state == null || state.getPosition() == null || ACTIVE_SNAPSHOTS.isEmpty()) {
            return false;
        }
        for (CycloneSnapshot cyclone : ACTIVE_SNAPSHOTS.values()) {
            double dx = state.getPosition().getX() - cyclone.centerX();
            double dz = state.getPosition().getZ() - cyclone.centerZ();
            if (Math.sqrt(dx * dx + dz * dz) < MIN_SEED_DISTANCE_BLOCKS) {
                return true;
            }
        }
        return false;
    }

    private static float ramp(float value, float startsAt, float fullAt) {
        if (fullAt <= startsAt) {
            return value >= fullAt ? 1f : 0f;
        }
        return Mth.clamp((value - startsAt) / (fullAt - startsAt), 0f, 1f);
    }

    public record CycloneSupport(
            boolean seedEligible,
            float seedSupport,
            float intensificationSupport,
            float severeSupport,
            float pressureAnomalyHpa,
            float convergenceSupport,
            float oceanMoistureBonus,
            float thunderstormSupport,
            float supercellSupport,
            float weakLowSupport
    ) {
        static CycloneSupport empty() {
            return new CycloneSupport(false, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
        }
    }

    public record CycloneCandidateDebug(
            RegionInstanceKey regionKey,
            BlockPos position,
            boolean canSpawn,
            float seedSupport,
            float intensificationSupport,
            float severeSupport,
            float pressureAnomalyHpa,
            float humidity,
            float cloudWater,
            float cloudCover,
            float convergenceSupport,
            float oceanMoistureBonus,
            float thunderstormSupport,
            float supercellSupport,
            float weakLowSupport,
            String blockedReason
    ) {
    }

    public record CycloneCandidateDiagnostics(
            List<CycloneCandidateDebug> candidates,
            int scanRadiusRegions,
            int maxRegionsPerTick,
            int activePlayersIncluded,
            int checkedRegions,
            int loadedRegions,
            int forecastOnlyRegions,
            int skippedRegions,
            int duplicateRegionsSkipped,
            Map<String, Integer> blockedReasonCounts,
            long lastCycloneSeedTick,
            long lastWeakLowTick
    ) {
    }

    public record SeedSpawnCheck(
            boolean canSpawn,
            CycloneSupport support,
            List<String> blockedReasons
    ) {
        public String blockedReasonSummary() {
            if (blockedReasons == null || blockedReasons.isEmpty()) {
                return "none";
            }
            return String.join(", ", blockedReasons);
        }
    }



    private static final class Cyclone {
        private final UUID id;
        private Vec2 center;
        private float radius;
        private float intensity;
        private final float corePressureDrop;
        private long lifetimeTicks;
        private int counter;
        private float lastMoveX;
        private float lastMoveZ;

        private Cyclone(UUID id, Vec2 center, float radius, float intensity, float corePressureDrop, long lifetimeTicks, int counter) {
            this.id = id == null ? UUID.randomUUID() : id;
            this.center = center;
            this.radius = radius;
            this.intensity = intensity;
            this.corePressureDrop = corePressureDrop;
            this.lifetimeTicks = lifetimeTicks;
            this.counter = counter;
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.putFloat("CenterX", center.x);
            tag.putFloat("CenterZ", center.y);
            tag.putFloat("Radius", radius);
            tag.putFloat("Intensity", intensity);
            tag.putFloat("CorePressureDrop", corePressureDrop);
            tag.putLong("LifetimeTicks", lifetimeTicks);
            tag.putInt("Counter", counter);
            return tag;
        }

        private static Cyclone load(CompoundTag tag) {
            if (tag == null || !tag.hasUUID("Id")) {
                return null;
            }
            return new Cyclone(
                    tag.getUUID("Id"),
                    new Vec2(tag.getFloat("CenterX"), tag.getFloat("CenterZ")),
                    tag.getFloat("Radius"),
                    tag.getFloat("Intensity"),
                    tag.getFloat("CorePressureDrop"),
                    tag.getLong("LifetimeTicks"),
                    tag.getInt("Counter")
            );
        }

        private CycloneImpactApplier.CycloneStep tick(List<RegionAtmosphereState> snapshot, long gameTime) {
            List<CycloneImpactApplier.CycloneDelta> deltas = List.of();
            if (counter++ % 20 == 0) {
                deltas = applyEffects(snapshot, gameTime);
            }
            RegionAtmosphereState nearest = findNearest(snapshot, center.x, center.y);
            adjustIntensity(nearest, gameTime);
            drift(nearest, gameTime);
            lifetimeTicks--;
            if (lifetimeTicks <= 0 || intensity < 0.05f) {
                return new CycloneImpactApplier.CycloneStep(true, deltas);
            }
            radius = Mth.clamp(radius + (intensity - 0.38f) * 1.2f, 140f, 620f);
            return new CycloneImpactApplier.CycloneStep(false, deltas);
        }

        private void adjustIntensity(RegionAtmosphereState nearest, long gameTime) {
            CycloneSupport support = evaluateCycloneSupport(nearest, gameTime);
            float organization = support.intensificationSupport();
            float severe = support.severeSupport();
            float seed = support.seedSupport();
            float tendency = -0.00035f;
            if (seed >= SEED_SUPPORT_THRESHOLD) {
                tendency += (seed - SEED_SUPPORT_THRESHOLD) * 0.0012f;
            }
            if (organization >= ORGANIZED_SUPPORT_THRESHOLD) {
                tendency += (organization - ORGANIZED_SUPPORT_THRESHOLD) * 0.0022f;
            }
            if (severe >= SEVERE_SUPPORT_THRESHOLD) {
                tendency += (severe - SEVERE_SUPPORT_THRESHOLD) * 0.0028f;
            }
            if (!support.seedEligible() && organization < 0.40f) {
                tendency -= 0.00045f;
            }
            intensity = Mth.clamp(intensity + tendency, 0.03f, 1.15f);
        }

        private List<CycloneImpactApplier.CycloneDelta> applyEffects(List<RegionAtmosphereState> states, long gameTime) {
            List<CycloneImpactApplier.CycloneDelta> deltas = new ArrayList<>();
            if (states.isEmpty()) {
                return deltas;
            }
            float maxPressureDrop = Math.min(8f, corePressureDrop);
            for (RegionAtmosphereState state : states) {
                double dx = state.getPosition().getX() - center.x;
                double dz = state.getPosition().getZ() - center.y;
                double distance = Math.sqrt(dx * dx + dz * dz);
                float influence = (float) (1d - (distance / radius));
                if (influence <= 0f) {
                    influence = 0f;
                }
                float scaledInfluence = influence * intensity;
                float targetPressure = state.getTargetPressure(gameTime);
                float currentDrop = Math.max(0.0f, targetPressure - state.getPressure());
                float allowedDrop = Mth.lerp(scaledInfluence, 12.0f, 38.0f);
                float remainingDrop = Math.max(0.0f, allowedDrop - currentDrop);
                float pressurePulse = maxPressureDrop * scaledInfluence * 0.045f;
                float pressureDelta = -Math.min(remainingDrop, pressurePulse);
                float humidityDelta = Mth.clamp(scaledInfluence * 0.018f, 0f, 0.018f);
                float temperatureDelta = Mth.clamp(-scaledInfluence * 0.16f, -0.18f, 0f);
                float rainCeil = Mth.clamp(scaledInfluence, 0f, 1f);
                float cloudCeil = Mth.clamp(state.getCloudCover() + scaledInfluence * 0.25f, 0f, 1f);
                deltas.add(new CycloneImpactApplier.CycloneDelta(state.getKey(), temperatureDelta, humidityDelta, pressureDelta, rainCeil, cloudCeil));
            }
            return deltas;
        }

        private void drift(RegionAtmosphereState nearest, long gameTime) {
            if (nearest == null) {
                return;
            }
            WindVector wind = ForecastOrchestrator.getWind(nearest.getKey(), gameTime);
            float speed = Math.max(0.05f, wind.baseSpeed() * 0.02f);
            float angle = wind.angleRadians();
            float dx = (float) Math.sin(angle) * speed;
            float dz = (float) Math.cos(angle) * speed;
            lastMoveX = dx;
            lastMoveZ = dz;
            center = center.add(new Vec2(dx, dz));
        }

        private RegionAtmosphereState findNearest(List<RegionAtmosphereState> states, double x, double z) {
            RegionAtmosphereState nearest = null;
            double best = Double.MAX_VALUE;
            for (RegionAtmosphereState state : states) {
                double dist = state.distanceTo(x, z);
                if (dist < best) {
                    best = dist;
                    nearest = state;
                }
            }
            return nearest;
        }

        private CycloneSnapshot snapshot() {
            return new CycloneSnapshot(
                    this.id,
                    this.center.x,
                    this.center.y,
                    this.radius,
                    this.intensity,
                    this.corePressureDrop,
                    this.lifetimeTicks,
                    this.counter,
                    this.lastMoveX,
                    this.lastMoveZ
            );
        }
    }

}
