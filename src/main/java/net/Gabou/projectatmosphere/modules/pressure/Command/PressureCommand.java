package net.Gabou.projectatmosphere.modules.pressure.Command;

import com.mojang.brigadier.CommandDispatcher;
import net.Gabou.projectatmosphere.modules.humidity.util.HumidityProfileManager;
import net.Gabou.projectatmosphere.modules.pressure.util.PressureProfileManager;
import net.Gabou.projectatmosphere.modules.temperature.command.TemperatureCommandHelper;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.Arrays;

public class PressureCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Register the "humidity" subcommand
        dispatcher.register(Commands.literal("pressure")
                .then(Commands.literal("get") // "get" subcommand
                        .executes(ctx -> {
                            Player player = ctx.getSource().getPlayerOrException();
                            BiomeInstanceKey biome = TemperatureCommandHelper.getCurrentBiome(player);

                            // Get the weekly forecast based on the biome
                            float[][] forecastArr = PressureProfileManager.getWeeklyForecast(biome);
                            String forecast = Arrays.deepToString(forecastArr);

                            // Send the forecast to the player
                            ctx.getSource().sendSuccess(() -> Component.literal(forecast), false);

                            return 1; // Return success code
                        })
                )
        );
    }
}
