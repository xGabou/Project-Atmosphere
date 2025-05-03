package net.Gabou.projectatmosphere.temperature.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.Gabou.projectatmosphere.temperature.TemperatureManager;
import net.Gabou.projectatmosphere.temperature.forcast.TemperatureForecast;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.Gabou.projectatmosphere.temperature.util.TemperatureProfileManager;

import java.awt.*;
import java.util.Objects;

public class TemperatureCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("temperature")
                .then(Commands.literal("forecast")
                        .executes(ctx -> {
                            Player p = ctx.getSource().getPlayerOrException();
                            ResourceLocation biome = p.level().getBiome(p.blockPosition()).unwrapKey().get().location();
                            float[][] week = TemperatureManager.getWeeklyForecast(biome);
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("Forecast for " + biome + ": " + java.util.Arrays.deepToString(week)), false);
                            return 1;
                        }))
                .then(Commands.literal("get")
                        .then(Commands.argument("biome", StringArgumentType.word())
                                .executes(ctx -> {
                                    ResourceLocation biome = new ResourceLocation(StringArgumentType.getString(ctx, "biome"));
                                    Player p = ctx.getSource().getPlayerOrException();
                                    int tick = (int)(p.level().getDayTime() % 24000);
                                    float temp = TemperatureProfileManager.getCurrentTemperature(biome, tick);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal(String.format("%s @ tick %d = %.2f°C", biome, tick, temp)), false);
                                    return 1;
                                })))
                .then(Commands.literal("dayprofile")
                        .executes(ctx -> {
                            // similar: dumps today’s 240-entry profile
                            return 1;
                        }))

        );
        dispatcher.register(Commands.literal("realtemp")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerLevel level = context.getSource().getLevel();
                    BlockPos onPos = Objects.requireNonNull(context.getSource().getPlayer()).getOnPos();
                    float temp = TemperatureManager.getCurrentTemperature(level, onPos);
                    context.getSource().sendSuccess(
                            () -> Component.literal("Current temperature in celcius: " + temp),
                            false
                    );
                    return 1;
                }));
    }
}
