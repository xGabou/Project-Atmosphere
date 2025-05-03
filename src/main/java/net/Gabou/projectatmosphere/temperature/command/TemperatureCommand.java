package net.Gabou.projectatmosphere.temperature.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.Gabou.projectatmosphere.temperature.TemperatureManager;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public class TemperatureCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("temperature")
                .then(Commands.literal("forecast")
                        .executes(ctx -> {
                            Player p = ctx.getSource().getPlayerOrException();
                            ResourceLocation biome = p.level()
                                    .getBiome(p.blockPosition())
                                    .unwrapKey().get().location();
                            float[][] week = TemperatureManager.getWeeklyForecast(biome);
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("Forecast for " + biome + ": " +
                                            java.util.Arrays.deepToString(week)), false);
                            return 1;
                        }))
                .then(Commands.literal("get")
                        .then(Commands.argument("biome", StringArgumentType.word())
                                .executes(ctx -> {
                                    String biomeStr = StringArgumentType.getString(ctx, "biome");
                                    ResourceLocation biome = new ResourceLocation(biomeStr);
                                    long tick = ctx.getSource().getLevel().getDayTime() % 24000L;
                                    float temp = TemperatureManager.getCurrentTemperature(biome, tick);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal(biome + " @ tick " + tick + ": " +
                                                    String.format("%.2f°C", temp)), false);
                                    return 1;
                                })))
                .then(Commands.literal("dayprofile")
                        .executes(ctx -> {
                            Player p = ctx.getSource().getPlayerOrException();
                            ResourceLocation biome = p.level()
                                    .getBiome(p.blockPosition())
                                    .unwrapKey().get().location();
                            float[] profile = net.Gabou.projectatmosphere.temperature.util
                                    .TemperatureProfileManager
                                    .getDayProfile(biome);
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("Day profile: " +
                                            java.util.Arrays.toString(profile)), false);
                            return 1;
                        }))
        );
    }
}
