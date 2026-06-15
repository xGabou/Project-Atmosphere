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
            changed |= FORMATION.tick(level, data, activeCells);
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

    public static void resetRuntimeState() {
        nextTick = 0L;
        nextFormationTick = 0L;
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
}
