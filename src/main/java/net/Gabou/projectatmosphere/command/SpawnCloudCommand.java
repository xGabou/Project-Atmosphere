package net.Gabou.projectatmosphere.command;

import com.mojang.brigadier.CommandDispatcher;
import net.Gabou.projectatmosphere.manager.SimpleCloudSpawner;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Objects;

public class SpawnCloudCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spawncloud")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerLevel level = context.getSource().getLevel();
                    if (!level.dimension().equals(Level.OVERWORLD)) return 0;
                    SimpleCloudSpawner.spawnCloudForPlayer(Objects.requireNonNull(context.getSource().getPlayer()),level);
                    return 1;
                }));

    }
}
