package net.Gabou.projectatmosphere.modules.weathercell;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class WeatherCellManager {
    static final int TICK_INTERVAL = 20;
    private static final int FORMATION_INTERVAL = 600;

    private static final WeatherCellFormationController FORMATION = new WeatherCellFormationController();
    private static final WeatherCellMotionController MOTION = new WeatherCellMotionController();
    private static final WeatherCellLifecycleController LIFECYCLE = new WeatherCellLifecycleController();

    private static long nextTick;
    private static long nextFormationTick;
    private static long lastFormationAttemptTick = -1L;
    private static long lastWeatherCellSpawnTick = -1L;

    private WeatherCellManager() {
    }

    public static void tick(ServerLevel level) {
        if (level == null || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        long now = level.getGameTime();
        if (now < nextTick) {
            return;
        }
        nextTick = now + TICK_INTERVAL;

        WeatherCellSavedData data = WeatherCellSavedData.get(level);
        boolean changed = false;
        List<WeatherCellState> activeCells = activeCells(data.getCells());

        for (WeatherCellState cell : activeCells) {
            changed |= MOTION.tick(level, cell);
            changed |= LIFECYCLE.tick(level, cell);
        }

        for (WeatherCellState cell : new ArrayList<>(data.getCells())) {
            if (cell != null && !cell.isActive()) {
                data.remove(cell.getId());
                changed = true;
            }
        }

        activeCells = activeCells(data.getCells());
        if (now >= nextFormationTick) {
            nextFormationTick = now + FORMATION_INTERVAL;
            lastFormationAttemptTick = now;
            int activeBeforeFormation = activeCells.size();
            changed |= FORMATION.tick(level, data, activeCells);
            if (activeCells(data.getCells()).size() > activeBeforeFormation) {
                lastWeatherCellSpawnTick = now;
            }
        }

        if (changed) {
            data.markChanged();
        }
    }

    public static Collection<WeatherCellState> getCells(ServerLevel level) {
        if (level == null) {
            return List.of();
        }
        return List.copyOf(WeatherCellSavedData.get(level).getCells());
    }

    public static WeatherCellCandidateDiagnostics evaluateCandidateDiagnostics(ServerLevel level) {
        if (level == null) {
            return new WeatherCellCandidateDiagnostics(List.of(), 0, java.util.Map.of(), lastFormationAttemptTick, lastWeatherCellSpawnTick, nextFormationTick);
        }
        return FORMATION.evaluateCandidateDiagnostics(
                level,
                WeatherCellSavedData.get(level).getCells(),
                lastFormationAttemptTick,
                lastWeatherCellSpawnTick,
                nextFormationTick
        );
    }

    public static void resetRuntimeState() {
        nextTick = 0L;
        nextFormationTick = 0L;
        lastFormationAttemptTick = -1L;
        lastWeatherCellSpawnTick = -1L;
    }

    private static List<WeatherCellState> activeCells(Collection<WeatherCellState> cells) {
        List<WeatherCellState> active = new ArrayList<>();
        for (WeatherCellState cell : cells) {
            if (cell != null && cell.isActive()) {
                active.add(cell);
            }
        }
        return active;
    }

    public record WeatherCellCandidateDiagnostics(
            List<WeatherCellCandidateDebug> candidates,
            int checkedRegionCount,
            java.util.Map<String, Integer> blockedReasonCounts,
            long lastFormationAttemptTick,
            long lastWeatherCellSpawnTick,
            long nextFormationTick
    ) {
    }

    public record WeatherCellCandidateDebug(
            net.Gabou.projectatmosphere.util.RegionInstanceKey regionKey,
            net.minecraft.core.BlockPos position,
            boolean eligible,
            float score,
            float formationChance,
            float pressureAnomaly,
            float humidity,
            float minimumHumidity,
            float cloudWater,
            float minimumCloudWater,
            float cloudCover,
            float coverage,
            float convergence,
            float humidityTransport,
            float weakLowOrganization,
            int localActiveCells,
            String blockedReason
    ) {
    }
}
