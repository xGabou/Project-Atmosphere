package net.Gabou.projectatmosphere.clouds.service;

import net.Gabou.projectatmosphere.clouds.simulation.CloudGroupSpawner;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionStateStore;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericSupportEvaluator;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Native PA cloud service.
 * Owns automatic non-severe cloud birth while the existing simulation owns
 * lifecycle, motion, merging, evolution, sync, persistence, and rendering.
 */
final class NativeAtmosphereCloudService implements AtmosphereCloudService {
    private static final int SPAWN_ATTEMPT_INTERVAL_TICKS = 600;
    private static final int MAX_SPAWNS_PER_ATTEMPT = 2;
    private static final int MAX_CANDIDATES_PER_ATTEMPT = 24;
    private static final double PLAYER_SPAWN_RADIUS = 900.0D;
    private static final double NEARBY_CLOUD_RADIUS = 720.0D;
    private static final double SPAWN_POSITION_JITTER = 420.0D;

    private long nextSpawnAttemptTick;

    @Override
    public void onServerStarted(ServerLevel level) {
        nextSpawnAttemptTick = 0L;
    }

    @Override
    public void onServerStopping(ServerLevel level) {
        nextSpawnAttemptTick = 0L;
    }

    @Override
    public boolean shouldTrySpawn(ServerLevel level, int cloudBoosterTicks, boolean wasRegenerating) {
        if (level == null || !level.dimension().equals(Level.OVERWORLD) || level.players().isEmpty()) {
            return false;
        }
        long gameTime = level.getGameTime();
        if (wasRegenerating) {
            nextSpawnAttemptTick = gameTime;
            return true;
        }
        return gameTime >= nextSpawnAttemptTick;
    }

    @Override
    public void trySpawnClouds(ServerLevel level) {
        if (level == null || !level.dimension().equals(Level.OVERWORLD) || level.players().isEmpty()) {
            return;
        }

        long gameTime = level.getGameTime();
        nextSpawnAttemptTick = gameTime + SPAWN_ATTEMPT_INTERVAL_TICKS;

        List<SpawnCandidate> candidates = collectCandidates(level);
        if (candidates.isEmpty()) {
            return;
        }

        candidates.sort(Comparator.comparingDouble(SpawnCandidate::score).reversed());
        RandomSource random = level.getRandom();
        int spawned = 0;
        for (SpawnCandidate candidate : candidates) {
            if (spawned >= MAX_SPAWNS_PER_ATTEMPT) {
                break;
            }
            if (spawned > 0 && candidate.score() < 0.72F) {
                break;
            }
            if (random.nextFloat() > candidate.spawnChance()) {
                continue;
            }
            BlockPos spawnPos = chooseSpawnPosition(candidate.state(), random);
            CloudRegionState created = CloudGroupSpawner.spawnRequestedCloud(level, spawnPos, candidate.cloudTypeId());
            if (created != null) {
                drainCloudWater(candidate.state(), candidate.cloudTypeId());
                spawned++;
            }
        }
    }

    private static List<SpawnCandidate> collectCandidates(ServerLevel level) {
        Set<RegionInstanceKey> activeKeys = new HashSet<>(AtmosphericStateRegistry.getActiveStates());
        if (activeKeys.isEmpty()) {
            activeKeys.addAll(resolvePlayerRegionKeys(level));
        }
        if (activeKeys.isEmpty()) {
            return List.of();
        }

        List<SpawnCandidate> candidates = new ArrayList<>();
        for (RegionInstanceKey key : activeKeys) {
            RegionAtmosphereState state = AtmosphericStateRegistry.getState(key);
            if (state == null || !isNearAnyPlayer(level, state.getPosition())) {
                continue;
            }
            SpawnCandidate candidate = evaluateCandidate(level, key, state);
            if (candidate != null) {
                candidates.add(candidate);
            }
            if (candidates.size() >= MAX_CANDIDATES_PER_ATTEMPT) {
                break;
            }
        }
        return candidates;
    }

    private static Set<RegionInstanceKey> resolvePlayerRegionKeys(ServerLevel level) {
        Set<RegionInstanceKey> keys = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            keys.add(RegionInstanceKey.from(player.blockPosition()));
        }
        return keys;
    }

    private static SpawnCandidate evaluateCandidate(ServerLevel level, RegionInstanceKey key, RegionAtmosphereState state) {
        AtmosphericSupportEvaluator.Support support = AtmosphericSupportEvaluator.evaluate(key, state);
        float existingCoverage = estimateExistingCloudCoverage(level, key, state.getPosition());
        float score = support.cloudBirthScore(existingCoverage);

        if (score < 0.42F || existingCoverage >= 1.0F) {
            return null;
        }

        String cloudTypeId = selectBirthCloudType(score, support.humidity(), support.cloudWater());
        float spawnChance = Mth.clamp(0.18F + score * 0.62F, 0.18F, 0.82F);
        return new SpawnCandidate(state, score, spawnChance, cloudTypeId);
    }

    private static String selectBirthCloudType(float score, float humidity, float cloudWater) {
        if (score >= 0.82F && humidity >= 0.74F && cloudWater >= 0.22F) {
            return "cumulus_mediocris";
        }
        if (score >= 0.60F && humidity >= 0.62F) {
            return "cumulus_humilis";
        }
        return "vapor_cluster";
    }

    private static void drainCloudWater(RegionAtmosphereState state, String cloudTypeId) {
        if (state == null || state.getCloudWater() <= 0.0F) {
            return;
        }
        float drain = switch (cloudTypeId) {
            case "cumulus_mediocris" -> 0.05F;
            case "cumulus_humilis" -> 0.025F;
            default -> 0.01F;
        };
        state.setCloudWater(Math.max(0.0F, state.getCloudWater() - drain));
    }

    private static float estimateExistingCloudCoverage(ServerLevel level, RegionInstanceKey key, BlockPos pos) {
        if (pos == null) {
            return 0.0F;
        }
        float coverage = 0.0F;
        for (CloudRegionState region : CloudRegionStateStore.getActiveRegions(level)) {
            if (region == null || !region.isActive()) {
                continue;
            }
            if (key.equals(region.getSourceRegionKey()) || key.equals(region.getCurrentRegionKey())) {
                coverage += 0.45F;
            }
            Vec3 center = region.getCenter();
            double dx = center.x() - pos.getX();
            double dz = center.z() - pos.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            double range = Math.max(NEARBY_CLOUD_RADIUS, region.getRadius() + 360.0D);
            if (distance <= range) {
                float proximity = 1.0F - (float) (distance / range);
                coverage += proximity * Mth.clamp(region.getCoverage(), 0.0F, 1.0F);
            }
        }
        return Mth.clamp(coverage, 0.0F, 1.5F);
    }

    private static boolean isNearAnyPlayer(ServerLevel level, BlockPos pos) {
        if (pos == null) {
            return false;
        }
        double maxDistanceSq = PLAYER_SPAWN_RADIUS * PLAYER_SPAWN_RADIUS;
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - pos.getX();
            double dz = player.getZ() - pos.getZ();
            if (dx * dx + dz * dz <= maxDistanceSq) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos chooseSpawnPosition(RegionAtmosphereState state, RandomSource random) {
        BlockPos anchor = state.getPosition();
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = random.nextDouble() * SPAWN_POSITION_JITTER;
        int x = Mth.floor(anchor.getX() + Math.cos(angle) * distance);
        int z = Mth.floor(anchor.getZ() + Math.sin(angle) * distance);
        return new BlockPos(x, anchor.getY(), z);
    }

    private record SpawnCandidate(
            RegionAtmosphereState state,
            float score,
            float spawnChance,
            String cloudTypeId
    ) {
    }
}
