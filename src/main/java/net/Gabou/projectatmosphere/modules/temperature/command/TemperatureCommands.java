package net.Gabou.projectatmosphere.modules.temperature.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.Gabou.projectatmosphere.modules.temperature.spike.SpikeManager;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.Collection;

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
                .then(Commands.literal("forecast")
                        .executes(ctx -> {
                            Player player = ctx.getSource().getPlayerOrException();
                            String forecast = TemperatureCommandHelper.getWeeklyForecast(TemperatureCommandHelper.getCurrentBiomeResourceLocation(player));
                            ctx.getSource().sendSuccess(() -> Component.literal(forecast), false);
                            return 1;
                        }))

                .then(Commands.literal("get")
                        .then(Commands.argument("biome", StringArgumentType.word())
                                .suggests(BIOME_SUGGESTIONS)
                                .executes(ctx -> {
                                    Player player = ctx.getSource().getPlayerOrException();
                                    String biomeStr = StringArgumentType.getString(ctx, "biome");
                                    BiomeInstanceKey biome = TemperatureCommandHelper.resolveBiome(player, biomeStr);
                                    long tick = TemperatureCommandHelper.getCurrentTick(ctx.getSource().getLevel());
                                    float temp = TemperatureCommandHelper.getTemperatureAt(biome, tick);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal(biome.biomeType() + " @ tick " + tick + ": " +
                                                    net.Gabou.projectatmosphere.util.UnitFormatter.formatTemperature(temp)), false);
                                    return 1;
                                })))

                .then(Commands.literal("dayprofile")
                        .executes(ctx -> {
                            Player player = ctx.getSource().getPlayerOrException();
                            BiomeInstanceKey biome = TemperatureCommandHelper.getCurrentBiome(player);
                            float[] profile = TemperatureCommandHelper.getDayProfile(biome);
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("Day profile: " +
                                            java.util.Arrays.toString(profile)), false);
                            return 1;
                        }))

                .then(Commands.literal("getseason")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            ServerLevel level = ctx.getSource().getLevel();
                            String subSeason = TemperatureCommandHelper.getCurrentSubSeason(level);
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("Current season: " + subSeason), false);
                            return 1;
                        }))

                .then(Commands.literal("gettemp")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            ServerLevel level = ctx.getSource().getLevel();
                            Player player = ctx.getSource().getPlayerOrException();
                            BlockPos pos = player.getOnPos();

                            var biomeHolder = level.getBiome(pos);
                            BiomeInstanceKey biomeId = new BiomeInstanceKey(biomeHolder.unwrapKey().get().location(), pos);

                            float serene = TemperatureCommandHelper.getFinalBiomeTemperature(level, biomeHolder, pos);
                            double celsius = TemperatureCommandHelper.convertToCelsius(serene);
                            float realTemp = TemperatureCommandHelper.getRealTemperature(level, biomeId, pos);

                            ctx.getSource().sendSuccess(() -> Component.literal("Current temperature (raw): " + serene), false);
                            ctx.getSource().sendSuccess(() -> Component.literal("Converted: " + net.Gabou.projectatmosphere.util.UnitFormatter.formatTemperature((float) celsius)), false);
                            ctx.getSource().sendSuccess(() -> Component.literal("Current (converted): " + net.Gabou.projectatmosphere.util.UnitFormatter.formatTemperature(realTemp)), false);
                            return 1;
                        }))

                .then(Commands.literal("regenerate")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            if (ctx.getSource().getPlayer() == null)
                                ctx.getSource().sendFailure(Component.literal("This command can only be run by a player."));
                            AtmosphereManager.onRegenerate(ctx.getSource().getLevel());

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

                .then(Commands.literal("help")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "[Temperature Commands Help]\n" +
                                            "/temperature forecast - Show 7-day forecast for your current biome.\n" +
                                            "/temperature get <biome|current> - Display the current temperature at a specific biome and tick.\n" +
                                            "/temperature dayprofile - View the 240-point daily temperature curve.\n" +
                                            "/temperature getseason - Show the current Serene Seasons sub-season.\n" +
                                            "/temperature gettemp - Raw and converted temperatures.\n" +
                                            "/temperature regenerate - Clear forecast cache and regenerate missing data.\n" +
                                            "/temperature resetSpikes - Clear spike simulation state cache."
                            ), false);

                            return 1;
                        }))
        );
    }
}

