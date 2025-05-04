package net.Gabou.projectatmosphere.temperature.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.Gabou.projectatmosphere.temperature.manager.TemperatureManager;
import net.Gabou.projectatmosphere.temperature.forecast.TemperatureForecast;
import net.Gabou.projectatmosphere.temperature.spike.SpikeManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class TemperatureCommands {

    public static final SuggestionProvider<CommandSourceStack> BIOME_SUGGESTIONS = (ctx, builder) -> {
        Collection<ResourceLocation> biomeIds = ctx.getSource()
                .getServer()
                .registryAccess()
                .registryOrThrow(Registries.BIOME)
                .keySet();

        biomeIds.forEach(id -> builder.suggest(id.toString()));
        builder.suggest("current");
        builder.suggest("currentbiome");
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("temperature")
                        // /temperature forecast
                        .then(Commands.literal("forecast")
                                .executes(ctx -> {
                                    Player player = ctx.getSource().getPlayerOrException();
                                    ResourceLocation biome = TemperatureCommandHelper.getCurrentBiome(player);
                                    String forecast = TemperatureCommandHelper.getWeeklyForecast(biome);
                                    ctx.getSource().sendSuccess(() -> Component.literal(forecast), false);
                                    return 1;
                                }))
                        // /temperature get <biome>
                        .then(Commands.literal("get")
                                .then(Commands.argument("biome", StringArgumentType.word())
                                        .suggests(BIOME_SUGGESTIONS)
                                        .executes(ctx -> {
                                            Player player = ctx.getSource().getPlayerOrException();
                                            String biomeStr = StringArgumentType.getString(ctx, "biome");
                                            ResourceLocation biome = TemperatureCommandHelper.resolveBiome(player, biomeStr);
                                            long tick = TemperatureCommandHelper.getCurrentTick(ctx.getSource().getLevel());
                                            float temp = TemperatureCommandHelper.getTemperatureAt(biome, tick);
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal(biome + " @ tick " + tick + ": " +
                                                            String.format("%.2f°C", temp)), false);
                                            return 1;
                                        })))
                        // /temperature dayprofile
                        .then(Commands.literal("dayprofile")
                                .executes(ctx -> {
                                    Player player = ctx.getSource().getPlayerOrException();
                                    ResourceLocation biome = TemperatureCommandHelper.getCurrentBiome(player);
                                    float[] profile = TemperatureCommandHelper.getDayProfile(biome);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Day profile: " +
                                                    java.util.Arrays.toString(profile)), false);
                                    return 1;
                                }))
                        // /temperature getseason
                        .then(Commands.literal("getseason")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> {
                                    ServerLevel level = ctx.getSource().getLevel();
                                    String subSeason = TemperatureCommandHelper.getCurrentSubSeason(level);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Current season: " + subSeason), false);
                                    return 1;
                                }))
                        // /temperature gettemp
                        .then(Commands.literal("gettemp")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> {
                                    ServerLevel level = ctx.getSource().getLevel();
                                    Player player = ctx.getSource().getPlayerOrException();
                                    BlockPos pos = player.getOnPos();

                                    var biomeHolder = level.getBiome(pos);
                                    ResourceLocation biomeId = biomeHolder.unwrapKey().get().location();

                                    float serene = TemperatureCommandHelper.getFinalBiomeTemperature(level, biomeHolder, pos);
                                    double celsius = TemperatureCommandHelper.convertToCelsius(serene);
                                    float realTemp = TemperatureCommandHelper.getRealTemperature(level, biomeId, pos);

                                    ctx.getSource().sendSuccess(() -> Component.literal("Current temperature: " + serene), false);
                                    ctx.getSource().sendSuccess(() -> Component.literal("In Celsius: " + celsius), false);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Current temperature in Celsius: " + realTemp), false);
                                    return 1;
                                }))
                        // /temperature gt
                        .then(Commands.literal("gt")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> {
                                    ServerLevel level = ctx.getSource().getLevel();
                                    Player player = ctx.getSource().getPlayerOrException();
                                    BlockPos pos = player.getOnPos();

                                    String forecasted = TemperatureCommandHelper.getForecastedTemperature(level, pos);
                                    ctx.getSource().sendSuccess(() -> Component.literal(forecasted), false);
                                    return 1;
                                }))
                        .then(Commands.literal("testforecast")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            Player player = ctx.getSource().getPlayerOrException();
                                            ServerLevel level = ctx.getSource().getLevel();
                                            int radius = IntegerArgumentType.getInteger(ctx, "radius");

                                            if (radius > 300) {
                                                ctx.getSource().sendFailure(Component.literal("§cRadius too large. Please use §e/temperature testforecastjson " + radius + " §cfor large forecasts."));
                                                return 0;
                                            }

                                            CompletableFuture.runAsync(() -> {
                                                var forecast = TemperatureForecast.generateTemporaryForecastAround(level, player.blockPosition(), radius);

                                                String formatted = TemperatureCommandHelper.formatForecastMap(forecast);

                                                level.getServer().execute(() ->
                                                        ctx.getSource().sendSuccess(() -> Component.literal(formatted), false)
                                                );
                                            });

                                            return 1;
                                        }))
                                .executes(ctx -> {
                                    Player player = ctx.getSource().getPlayerOrException();
                                    ServerLevel level = ctx.getSource().getLevel();

                                    CompletableFuture.runAsync(() -> {
                                        var forecast = TemperatureForecast.generateTemporaryForecastAround(level, player.blockPosition(), 100);

                                        String formatted = TemperatureCommandHelper.formatForecastMap(forecast);

                                        level.getServer().execute(() ->
                                                ctx.getSource().sendSuccess(() -> Component.literal(formatted), false)
                                        );
                                    });

                                    return 1;
                                }))


                        .then(Commands.literal("testforecastjson")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            Player player = ctx.getSource().getPlayerOrException();
                                            ServerLevel level = ctx.getSource().getLevel();
                                            int radius = IntegerArgumentType.getInteger(ctx, "radius");

                                            if (radius > 10000) {
                                                ctx.getSource().sendSystemMessage(Component.literal("§eRadius exceeding 10000, this may take a while."));
                                            }

                                            CompletableFuture.runAsync(() -> {
                                                Map<ResourceLocation, float[][]> forecast = TemperatureForecast.generateTemporaryForecastAround(level, player.blockPosition(), radius);
                                                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                                                String json = gson.toJson(forecast);

                                                level.getServer().execute(() ->
                                                        ctx.getSource().sendSuccess(() -> Component.literal("§aForecast (JSON):\n" + json), false)
                                                );
                                            });

                                            return 1;
                                        }))
                                .executes(ctx -> {
                                    Player player = ctx.getSource().getPlayerOrException();
                                    ServerLevel level = ctx.getSource().getLevel();

                                    CompletableFuture.runAsync(() -> {
                                        Map<ResourceLocation, float[][]> forecast = TemperatureForecast.generateTemporaryForecastAround(level, player.blockPosition(), 100);
                                        Gson gson = new GsonBuilder().setPrettyPrinting().create();
                                        String json = gson.toJson(forecast);

                                        level.getServer().execute(() ->
                                                ctx.getSource().sendSuccess(() -> Component.literal("§aForecast (JSON):\n" + json), false)
                                        );
                                    });

                                    return 1;
                                }))


// /temperature regenerate
                        .then(Commands.literal("regenerate")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> {
                                    if (ctx.getSource().getPlayer() == null)
                                        ctx.getSource().sendFailure(Component.literal("§cThis command can only be run by a player."));
                                    TemperatureManager.clearForecastCache(ctx.getSource().getLevel());
                                    ctx.getSource().sendSuccess(() -> Component.literal("§aTemperature forecast cache has been cleared."), false);
                                    return 1;
                                }))
                        .then(Commands.literal("resetSpikes")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> {
                                    SpikeManager.clearSpikeCache(ctx.getSource().getLevel());
                                    ctx.getSource().sendSuccess(() -> Component.literal("§Spike's cache has been cleared."), false);
                                    return 1;
                                }))
                        // /temperature help
                        .then(Commands.literal("help")
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(() -> Component.literal("""
                                                                         §6[Temperature Commands Help]
                                            
                                            §e/temperature forecast §7– Show 7-day forecast for your current biome.
                                            §e/temperature get <biome|current> §7– Display the current temperature at a specific biome and tick.
                                            §e/temperature dayprofile §7– View the 240-point daily temperature curve.
                                            §e/temperature getseason §7– Show the current Serene Seasons sub-season.
                                            §e/temperature gettemp §7– Raw, Celsius, and real computed temperature.
                                            §e/temperature gt §7– Real-time computed temperature from the forecast system.
                                            
                                            §e/temperature testforecast [radius] §7– Show forecast in chat (1–300). For debug/testing.
                                            §e/temperature testforecastjson [radius] §7– Output JSON forecast, supports large radius (>300). Async-safe.
                                            
                                            §e/temperature regenerate §7– Clear forecast cache and regenerate missing data.
                                            §e/temperature resetSpikes §7– Clear spike simulation state cache.
                                            """), false);

                                    return 1;
                                }))
        );
    }
}
