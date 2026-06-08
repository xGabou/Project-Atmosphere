package net.Gabou.projectatmosphere.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
import net.Gabou.projectatmosphere.modules.fog.FogCommand;
import net.Gabou.projectatmosphere.modules.humidity.HumidityCommand;
import net.Gabou.projectatmosphere.modules.pressure.PressureCommand;
import net.Gabou.projectatmosphere.modules.temperature.command.TemperatureCommands;
import net.Gabou.projectatmosphere.modules.tornado.TornadoCommand;
import net.Gabou.projectatmosphere.modules.wind.WindCommand;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class ProjectAtmosphereCommands {
    private ProjectAtmosphereCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("pa");
        boolean simpleCloudsLoaded = AtmosphereCloudServices.isSimpleCloudsLoaded();

        root.then(TemperatureCommands.build());
        root.then(HumidityCommand.build());
        root.then(PressureCommand.build());
        root.then(WindCommand.build());
        root.then(FogCommand.build());

        LiteralArgumentBuilder<CommandSourceStack> weatherDebug = Commands.literal("weatherdebug");
        DebugAtmoCommand.appendTo(weatherDebug, !simpleCloudsLoaded);
        TornadoDebug.appendTo(weatherDebug);
        root.then(weatherDebug);

        if (simpleCloudsLoaded) {
            TornadoCommand.appendTo(root);
            HurricaneCommand.appendTo(root);
        }

        dispatcher.register(root);
    }
}
