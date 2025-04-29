package net.Gabou.projectatmosphere.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.Gabou.projectatmosphere.manager.CloudSpawner;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.init.ModConfig;
import sereneseasons.season.SeasonHandler;
import sereneseasons.season.SeasonSavedData;
import sereneseasons.season.SeasonTime;

import java.util.Locale;

public class SpawnCloudCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spawncloud")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerLevel level = context.getSource().getLevel();
                    CloudSpawner.spawnCloudForPlayer(context.getSource().getPlayer(),level);
                    return 1;
                }));
        dispatcher.register(Commands.literal("getseason")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerLevel level = context.getSource().getLevel();
                    var seasonData = SeasonHandler.getSeasonSavedData(level);
                    SeasonTime time = new SeasonTime(seasonData.seasonCycleTicks);
                    int subSeasonDuration = ModConfig.seasons.subSeasonDuration;
                    Object[] var10001 = new Object[5];
                    String var10004 = time.getSubSeason().toString();
                    var10001[0] = Component.translatable("desc.sereneseasons." + var10004.toLowerCase(Locale.ROOT));
                    var10001[1] = time.getDay() % subSeasonDuration + 1;
                    var10001[2] = subSeasonDuration;
                    var10001[3] = time.getSeasonCycleTicks() % time.getDayDuration();
                    var10001[4] = time.getDayDuration();
                    context.getSource().sendSuccess(
                            () -> Component.literal("Current season: " + var10004.toString()),
                            false
                    );
                    return 1;
                }));
    }
}
