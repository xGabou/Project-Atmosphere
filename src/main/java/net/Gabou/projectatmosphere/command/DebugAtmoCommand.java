package net.Gabou.projectatmosphere.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.api.AtmoApi;
import net.Gabou.projectatmosphere.clouds.backend.CloudRegionManager;
import net.Gabou.projectatmosphere.clouds.backend.CloudRegionSyncManager;
import net.Gabou.projectatmosphere.clouds.backend.CloudRegionState;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.CloudLibrary;
import net.Gabou.projectatmosphere.client.fog.FogBiomeClassifier;
import net.Gabou.projectatmosphere.modules.fog.FogHeuristics;
import net.Gabou.projectatmosphere.modules.temperature.command.TemperatureCommandHelper;
import net.Gabou.projectatmosphere.modules.temperature.spike.SpikeManager;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.Gabou.projectatmosphere.seasons.SeasonStage;
import net.Gabou.projectatmosphere.seasons.SeasonTimeHelper;

import java.util.List;

public class DebugAtmoCommand {

    public static final Logger LOGGER = LogManager.getLogger("DebugAtmoCommand");

    private static int sendForecast(CommandContext<CommandSourceStack> ctx, BlockPos pos, ResourceLocation biome) {

        ServerLevel level = ctx.getSource().getLevel();
        long tick = level.getGameTime();
        RegionInstanceKey regionKey = RegionInstanceKey.from(pos);
        float temperature = ForecastOrchestrator.getCurrentTemperature(level, pos, tick);
        float humidity = ForecastOrchestrator.getCurrentHumidity(level, pos, tick);
        float pressure = ForecastOrchestrator.getCurrentPressure(level, pos, tick);
        var windVector = ForecastOrchestrator.getWind(regionKey, tick);
        String wind = windVector == null ? "-" : UnitFormatter.formatWindSpeed(windVector.baseSpeed()) + " at " + String.format("%.0f°", Math.toDegrees(windVector.angleRadians()));

        ctx.getSource().sendSuccess(() -> Component.literal(
                "Biome: " + biome +
                        "\n  Region:   " + regionKey +
                        "\n  Temp:     " + UnitFormatter.formatTemperature(temperature) +
                        "\n  Pressure: " + UnitFormatter.formatPressure(pressure) +
                        "\n  Humidity: " + UnitFormatter.formatHumidity(humidity) +
                        "\n  Wind:     [" + wind + "]"
        ), false);
        return 1;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("weatherdebug");
        appendTo(root);
        dispatcher.register(root);
    }

    public static void appendTo(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("forecast")
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
                    return sendForecast(ctx, pos, biome);
                })
                // Maintenance and diagnostics
                .then(Commands.literal("regenerate")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            if (ctx.getSource().getPlayer() == null)
                                ctx.getSource().sendFailure(Component.literal("This command can only be run by a player."));
                            ServerLevel level = ctx.getSource().getLevel();
                            if (!TemperatureCommandHelper.isInOverworld(level)) {
                                ctx.getSource().sendFailure(Component.literal("Temperature forecast is only available in the Overworld."));
                                return 0;
                            }
                            AtmosphereManager.onRegenerate(level);


                            ctx.getSource().sendSuccess(() -> Component.literal("Temperature forecast cache has been cleared."), false);
                            return 1;
                        }))
                .then(Commands.literal("resetSpikes")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            SpikeManager.clearSpikeCache(ctx.getSource().getLevel());
                            ctx.getSource().sendSuccess(() -> Component.literal("Spike's cache has been cleared."), false);
                            return 1;
                        }))
                .then(Commands.argument("biome", ResourceLocationArgument.id())
                        .executes(ctx -> {
                            if (!TemperatureCommandHelper.isInOverworld(ctx.getSource().getLevel())) {
                                ctx.getSource().sendFailure(Component.literal("Biome forecast is only available in the Overworld."));
                                return 0;
                            }
                            ResourceLocation biome = ResourceLocationArgument.getId(ctx, "biome");
                            BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
                            return sendForecast(ctx, pos, biome);
                        })
                )
        );
        root.then(Commands.literal("fog")
                .executes(DebugAtmoCommand::sendFogDebug)
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(DebugAtmoCommand::sendFogDebug)
                )
        );

        root.then(Commands.literal("cpu")
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
        );

        root.then(Commands.literal("rain")
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
        );

        root.then(Commands.literal("thunder")
                .executes(DebugAtmoCommand::spawnThunder)
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(DebugAtmoCommand::spawnThunder)
                        .then(Commands.argument("intensity", IntegerArgumentType.integer(1, 2))
                                .executes(DebugAtmoCommand::spawnThunder)
                        )
                )
        );

        root.then(Commands.literal("snowstorm")
                .executes(DebugAtmoCommand::spawnSnowstorm)
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(DebugAtmoCommand::spawnSnowstorm)
                        .then(Commands.argument("overwrite", BoolArgumentType.bool())
                                .executes(DebugAtmoCommand::spawnSnowstorm)
                        )
                )
        );

        root.then(Commands.argument("violence", ResourceLocationArgument.id())
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Violence debug Simple Clouds indisponible dans le chemin PA natif."
                    ), false);
                    return 1;
                })
        );


        root.then(Commands.literal("debugmode")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean value = BoolArgumentType.getBool(ctx, "value");
                            ProjectAtmosphere.DEBUG_MODE = value;

                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("Project Atmosphere debug mode set to: " + value),
                                    true
                            );

                            return 1;
                        })
                )
        );

        root.then(Commands.literal("clouds")
                .then(Commands.literal("create")
                        .requires(source -> source.hasPermission(2))
                        .executes(DebugAtmoCommand::createDebugCloudRegion)
                )
                .then(Commands.literal("count")
                        .executes(DebugAtmoCommand::sendCloudRegionCount)
                )
                .then(Commands.literal("list")
                        .executes(DebugAtmoCommand::listCloudRegions)
                )
                .then(Commands.literal("clear")
                        .requires(source -> source.hasPermission(2))
                        .executes(DebugAtmoCommand::clearCloudRegions)
                )
                .then(Commands.literal("clearInactive")
                        .requires(source -> source.hasPermission(2))
                        .executes(DebugAtmoCommand::clearInactiveCloudRegions)
                )
                .then(Commands.literal("sync")
                        .executes(DebugAtmoCommand::syncCloudRegions)
                )
        );

        root.then(Commands.literal("createCloudDebug")
                .requires(source -> source.hasPermission(2))
                .executes(DebugAtmoCommand::createDebugCloudRegion)
        );

        root.then(Commands.literal("Clouds count")
                .executes(DebugAtmoCommand::sendCloudRegionCount)
        );
    }

    private static int createDebugCloudRegion(CommandContext<CommandSourceStack> ctx) {
        if (FMLEnvironment.production) {
            ctx.getSource().sendFailure(Component.literal("This command is only available in a development environment."));
            return 0;
        }

        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        RegionInstanceKey sourceRegionKey = RegionInstanceKey.from(pos);

        CloudRegionState state = CloudRegionManager.getInstance().createCloudRegion(
                level,
                new Vec3(pos.getX(), pos.getY() + 80.0D, pos.getZ()),
                64.0F,
                pos.getY() + 72.0F,
                pos.getY() + 88.0F,
                0.65F,
                0.75F,
                0.35F,
                sourceRegionKey
        );

        if (source.getPlayer() != null) {
            CloudRegionSyncManager.syncPlayer(source.getPlayer());
        }

        int count = CloudRegionManager.getInstance().getCloudRegionCount(level);
        source.sendSuccess(
                () -> Component.literal("Cloud backend créé: " + state.getRegionId() + "\nCloud regions sauvegardées: " + count + "\nSync client immédiate envoyée."),
                false
        );

        return 1;
    }

    private static int sendCloudRegionCount(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        int storedCount = CloudRegionManager.getInstance().getCloudRegionCount(level);
        int activeRenderDataCount = CloudRegionManager.getInstance().getActiveRenderData(level).size();

        source.sendSuccess(
                () -> Component.literal("Cloud backend: stored=" + storedCount + " activeRenderData=" + activeRenderDataCount),
                false
        );

        return 1;
    }

    private static int listCloudRegions(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        List<String> lines = CloudRegionManager.getInstance().describeCloudRegions(level);

        if (lines.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Aucune cloud region backend sauvegardée."), false);
            return 1;
        }

        StringBuilder message = new StringBuilder("Cloud regions backend sauvegardées: ").append(lines.size());
        int limit = Math.min(lines.size(), 8);
        for (int i = 0; i < limit; i++) {
            message.append("\n").append(i + 1).append(". ").append(lines.get(i));
        }
        if (lines.size() > limit) {
            message.append("\n... ").append(lines.size() - limit).append(" autres régions");
        }

        source.sendSuccess(() -> Component.literal(message.toString()), false);
        return 1;
    }

    private static int clearCloudRegions(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        CloudRegionManager.getInstance().clearCloudRegions(level);

        if (source.getPlayer() != null) {
            CloudRegionSyncManager.syncPlayer(source.getPlayer());
        }

        source.sendSuccess(() -> Component.literal("Cloud regions backend effacées et sync client envoyée."), false);
        return 1;
    }

    private static int clearInactiveCloudRegions(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        int removed = CloudRegionManager.getInstance().clearInactiveCloudRegions(level);

        if (source.getPlayer() != null) {
            CloudRegionSyncManager.syncPlayer(source.getPlayer());
        }

        source.sendSuccess(
                () -> Component.literal("Cloud regions inactives supprimées: " + removed + "\nSync client envoyée."),
                false
        );
        return 1;
    }

    private static int syncCloudRegions(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (source.getPlayer() == null) {
            source.sendFailure(Component.literal("Cette commande doit être exécutée par un joueur."));
            return 0;
        }

        CloudRegionSyncManager.syncPlayer(source.getPlayer());
        source.sendSuccess(() -> Component.literal("Sync cloud regions envoyée au client."), false);
        return 1;
    }

    private static CloudRegionState spawnCloud(ServerLevel level, BlockPos pos, String cloudId) {
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return null;
        }
        float density = cloudId != null && cloudId.contains("thunder") ? 0.85F : 0.65F;
        float coverage = cloudId != null && cloudId.contains("snow") ? 0.85F : 0.75F;
        return CloudRegionManager.getInstance().createCloudRegion(
                level,
                new Vec3(pos.getX(), pos.getY() + 80.0D, pos.getZ()),
                64.0F,
                pos.getY() + 72.0F,
                pos.getY() + 88.0F,
                density,
                coverage,
                0.35F,
                RegionInstanceKey.from(pos)
        );
    }

    private static int sendFogDebug(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            ctx.getSource().sendFailure(Component.literal("Fog debug is only available in the Overworld."));
            return 0;
        }

        BlockPos pos;
        try {
            pos = BlockPosArgument.getBlockPos(ctx, "pos");
        } catch (IllegalArgumentException e) {
            pos = BlockPos.containing(ctx.getSource().getPosition());
        }
        final BlockPos samplePos = pos;

        long tick = level.getGameTime();
        float humidity = ForecastOrchestrator.getCurrentHumidity(level, samplePos, tick);
        float rainIntensity = AtmoApi.getInstance().getWeatherSnapshot(level, samplePos, tick).rainIntensity();
        float wetBiomeFactor = FogBiomeClassifier.computeWetBiomeFactor(level, samplePos);
        FogHeuristics.FogProfile fog = FogHeuristics.sample(humidity, wetBiomeFactor, rainIntensity);
        ResourceLocation biome = level.registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getKey(level.getBiome(samplePos).value());

        ctx.getSource().sendSuccess(() -> Component.literal(
                "Fog Debug: " + samplePos.getX() + ", " + samplePos.getY() + ", " + samplePos.getZ() +
                        "\n  Enabled:          " + AtmoCommonConfig.FOG_ENABLED.get() +
                        "\n  Biome:            " + biome +
                        "\n  Humidity:         " + UnitFormatter.formatHumidity(humidity) +
                        "\n  Rain intensity:   " + String.format("%.2f", rainIntensity) +
                        "\n  Wet biome factor: " + String.format("%.2f", wetBiomeFactor) +
                        "\n  Humidity factor:  " + String.format("%.2f", fog.humidityFactor()) +
                        "\n  Fog strength:     " + String.format("%.2f", fog.strength()) +
                        "\n  Thresholds:       start=" + String.format("%.0f", AtmoCommonConfig.FOG_HUMIDITY_START_PERCENT.get())
                        + "% full=" + String.format("%.0f", AtmoCommonConfig.FOG_HUMIDITY_FULL_PERCENT.get()) + "%" +
                        "\n  Tunables:         wetBiome=" + String.format("%.2f", AtmoCommonConfig.FOG_WET_BIOME_BASE_STRENGTH.get())
                        + " rainBoost=" + String.format("%.2f", AtmoCommonConfig.FOG_RAIN_BOOST.get())
                        + " near=" + String.format("%.1f", AtmoCommonConfig.FOG_NEAR_DISTANCE.get())
                        + " far=" + String.format("%.1f", AtmoCommonConfig.FOG_FAR_DISTANCE.get())
                        + " colorBlend=" + String.format("%.2f", AtmoCommonConfig.FOG_COLOR_BLEND.get())
        ), false);
        return 1;
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
        CloudRegionState state = spawnCloud(level, pos, cloudId);
        if (state != null && ctx.getSource().getPlayer() != null) {
            CloudRegionSyncManager.syncPlayer(ctx.getSource().getPlayer());
        }
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
        CloudRegionState state = spawnCloud(level, pos, cloudId);
        if (state != null && ctx.getSource().getPlayer() != null) {
            CloudRegionSyncManager.syncPlayer(ctx.getSource().getPlayer());
        }
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
        if (SeasonTimeHelper.stage(level) != SeasonStage.WINTER) {
            if (!overwrite) {
                ctx.getSource().sendFailure(Component.literal("It is not winter."));
                return 0;
            }
            ctx.getSource().sendSuccess(() -> Component.literal("Proceeding despite non-winter season (no season override available)."), true);
        }
        String cloudId = CloudLibrary.getSnowstormCloudId();
        CloudRegionState region = spawnCloud(level, pos, cloudId);
        if (region == null) {
            ctx.getSource().sendFailure(Component.literal("Could not create PA snowstorm cloud region."));
            return 0;
        }
        if (ctx.getSource().getPlayer() != null) {
            CloudRegionSyncManager.syncPlayer(ctx.getSource().getPlayer());
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Created PA snowstorm cloud region: " + region.getRegionId()), true);
        return 1;
    }

}

