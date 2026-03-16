package net.Gabou.projectatmosphere.modules.pressure;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.Arrays;

public class PressureCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(build());
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("pressure")
                .then(Commands.literal("get") 
                        .executes(ctx -> {
                            Player player = ctx.getSource().getPlayerOrException();
                            ServerLevel level = (ServerLevel) player.level();
                            BlockPos pos = player.blockPosition();
                            ForecastRegion region = ForecastOrchestrator.getRegionForecast(level, pos);
                            if (region == null || region.getPressure() == null) {
                                ctx.getSource().sendFailure(Component.literal("No region pressure forecast available."));
                                return 0;
                            }

                            float[][] forecastArr = region.getPressure();
                            String forecast = Arrays.deepToString(forecastArr);

                            ctx.getSource().sendSuccess(() -> Component.literal(forecast), false);

                            return 1;
                        })
                );
    }
}
