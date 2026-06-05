package net.Gabou.projectatmosphere.modules.hurricane;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.seasons.SeasonStage;
import net.Gabou.projectatmosphere.seasons.SeasonTimeHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class HurricaneCommand {
    public static void appendTo(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> base = Commands.literal("spawnHurricane")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("category", IntegerArgumentType.integer(1, 5))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            ServerLevel level = player.serverLevel();
                            if (!level.dimension().equals(Level.OVERWORLD)) {
                                return 0;
                            }
                            var pos = player.blockPosition();
                            var biome = level.getBiome(pos);
                            if (!biome.is(BiomeTags.IS_OCEAN)) {
                                ctx.getSource().sendFailure(Component.literal("Hurricanes can only spawn in warm oceans."));
                                return 0;
                            }
                            SeasonStage stage = SeasonTimeHelper.stage(level);
                            if (stage == SeasonStage.WINTER) {
                                ctx.getSource().sendFailure(Component.literal("Hurricanes only spawn between spring and autumn."));
                                return 0;
                            }
                            int catInt = IntegerArgumentType.getInteger(ctx, "category");
                            HurricaneCategory cat = HurricaneCategory.fromId(catInt);
                            var wind = ForecastOrchestrator.getWind(pos, level.getGameTime());
                    Vec3 spawnPos = new Vec3(player.getX(), level.getSeaLevel(), player.getZ());
                    HurricaneManager.spawnServer(level, spawnPos, 40.0F, wind, cat);
                    ctx.getSource().sendSuccess(() -> Component.literal("Hurricane category " + catInt + " spawned and is forming."), true);
                    return 1;
                }));

        root.then(base);
        root.then(Commands.literal("clearhurricanes")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    HurricaneManager.clearHurricanes();
                    ctx.getSource().sendSuccess(() -> Component.literal("All hurricanes cleared."), true);
                    return 1;
                }));
        root.then(Commands.literal("removehurricane")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    Vec3 playerPos = player.position();
                    HurricaneInstance hurricane = HurricaneManager.getActiveHurricanes().stream()
                            .filter(h -> h.position.distanceToSqr(playerPos) < 400)
                            .findFirst().orElse(null);
                    if (hurricane != null) {
                        HurricaneManager.removeHurricane(hurricane);
                        ctx.getSource().sendSuccess(() -> Component.literal("Hurricane is dissipating."), true);
                    } else {
                        ctx.getSource().sendFailure(Component.literal("No hurricane found near you."));
                    }
                    return 1;
                }));
    }
}
