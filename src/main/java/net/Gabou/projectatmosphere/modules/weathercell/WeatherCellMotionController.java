package net.Gabou.projectatmosphere.modules.weathercell;

import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

final class WeatherCellMotionController {
    private static final double WIND_DRIFT_SCALE = 0.030D;

    boolean tick(ServerLevel level, WeatherCellState cell) {
        if (level == null || cell == null || !cell.isActive()) {
            return false;
        }
        RegionInstanceKey windRegion = WeatherCellSupport.currentRegionKey(cell);
        WindVector wind = ForecastOrchestrator.getWind(windRegion, level.getGameTime());
        if (wind == null || wind.baseSpeed() <= 0.0F) {
            return false;
        }

        double speed = Math.max(0.0F, wind.baseSpeed()) * WIND_DRIFT_SCALE;
        double angle = wind.angleRadians();
        Vec3 velocity = new Vec3(-Math.sin(angle) * speed, 0.0D, Math.cos(angle) * speed);
        cell.setCenter(cell.getCenter().add(velocity));
        return velocity.lengthSqr() > 0.0000001D;
    }
}
