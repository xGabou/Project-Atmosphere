package net.Gabou.projectatmosphere.modules.wind;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.Gabou.projectatmosphere.modules.temperature.command.TemperatureCommandHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.Arrays;

public class WindCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(build());
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("windSpeed")
                .then(Commands.literal("get") 
                        .executes(ctx -> {
                            Player player = ctx.getSource().getPlayerOrException();
                            ServerLevel level = (ServerLevel) player.level();
                            if (!TemperatureCommandHelper.isInOverworld(level)) {
                                ctx.getSource().sendFailure(Component.literal("Wind forecast is only available in the Overworld."));
                                return 0;
                            }
                            BlockPos pos = player.blockPosition();
                            ForecastRegion region = ForecastOrchestrator.getRegionForecast(level, pos);
                            if (region == null || region.getWind() == null) {
                                ctx.getSource().sendFailure(Component.literal("No region wind forecast available."));
                                return 0;
                            }

                            var forecastArr = region.getWind();
                            String forecast = Arrays.toString(forecastArr);
                            ctx.getSource().sendSuccess(() -> Component.literal(forecast), false);

                            return 1; 
                        })
                );
    }
}
