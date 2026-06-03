package net.Gabou.projectatmosphere.modules.temperature.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
        dispatcher.register(build());
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("temperature")
                        .then(Commands.literal("forecast")
                                .executes(ctx -> {
                                    Player player = ctx.getSource().getPlayerOrException();
                                    if(!TemperatureCommandHelper.isInOverworld(player.level()))
                                    {
                                        ctx.getSource().sendFailure(Component.literal("Temperature forecast is only available in the Overworld."));
                                        return 0;
                                    }
                                    String forecast = TemperatureCommandHelper.getWeeklyForecast(TemperatureCommandHelper.getCurrentBiomeResourceLocation(player));
                                    ctx.getSource().sendSuccess(() -> Component.literal(forecast), false);
                                    return 1;
                                }))

                        .then(Commands.literal("get")
                                .then(Commands.argument("biome", StringArgumentType.word())
                                        .suggests(BIOME_SUGGESTIONS)
                                        .executes(ctx -> {
                                            Player player = ctx.getSource().getPlayerOrException();
                                            if(!TemperatureCommandHelper.isInOverworld(player.level()))
                                            {
                                                ctx.getSource().sendFailure(Component.literal("Temperature forecast is only available in the Overworld."));
                                                return 0;
                                            }
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
                                    if(!TemperatureCommandHelper.isInOverworld(player.level()))
                                    {
                                        ctx.getSource().sendFailure(Component.literal("Temperature forecast is only available in the Overworld."));
                                        return 0;
                                    }
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
                                    if(!TemperatureCommandHelper.isInOverworld(player.level()))
                                    {
                                        ctx.getSource().sendFailure(Component.literal("Temperature forecast is only available in the Overworld."));
                                        return 0;
                                    }
                                    BlockPos pos = player.getOnPos();

                                    var biomeHolder = level.getBiome(pos);
                                    BiomeInstanceKey biomeId = new BiomeInstanceKey(biomeHolder.unwrapKey().get().location(), pos);

                                    float biomeBase = biomeHolder.value().getBaseTemperature();
                                    float realTemp = TemperatureCommandHelper.getRealTemperature(level, biomeId, pos);

                                    ctx.getSource().sendSuccess(() -> Component.literal("Biome base temperature: " + biomeBase), false);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Season provider: " + TemperatureCommandHelper.getCurrentSubSeason(level)), false);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Current forecast temperature: " + net.Gabou.projectatmosphere.util.UnitFormatter.formatTemperature(realTemp)), false);
                                    return 1;
                                }))

                        .then(Commands.literal("regenerate")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> {
                                    if (ctx.getSource().getPlayer() == null)
                                        ctx.getSource().sendFailure(Component.literal("This command can only be run by a player."));
                                    ServerLevel level = ctx.getSource().getLevel();
                                    if(!TemperatureCommandHelper.isInOverworld(level))
                                    {
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

                        .then(Commands.literal("help")
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "[Temperature Commands Help]\n" +
                                                    "/pa temperature forecast - Show 7-day forecast for your current biome.\n" +
                                                    "/pa temperature get <biome|current> - Display the current temperature at a specific biome and tick.\n" +
                                                    "/pa temperature dayprofile - View the 240-point daily temperature curve.\n" +
                                                    "/pa temperature getseason - Show the current season provider state.\n" +
                                                    "/pa temperature gettemp - Show biome base and PA forecast temperatures.\n" +
                                                    "/pa temperature regenerate - Clear forecast cache and regenerate missing data.\n" +
                                                    "/pa temperature resetSpikes - Clear spike simulation state cache."
                                    ), false);

                                    return 1;
                                }))
        ;
    }
}
