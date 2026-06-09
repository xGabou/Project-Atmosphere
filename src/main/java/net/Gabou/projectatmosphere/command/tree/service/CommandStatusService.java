package net.Gabou.projectatmosphere.command.tree.service;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandMessages;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandSupport;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneManager;
import net.Gabou.projectatmosphere.modules.tornado.TornadoManager;
import net.Gabou.projectatmosphere.util.UnitFormatter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class CommandStatusService {
    private CommandStatusService() {
    }

    public static int sendStatus(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos pos = PaCommandSupport.sourceBlockPos(source);
        long tick = level.getGameTime();
        Vec3 posVec = source.getPosition();

        float temperature = ForecastOrchestrator.getCurrentTemperature(level, pos, tick);
        float humidity = ForecastOrchestrator.getCurrentHumidity(level, pos, tick);
        float pressure = ForecastOrchestrator.getCurrentPressure(level, pos, tick);
        var wind = ForecastOrchestrator.getWind(pos, tick);
        var phase = ForecastOrchestrator.getWeatherPhase(level, net.Gabou.projectatmosphere.util.RegionInstanceKey.from(pos), tick);
        int tornadoes = TornadoManager.getActiveTornadoes().size();
        int hurricanes = HurricaneManager.getActiveHurricanes().size();
        boolean simpleClouds = AtmosphereCloudServices.isSimpleCloudsLoaded();

        PaCommandMessages.success(
                source,
                false,
                "System status",
                "Dimension: " + level.dimension().location(),
                "Position: " + pos.getX() + " " + pos.getY() + " " + pos.getZ(),
                "Forecast: " + UnitFormatter.formatTemperature(temperature)
                        + ", " + UnitFormatter.formatHumidity(humidity)
                        + ", " + UnitFormatter.formatPressure(pressure),
                "Wind: " + PaCommandSupport.formatWind(wind),
                "Weather phase: " + phase,
                "Simple Clouds: " + (simpleClouds ? "loaded" : "disabled"),
                "Tornadoes: " + tornadoes,
                "Hurricanes: " + hurricanes,
                "Debug mode: " + ProjectAtmosphere.DEBUG_MODE
        );
        return 1;
    }
}
