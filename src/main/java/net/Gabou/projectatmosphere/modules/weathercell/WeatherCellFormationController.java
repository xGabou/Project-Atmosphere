package net.Gabou.projectatmosphere.modules.weathercell;

import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericSupportEvaluator;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.atmosphere.WeakLowManager;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class WeatherCellFormationController {
    private static final int MAX_CANDIDATES = 24;
    private static final int MAX_CANDIDATES_PER_PLAYER = 8;
    private static final int MAX_NEW_CELLS_PER_ATTEMPT = 2;
    private static final int MAX_NEW_CELLS_PER_PLAYER_ATTEMPT = 1;
    private static final int MAX_ACTIVE_WEATHER_CELLS = 48;
    private static final int MAX_ACTIVE_CELLS_PER_REGION = 3;
    private static final int MAX_ACTIVE_CELLS_NEAR_PLAYER = 8;
    private static final double PLAYER_FORMATION_RADIUS = 1100.0D;

    boolean tick(ServerLevel level, WeatherCellSavedData data, Collection<WeatherCellState> activeCells) {
        if (level == null || data == null || level.players().isEmpty()) {
            return false;
        }
        int activeWeatherCells = countActiveWeatherCells(activeCells);
        if (activeWeatherCells >= MAX_ACTIVE_WEATHER_CELLS) {
            return false;
        }
        int maxNewThisAttempt = Math.min(MAX_NEW_CELLS_PER_ATTEMPT, MAX_ACTIVE_WEATHER_CELLS - activeWeatherCells);

        List<FormationCandidate> candidates = collectCandidates(level, activeCells);
        if (candidates.isEmpty()) {
            return false;
        }

        candidates.sort(Comparator
                .comparingInt(FormationCandidate::localActiveCells)
                .thenComparing(Comparator.comparingDouble(FormationCandidate::score).reversed()));
        RandomSource random = level.getRandom();
        boolean changed = false;
        int formed = 0;
        Map<UUID, Integer> spawnedByPlayer = new HashMap<>();
        for (FormationCandidate candidate : candidates) {
            if (formed >= maxNewThisAttempt) {
                break;
            }
            if (spawnedByPlayer.getOrDefault(candidate.playerId(), 0) >= MAX_NEW_CELLS_PER_PLAYER_ATTEMPT) {
                continue;
            }
            if (random.nextFloat() > candidate.formationChance()) {
                continue;
            }
            WeatherCellState cell = createRainCell(level, candidate, random);
            data.add(cell);
            formed++;
            spawnedByPlayer.merge(candidate.playerId(), 1, Integer::sum);
            changed = true;
        }
        return changed;
    }

    WeatherCellManager.WeatherCellCandidateDiagnostics evaluateCandidateDiagnostics(
            ServerLevel level,
            Collection<WeatherCellState> activeCells,
            long lastFormationAttemptTick,
            long lastWeatherCellSpawnTick,
            long nextFormationTick
    ) {
        if (level == null) {
            return new WeatherCellManager.WeatherCellCandidateDiagnostics(List.of(), 0, Map.of(), lastFormationAttemptTick, lastWeatherCellSpawnTick, nextFormationTick);
        }
        Map<String, Integer> blockedReasons = new HashMap<>();
        if (level.players().isEmpty()) {
            blockedReasons.put("no players", 1);
            return new WeatherCellManager.WeatherCellCandidateDiagnostics(List.of(), 0, Map.copyOf(blockedReasons), lastFormationAttemptTick, lastWeatherCellSpawnTick, nextFormationTick);
        }
        int activeWeatherCells = countActiveWeatherCells(activeCells);
        if (activeWeatherCells >= MAX_ACTIVE_WEATHER_CELLS) {
            blockedReasons.put("global weather cell cap reached", 1);
            return new WeatherCellManager.WeatherCellCandidateDiagnostics(List.of(), 0, Map.copyOf(blockedReasons), lastFormationAttemptTick, lastWeatherCellSpawnTick, nextFormationTick);
        }

        Map<RegionInstanceKey, Integer> regionalCounts = countCellsByCurrentRegion(activeCells);
        Set<RegionInstanceKey> keys = collectCandidateKeys(level);
        List<WeatherCellManager.WeatherCellCandidateDebug> candidates = new ArrayList<>();
        for (RegionInstanceKey key : keys) {
            RegionAtmosphereState state = AtmosphericStateRegistry.getState(key);
            if (state == null) {
                blockedReasons.merge("missing live state", 1, Integer::sum);
                continue;
            }
            ServerPlayer nearestPlayer = nearestPlayer(level, state.getPosition());
            if (nearestPlayer == null) {
                blockedReasons.merge("not near player", 1, Integer::sum);
                continue;
            }
            if (regionalCounts.getOrDefault(key, 0) >= MAX_ACTIVE_CELLS_PER_REGION) {
                blockedReasons.merge("regional cell cap reached", 1, Integer::sum);
                continue;
            }
            int localActiveCells = countCellsNear(nearestPlayer, activeCells);
            if (localActiveCells >= MAX_ACTIVE_CELLS_NEAR_PLAYER) {
                blockedReasons.merge("near-player cell cap reached", 1, Integer::sum);
                continue;
            }
            WeatherCellManager.WeatherCellCandidateDebug candidate = evaluateDebug(key, state, activeCells, localActiveCells);
            if (candidate == null) {
                blockedReasons.merge("candidate unavailable", 1, Integer::sum);
                continue;
            }
            candidates.add(candidate);
            blockedReasons.merge(candidate.blockedReason(), 1, Integer::sum);
        }
        candidates.sort(Comparator.comparing(WeatherCellManager.WeatherCellCandidateDebug::score).reversed());
        int maxCandidates = Math.min(MAX_CANDIDATES, Math.max(1, level.players().size() * MAX_CANDIDATES_PER_PLAYER));
        if (candidates.size() > maxCandidates) {
            candidates = new ArrayList<>(candidates.subList(0, maxCandidates));
        }
        return new WeatherCellManager.WeatherCellCandidateDiagnostics(
                List.copyOf(candidates),
                keys.size(),
                Map.copyOf(blockedReasons),
                lastFormationAttemptTick,
                lastWeatherCellSpawnTick,
                nextFormationTick
        );
    }

    private static List<FormationCandidate> collectCandidates(ServerLevel level, Collection<WeatherCellState> activeCells) {
        Map<RegionInstanceKey, Integer> regionalCounts = countCellsByCurrentRegion(activeCells);
        Set<RegionInstanceKey> keys = collectCandidateKeys(level);

        List<FormationCandidate> candidates = new ArrayList<>();
        for (RegionInstanceKey key : keys) {
            RegionAtmosphereState state = AtmosphericStateRegistry.getState(key);
            ServerPlayer nearestPlayer = nearestPlayer(level, state == null ? null : state.getPosition());
            if (state == null || nearestPlayer == null) {
                continue;
            }
            if (regionalCounts.getOrDefault(key, 0) >= MAX_ACTIVE_CELLS_PER_REGION) {
                continue;
            }
            int localActiveCells = countCellsNear(nearestPlayer, activeCells);
            if (localActiveCells >= MAX_ACTIVE_CELLS_NEAR_PLAYER) {
                continue;
            }
            FormationCandidate candidate = evaluate(key, state, activeCells, nearestPlayer, localActiveCells);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        candidates.sort(Comparator
                .comparingInt(FormationCandidate::localActiveCells)
                .thenComparing(Comparator.comparingDouble(FormationCandidate::score).reversed()));
        int maxCandidates = Math.min(MAX_CANDIDATES, Math.max(1, level.players().size() * MAX_CANDIDATES_PER_PLAYER));
        if (candidates.size() > maxCandidates) {
            return new ArrayList<>(candidates.subList(0, maxCandidates));
        }
        return candidates;
    }

    private static FormationCandidate evaluate(RegionInstanceKey key,
                                               RegionAtmosphereState state,
                                               Collection<WeatherCellState> activeCells,
                                               ServerPlayer nearestPlayer,
                                               int localActiveCells) {
        WeatherCellManager.WeatherCellCandidateDebug debug = evaluateDebug(key, state, activeCells, localActiveCells);
        if (debug == null || !debug.eligible()) {
            return null;
        }
        return new FormationCandidate(
                key,
                nearestPlayer.getUUID(),
                localActiveCells,
                state,
                debug.score(),
                debug.formationChance(),
                Mth.clamp(AtmosphericSupportEvaluator.evaluate(key, state).rainCellSustain()
                        + WeakLowManager.weatherCellBoost(key, state.getPosition()).evolutionBoost() * 0.50F, 0.0F, 1.0F),
                debug.pressureAnomaly(),
                Mth.clamp(debug.convergence() * 0.65F + Math.max(0.0F, debug.humidityTransport()) * 8.0F, 0.0F, 1.0F)
        );
    }

    private static WeatherCellManager.WeatherCellCandidateDebug evaluateDebug(RegionInstanceKey key,
                                                                              RegionAtmosphereState state,
                                                                              Collection<WeatherCellState> activeCells,
                                                                              int localActiveCells) {
        AtmosphericSupportEvaluator.Support support = AtmosphericSupportEvaluator.evaluate(key, state);
        float coverage = WeatherCellSupport.estimateCellCoverage(state.getPosition(), activeCells);
        WeakLowManager.WeatherCellBoost lowBoost = WeakLowManager.weatherCellBoost(key, state.getPosition());

        float score = Mth.clamp(support.rainCellFormationScore(coverage) + lowBoost.formationBoost(), -1.0F, 1.0F);
        float minimumHumidity = lowBoost.organization() >= 0.35F ? 0.72F : 0.78F;
        float minimumCloudWater = lowBoost.organization() >= 0.35F ? 0.18F : 0.22F;
        String blockedReason = "none";
        if (support.humidity() < minimumHumidity) {
            blockedReason = "humidity too low";
        } else if (support.cloudWater() < minimumCloudWater) {
            blockedReason = "cloud water too low";
        } else if (coverage >= 0.58F) {
            blockedReason = "coverage too high";
        } else if (score < AtmosphericSupportEvaluator.RAIN_CELL_FORMATION_THRESHOLD) {
            blockedReason = "support score too low";
        }

        float pressureAnomaly = 1013.25F - support.pressure();
        float formationChance = Mth.clamp(0.08F + score * 0.28F + lowBoost.chanceBoost(), 0.08F, 0.38F);
        return new WeatherCellManager.WeatherCellCandidateDebug(
                key,
                state.getPosition(),
                "none".equals(blockedReason),
                score,
                formationChance,
                pressureAnomaly,
                support.humidity(),
                minimumHumidity,
                support.cloudWater(),
                minimumCloudWater,
                support.cloudCover(),
                coverage,
                support.windConvergence(),
                support.humidityTransport(),
                lowBoost.organization(),
                localActiveCells,
                blockedReason
        );
    }

    private static Set<RegionInstanceKey> collectCandidateKeys(ServerLevel level) {
        Set<RegionInstanceKey> keys = new HashSet<>(AtmosphericStateRegistry.getActiveStates());
        if (keys.isEmpty()) {
            for (ServerPlayer player : level.players()) {
                keys.add(RegionInstanceKey.from(player.blockPosition()));
            }
        }
        return keys;
    }

    private static WeatherCellState createRainCell(ServerLevel level, FormationCandidate candidate, RandomSource random) {
        BlockPos anchor = candidate.state().getPosition();
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = random.nextDouble() * 180.0D;
        Vec3 center = new Vec3(
                anchor.getX() + Math.cos(angle) * distance,
                anchor.getY(),
                anchor.getZ() + Math.sin(angle) * distance
        );
        float intensity = Mth.clamp(0.18F + candidate.score() * 0.32F, 0.16F, 0.48F);
        float radius = Mth.clamp(180.0F + candidate.score() * 220.0F + random.nextFloat() * 80.0F, 180.0F, 520.0F);
        int lifetime = 20 * (420 + random.nextInt(420));
        return new WeatherCellState(
                null,
                WeatherCellType.RAIN_CELL,
                candidate.regionKey(),
                center,
                radius,
                intensity,
                candidate.state().getHumidity(),
                candidate.instability(),
                candidate.pressureAnomaly(),
                candidate.windInfluence(),
                candidate.state().getCloudWater(),
                intensity * 0.55F,
                0,
                lifetime,
                true
        );
    }

    private static int countActiveWeatherCells(Collection<WeatherCellState> activeCells) {
        int count = 0;
        for (WeatherCellState cell : activeCells) {
            if (cell != null && cell.isActive()
                    && (cell.getType() == WeatherCellType.RAIN_CELL
                    || cell.getType() == WeatherCellType.THUNDERSTORM
                    || cell.getType() == WeatherCellType.SUPERCELL)) {
                count++;
            }
        }
        return count;
    }

    private static ServerPlayer nearestPlayer(ServerLevel level, BlockPos pos) {
        if (pos == null) {
            return null;
        }
        double maxDistanceSq = PLAYER_FORMATION_RADIUS * PLAYER_FORMATION_RADIUS;
        ServerPlayer nearest = null;
        double nearestDistanceSq = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - pos.getX();
            double dz = player.getZ() - pos.getZ();
            double distanceSq = dx * dx + dz * dz;
            if (distanceSq <= maxDistanceSq && distanceSq < nearestDistanceSq) {
                nearest = player;
                nearestDistanceSq = distanceSq;
            }
        }
        return nearest;
    }

    private static Map<RegionInstanceKey, Integer> countCellsByCurrentRegion(Collection<WeatherCellState> activeCells) {
        Map<RegionInstanceKey, Integer> counts = new HashMap<>();
        if (activeCells == null) {
            return counts;
        }
        for (WeatherCellState cell : activeCells) {
            if (cell == null || !cell.isActive()) {
                continue;
            }
            RegionInstanceKey key = WeatherCellSupport.currentRegionKey(cell);
            if (key != null) {
                counts.merge(key, 1, Integer::sum);
            }
        }
        return counts;
    }

    private static int countCellsNear(ServerPlayer player, Collection<WeatherCellState> activeCells) {
        if (player == null || activeCells == null || activeCells.isEmpty()) {
            return 0;
        }
        double maxDistanceSq = PLAYER_FORMATION_RADIUS * PLAYER_FORMATION_RADIUS;
        int count = 0;
        for (WeatherCellState cell : activeCells) {
            if (cell == null || !cell.isActive() || cell.getCenter() == null) {
                continue;
            }
            double dx = cell.getCenter().x() - player.getX();
            double dz = cell.getCenter().z() - player.getZ();
            if (dx * dx + dz * dz <= maxDistanceSq) {
                count++;
            }
        }
        return count;
    }

    private record FormationCandidate(
            RegionInstanceKey regionKey,
            UUID playerId,
            int localActiveCells,
            RegionAtmosphereState state,
            float score,
            float formationChance,
            float instability,
            float pressureAnomaly,
            float windInfluence
    ) {
    }
}
