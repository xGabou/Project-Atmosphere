package net.Gabou.projectatmosphere.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudGenerator;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.manager.SimpleCloudSpawner;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.modules.core.ForecastType;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.snowstorm.SnowstormManager;
import net.Gabou.projectatmosphere.modules.temperature.command.TemperatureCommandHelper;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.UnitFormatter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;

public class DebugAtmoCommand {

    public static final Logger LOGGER = LogManager.getLogger("DebugAtmoCommand");

    private static int sendForecast(CommandContext<CommandSourceStack> ctx, BiomeForecast forecast, ResourceLocation biome) {
        if (forecast == null) {
            ctx.getSource().sendFailure(Component.literal("No forecast found for biome: " + biome));
            return 0;
        }

        var state = AtmosphericStateRegistry.getState(forecast.getBiomeKey());
        float temperature = state != null ? state.getTemperature() : 0f;
        float humidity = state != null ? state.getHumidityPercent() : 0f;
        float pressure = state != null ? state.getPressure() : 0f;
        WindVector w = state != null ? state.getWind() : null;
        if (w == null) {
            WindVector[] week = forecast.getWind();
            if (week != null && week.length > 0) {
                w = week[0];
            }
        }
        String wind = w == null ? "-" : UnitFormatter.formatWindSpeed(w.baseSpeed()) + " at " + String.format("%.0f°", Math.toDegrees(w.angleRadians()));

        ctx.getSource().sendSuccess(() -> Component.literal(
                "Biome: " + biome +
                        "\n  Temp:     " + UnitFormatter.formatTemperature(temperature) +
                        "\n  Pressure: " + UnitFormatter.formatPressure(pressure) +
                        "\n  Humidity: " + UnitFormatter.formatHumidity(humidity) +
                        "\n  Wind:     [" + wind + "]"
        ), false);
        return 1;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("weatherdebug")
                        .then(Commands.literal("forecast")
                                .executes(ctx -> {
                                    ServerLevel world = ctx.getSource().getLevel();
                                    if (!TemperatureCommandHelper.isInOverworld(world)) {
                                        ctx.getSource().sendFailure(Component.literal("Weather forecast is only available in the Overworld."));
                                        return 0;
                                    }
                                    BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
                                    ResourceLocation biome = world.registryAccess()
                                            .registryOrThrow(Registries.BIOME)
                                            .getKey(world.getBiome(pos).value());
                                    BiomeForecast forecast = ForecastGenerator.getClosestValidForecast(new BiomeInstanceKey(biome, pos), ForecastType.WIND);
                                    return sendForecast(ctx, forecast, biome);
                                })
                                .then(Commands.argument("biome", ResourceLocationArgument.id())
                                        .executes(ctx -> {
                                            if (!TemperatureCommandHelper.isInOverworld(ctx.getSource().getLevel())) {
                                                ctx.getSource().sendFailure(Component.literal("Biome forecast is only available in the Overworld."));
                                                return 0;
                                            }
                                            ResourceLocation biome = ResourceLocationArgument.getId(ctx, "biome");
                                            BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
                                            BiomeForecast forecast = ForecastGenerator.getClosestValidForecast(new BiomeInstanceKey(biome, pos), ForecastType.WIND);
                                            return sendForecast(ctx, forecast, biome);
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
                                            "CPU Info\n" +
                                                    "- Logical cores: " + cores + "\n" +
                                                    "- Force shared (config): " + forceShared + "\n" +
                                                    "- Current async mode: " + mode
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
                                    if (violence == 0) {
                                        ctx.getSource().sendFailure(Component.literal("No violence detected"));
                                        return 0;
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Violence is: " + violence +
                                                    "\nCloudViolence: [" + violence + "]"
                                    ), false);
                                    return 1;
                                })
                        )
        );
    }

    private static CloudRegion spawnCloud(ServerLevel level, BlockPos pos, String cloudId) {
        if (SimpleCloudsCompat.generator == null) {
            LOGGER.warn("Simple Clouds generator is null, cannot spawn cloud.");
            return null;
        }
        if (cloudId == null) {
            LOGGER.warn("Cloud ID is null, cannot spawn cloud.");
            return null;
        }
        if(!level.dimension().equals(Level.OVERWORLD)) {
            return null;
        }

        CloudGenerator generator = SimpleCloudsCompat.generator;
        CloudRegion existing = generator.getCloudAtWorldPosition(pos.getX(), pos.getZ());
        if (existing != null) {
            generator.removeClouds(r -> r == existing);
        }

        BiomeInstanceKey key = new BiomeInstanceKey(AtmosphereUtils.getBiomeLocation(pos, level), pos);
        WindVector wind = ForecastOrchestrator.getCurrentWind(key, level.getGameTime());
        return SimpleCloudsCompat.spawnCloudInBiome(cloudId, key, level, null, wind);
    }

    private static int spawnRain(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            ctx.getSource().sendFailure(Component.literal("Rain clouds can only be spawned in the Overworld."));
            return 0;
        }
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
        ctx.getSource().sendSuccess(() -> Component.literal("Spawned rain cloud: " + cloudId), true);
        return 1;
    }

    private static int spawnThunder(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            ctx.getSource().sendFailure(Component.literal("Thunder clouds can only be spawned in the Overworld."));
            return 0;
        }
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
        ctx.getSource().sendSuccess(() -> Component.literal("Spawned thunder cloud: " + cloudId), true);
        return 1;
    }

    private static int spawnSnowstorm(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            ctx.getSource().sendFailure(Component.literal("Snowstorm clouds can only be spawned in the Overworld."));
            return 0;
        }
        BlockPos pos;
        int intensity;
        try {
            pos = BlockPosArgument.getBlockPos(ctx, "pos");
        } catch (IllegalArgumentException e) {
            pos = BlockPos.containing(ctx.getSource().getPosition());
        }
        try {
            intensity = IntegerArgumentType.getInteger(ctx, "intensity");
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("No intensity given."));
            return 0;
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
        CloudRegion region = spawnCloud(level, pos, cloudId);
        SnowstormManager.startSnowstorm(intensity, region);
        ctx.getSource().sendSuccess(() -> Component.literal("Spawned snowstorm cloud."), true);
        return 1;
    }

}

