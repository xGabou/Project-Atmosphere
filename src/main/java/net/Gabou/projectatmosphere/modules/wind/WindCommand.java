package net.Gabou.projectatmosphere.modules.wind;

import com.mojang.brigadier.CommandDispatcher;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.temperature.command.TemperatureCommandHelper;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.Arrays;

public class WindCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        
        dispatcher.register(Commands.literal("windSpeed")
                .then(Commands.literal("get") 
                        .executes(ctx -> {
                            Player player = ctx.getSource().getPlayerOrException();
                            BiomeInstanceKey biome = TemperatureCommandHelper.getCurrentBiome(player);

                            
                            WindVector[] forecastArr = ForecastGenerator.getForecast(biome).getWind();
                            String forecast = Arrays.toString(forecastArr);

                            
                            ctx.getSource().sendSuccess(() -> Component.literal(forecast), false);

                            return 1; 
                        })
                )
        );
    }
}
