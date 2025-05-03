package net.Gabou.projectatmosphere.command;

import com.mojang.brigadier.CommandDispatcher;
import net.Gabou.projectatmosphere.temperature.compat.SereneTempToCelcius;
import net.Gabou.projectatmosphere.temperature.forecast.TemperatureForecast;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.Gabou.projectatmosphere.manager.CloudSpawner;
import sereneseasons.init.ModConfig;
import sereneseasons.season.SeasonHandler;
import sereneseasons.season.SeasonHooks;
import sereneseasons.season.SeasonTime;

import java.util.Locale;
import java.util.Objects;

public class SpawnCloudCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spawncloud")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerLevel level = context.getSource().getLevel();
                    CloudSpawner.spawnCloudForPlayer(context.getSource().getPlayer(),level);
                    return 1;
                }));

    }
}
