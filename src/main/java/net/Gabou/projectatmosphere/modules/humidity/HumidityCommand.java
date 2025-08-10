package net.Gabou.projectatmosphere.modules.humidity;

import com.mojang.brigadier.CommandDispatcher;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.modules.temperature.command.TemperatureCommandHelper;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.Arrays;

public class HumidityCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        
        dispatcher.register(Commands.literal("humidity")
                .then(Commands.literal("get") 
                        .executes(ctx -> {
                            Player player = ctx.getSource().getPlayerOrException();
                            BiomeInstanceKey biome = TemperatureCommandHelper.getCurrentBiome(player);

                            
                            float[][] forecastArr = ForecastGenerator.getForecastMap().get(biome).getHumidity();
                            String forecast = Arrays.deepToString(forecastArr);

                            
                            ctx.getSource().sendSuccess(() -> Component.literal(forecast), false);

                            return 1; 
                        })
                )
        );
    }
}
