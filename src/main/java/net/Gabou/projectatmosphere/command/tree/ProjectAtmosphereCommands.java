package net.Gabou.projectatmosphere.command.tree;

import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class ProjectAtmosphereCommands {
    private ProjectAtmosphereCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        boolean simpleCloudsLoaded = AtmosphereCloudServices.isSimpleCloudsLoaded();
        dispatcher.register(
                Commands.literal("pa")
                        .requires(source -> source.hasPermission(2))
                        .then(PaHelpCommand.build())
                        .then(PaStatusCommand.build())
                        .then(PaDebugCommand.build())
                        .then(PaForecastCommand.build())
                        .then(PaTemperatureCommand.build())
                        .then(PaHumidityCommand.build())
                        .then(PaPressureCommand.build())
                        .then(PaWindCommand.build())
                        .then(PaFogCommand.build())
                        .then(PaCloudCommand.build(simpleCloudsLoaded))
                        .then(PaTornadoCommand.build(simpleCloudsLoaded))
                        .then(PaHurricaneCommand.build(simpleCloudsLoaded))
                        .then(PaSystemCommand.build())
                        .then(PaCommandAliases.build(simpleCloudsLoaded))
        );
    }
}
