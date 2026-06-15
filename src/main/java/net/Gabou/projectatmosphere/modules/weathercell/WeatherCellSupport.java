package net.Gabou.projectatmosphere.modules.weathercell;

import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;

final class WeatherCellSupport {
    private WeatherCellSupport() {
    }

    static float estimateCellCoverage(BlockPos position, Collection<WeatherCellState> cells) {
        if (position == null || cells == null || cells.isEmpty()) {
            return 0.0F;
        }
        float coverage = 0.0F;
        for (WeatherCellState cell : cells) {
            if (cell == null || !cell.isActive()) {
                continue;
            }
            Vec3 center = cell.getCenter();
            double dx = center.x() - position.getX();
            double dz = center.z() - position.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            double range = Math.max(1.0F, cell.getRadius());
            if (distance <= range) {
                float proximity = 1.0F - (float) (distance / range);
                coverage += proximity * Math.max(0.2F, cell.getIntensity());
            }
        }
        return Mth.clamp(coverage, 0.0F, 1.5F);
    }

    static RegionInstanceKey currentRegionKey(WeatherCellState cell) {
        if (cell == null || cell.getCenter() == null) {
            return null;
        }
        return RegionInstanceKey.from(BlockPos.containing(cell.getCenter()));
    }

    static RegionAtmosphereState currentAtmosphere(WeatherCellState cell) {
        if (cell == null || cell.getCenter() == null) {
            return null;
        }
        RegionInstanceKey key = currentRegionKey(cell);
        RegionAtmosphereState state = AtmosphericStateRegistry.getState(key);
        if (state != null) {
            return state;
        }
        Vec3 center = cell.getCenter();
        return AtmosphericStateRegistry.findNearest(center.x(), center.z());
    }
}
