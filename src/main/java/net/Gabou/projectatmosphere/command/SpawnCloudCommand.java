package net.Gabou.projectatmosphere.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.Gabou.projectatmosphere.clouds.backend.CloudRegionManager;
import net.Gabou.projectatmosphere.clouds.backend.CloudRegionSyncManager;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SpawnCloudCommand {
    /**
     * Enregistre la commande <code>/pa spawncloud</code> qui crée une région de nuage PA.
     *
     * @param dispatcher dispatcher de commandes
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(build());
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("spawncloud")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerLevel level = context.getSource().getLevel();
                    if (!level.dimension().equals(Level.OVERWORLD)) return 0;
                    BlockPos pos = BlockPos.containing(context.getSource().getPosition());
                    CloudRegionManager.getInstance().createCloudRegion(
                            level,
                            new Vec3(pos.getX(), pos.getY() + 80.0D, pos.getZ()),
                            64.0F,
                            pos.getY() + 72.0F,
                            pos.getY() + 88.0F,
                            0.65F,
                            0.75F,
                            0.35F,
                            RegionInstanceKey.from(pos)
                    );
                    if (context.getSource().getPlayer() != null) {
                        CloudRegionSyncManager.syncPlayer(context.getSource().getPlayer());
                    }
                    return 1;
                });
    }
}
