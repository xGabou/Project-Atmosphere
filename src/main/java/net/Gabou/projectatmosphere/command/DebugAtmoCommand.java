package net.Gabou.projectatmosphere.command;

import com.mojang.brigadier.CommandDispatcher;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class DebugAtmoCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("weatherdebug")
                        .then(Commands.literal("forecast")
                                .then(Commands.argument("biome", ResourceLocationArgument.id())
                                        .executes(ctx -> {
                                            ResourceLocation biome = ResourceLocationArgument.getId(ctx, "biome");
                                            var forecast = AtmosphereManager.getForecast(biome);
                                            if (forecast == null) {
                                                ctx.getSource().sendFailure(Component.literal("No forecast found for biome: " + biome));
                                                return 0;
                                            }

                                            ctx.getSource().sendSuccess(() ->
                                                    Component.literal("Biome: " + biome +
                                                            "\n  🌡 Temp:     [" + format(forecast.temperature()) + "]" +
                                                            "\n  🧪 Pressure: [" + format(forecast.pressure()) + "]" +
                                                            "\n  💧 Humidity: [" + format(forecast.humidity()) + "]" +
                                                            "\n  🌬 Wind:     [" + format(forecast.wind()) + "]"
                                                    ), false);
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("cpu")
                                .executes(ctx -> {
                                    int cores = Runtime.getRuntime().availableProcessors();
                                    boolean forceShared = AtmoCommonConfig.FORCE_SHARED_EXECUTOR.get();
                                    String mode;
                                    if (forceShared || cores <= 6) {
                                        mode = "Shared Executor (1 thread pool)";
                                    } else if (cores <= 10) {
                                        mode = "Two Executor Groups (shared in pairs)";
                                    } else {
                                        mode = "Four Separate Executors";
                                    }

                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "🧠 CPU Info\n" +
                                                    "• Logical cores: " + cores + "\n" +
                                                    "• Force shared (config): " + forceShared + "\n" +
                                                    "• Current async mode: " + mode
                                    ), false);
                                    return 1;
                                })
                        )
        );
    }

    private static String format(float[] arr) {
        return String.format("%.1f, %.1f", arr[0], arr[1]);
    }
}
