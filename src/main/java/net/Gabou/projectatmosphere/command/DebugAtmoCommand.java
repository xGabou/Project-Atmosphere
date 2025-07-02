package net.Gabou.projectatmosphere.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.manager.SimpleCloudSpawner;
import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.modules.core.ForecastType;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

public class DebugAtmoCommand {
    private static int sendForecast(CommandContext<CommandSourceStack> ctx, BiomeForecast forecast, ResourceLocation biome) {
        if (forecast == null) {
            ctx.getSource().sendFailure(Component.literal("No forecast found for biome: " + biome));
            return 0;
        }

        ctx.getSource().sendSuccess(() ->
                Component.literal("Biome: " + biome +
                        "\n  🌡 Temp:     [" + format(forecast.getTemperatureDay()) + "]" +
                        "\n  🧪 Pressure: [" + format(forecast.getPressureDay()) + "]" +
                        "\n  💧 Humidity: [" + format(forecast.getHumidityDay()) + "]" +
                        "\n  🌬 Wind:     [" + forecast.getWindDay() + "]"
                ), false);
        return 1;
    }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("weatherdebug")
                        .then(Commands.literal("forecast")
                                .executes(ctx -> {
                                    // Use the executor's current biome
                                    ServerLevel world = ctx.getSource().getLevel();
                                    BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
                                    ResourceLocation biome = world.registryAccess()
                                            .registryOrThrow(Registries.BIOME)
                                            .getKey(world.getBiome(pos).value());
                                   BiomeForecast forecast = ForecastGenerator.getClosestValidForecast(new BiomeInstanceKey(biome, pos), ForecastType.WIND);

                                    return sendForecast(ctx,forecast, biome);
                                })
                                .then(Commands.argument("biome", ResourceLocationArgument.id())
                                        .executes(ctx -> {
                                            ResourceLocation biome = ResourceLocationArgument.getId(ctx, "biome");
                                            BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
                                            BiomeForecast forecast = ForecastGenerator.getClosestValidForecast(new BiomeInstanceKey(biome, pos), ForecastType.WIND);

                                            return sendForecast(ctx,forecast, biome);
                                        })
                                )
                        )

                        .then(Commands.literal("cpu")
                                .executes(ctx -> {
                                    int cores = Runtime.getRuntime().availableProcessors();
                                    boolean forceShared = /*AtmoCommonConfig.FORCE_SHARED_EXECUTOR.get()*/false;
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
                        .then(Commands.argument("violence", ResourceLocationArgument.id())
                                .executes(ctx -> {
                                        int violence = SimpleCloudSpawner.getCurrentViolence();
                                        if(violence==0) {
                                        ctx.getSource().sendFailure(Component.literal("No violence detected  "));
                                        return 0;
                                    }

                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal("Violence is: " + violence +
                                                    "\n CloudViolence:     [" +violence + "]"
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

