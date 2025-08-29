package net.Gabou.projectatmosphere.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudGenerator;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.manager.SimpleCloudSpawner;
import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.modules.core.ForecastType;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.snowstorm.SnowstormManager;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;

public class DebugAtmoCommand {
    /**
     * Sends a formatted forecast message to the command source for the given biome.
     *
     * @param ctx       the command context
     * @param forecast  the forecast to display, may be {@code null}
     * @param biome     the biome to describe
     * @return {@code 1} if a forecast was sent, {@code 0} otherwise
     */
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
    /**
     * Registers the <code>/weatherdebug</code> command and its subcommands used for debugging
     * atmospheric data.
     *
     * @param dispatcher the command dispatcher used to register the command
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("weatherdebug")
                        .then(Commands.literal("forecast")
                                .executes(ctx -> {
                                    
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
                                    boolean forceShared = false;
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
                        .then(Commands.literal("rain")
                                .executes(DebugAtmoCommand::spawnRain)
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(DebugAtmoCommand::spawnRain)
                                        .then(Commands.argument("noThunder", BoolArgumentType.bool())
                                                .executes(DebugAtmoCommand::spawnRain)
                                                .then(Commands.argument("intensity", IntegerArgumentType.integer(1, 2))
                                                        .executes(DebugAtmoCommand::spawnRain)
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("thunder")
                                .executes(DebugAtmoCommand::spawnThunder)
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(DebugAtmoCommand::spawnThunder)
                                        .then(Commands.argument("intensity", IntegerArgumentType.integer(1, 2))
                                                .executes(DebugAtmoCommand::spawnThunder)
                                        )
                                )
                        )
                        .then(Commands.literal("snowstorm")
                                .executes(DebugAtmoCommand::spawnSnowstorm)
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(DebugAtmoCommand::spawnSnowstorm)
                                        .then(Commands.argument("overwrite", BoolArgumentType.bool())
                                                .executes(DebugAtmoCommand::spawnSnowstorm)
                                        )
                                )
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

    private static void spawnCloud(ServerLevel level, BlockPos pos, String cloudId) {
        CloudGenerator generator = SimpleCloudsCompat.generator;
        if (generator != null) {
            CloudRegion existing = generator.getCloudAtWorldPosition(pos.getX(), pos.getZ());
            if (existing != null) {
                generator.removeClouds(r -> r == existing);
            }
        }
        BiomeInstanceKey key = new BiomeInstanceKey(AtmosphereUtils.getBiomeLocation(pos, level), pos);
        WindVector wind = ForecastOrchestrator.getCurrentWind(key, level.getGameTime());
        SimpleCloudsCompat.spawnCloudInBiome(cloudId, key, level, null, wind);
    }

    private static int spawnRain(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos;
        try {
            pos = BlockPosArgument.getBlockPos(ctx, "pos");
        } catch (IllegalArgumentException e) {
            pos = BlockPos.containing(ctx.getSource().getPosition());
        }
        boolean noThunder;
        try {
            noThunder = BoolArgumentType.getBool(ctx, "noThunder");
        } catch (IllegalArgumentException e) {
            noThunder = false;
        }
        int intensity;
        try {
            intensity = IntegerArgumentType.getInteger(ctx, "intensity");
        } catch (IllegalArgumentException e) {
            intensity = 1;
        }
        String cloudId = CloudLibrary.getRandomRainCloud(intensity, !noThunder);
        spawnCloud(level, pos, cloudId);
        ctx.getSource().sendSuccess(() -> Component.literal("☔ Spawned rain cloud: " + cloudId), true);
        return 1;
    }

    private static int spawnThunder(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos;
        try {
            pos = BlockPosArgument.getBlockPos(ctx, "pos");
        } catch (IllegalArgumentException e) {
            pos = BlockPos.containing(ctx.getSource().getPosition());
        }
        int intensity;
        try {
            intensity = IntegerArgumentType.getInteger(ctx, "intensity");
        } catch (IllegalArgumentException e) {
            intensity = 1;
        }
        String cloudId = CloudLibrary.getRandomThunderCloud(intensity);
        spawnCloud(level, pos, cloudId);
        ctx.getSource().sendSuccess(() -> Component.literal("⚡ Spawned thunder cloud: " + cloudId), true);
        return 1;
    }

    private static int spawnSnowstorm(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos;
        try {
            pos = BlockPosArgument.getBlockPos(ctx, "pos");
        } catch (IllegalArgumentException e) {
            pos = BlockPos.containing(ctx.getSource().getPosition());
        }
        boolean overwrite;
        try {
            overwrite = BoolArgumentType.getBool(ctx, "overwrite");
        } catch (IllegalArgumentException e) {
            overwrite = false;
        }
        if (SeasonHelper.getSeasonState(level).getSeason() != Season.WINTER) {
            if (!overwrite) {
                ctx.getSource().sendFailure(Component.literal("It is not winter."));
                return 0;
            }
            try {
                Object state = SeasonHelper.getSeasonState(level);
                state.getClass().getMethod("setSeason", Season.class).invoke(state, Season.WINTER);
            } catch (Exception e) {
                ctx.getSource().sendFailure(Component.literal("Failed to overwrite season."));
                return 0;
            }
        }
        String cloudId = CloudLibrary.getSnowstormCloudId();
        SnowstormManager.startSnowstorm(CloudLibrary.getSeverityFromCloudId(cloudId));
        spawnCloud(level, pos, cloudId);
        ctx.getSource().sendSuccess(() -> Component.literal("❄ Spawned snowstorm cloud."), true);
        return 1;
    }

    /**
     * Formats a two-element float array to a comma-separated string with one decimal place.
     *
     * @param arr the array to format
     * @return the formatted string
     */
    private static String format(float[] arr) {
        return String.format("%.1f, %.1f", arr[0], arr[1]);
    }


}

