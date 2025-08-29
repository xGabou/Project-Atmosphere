package net.Gabou.projectatmosphere.modules.hurricane;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.init.ModTags;

public class HurricaneCommand {

    public static void register(RegisterCommandsEvent event) {
        var base = Commands.literal("spawnHurricane")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("category", IntegerArgumentType.integer(1, 5))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            ServerLevel level = player.serverLevel();

                            if (!level.dimension().equals(Level.OVERWORLD)) return 0;

                            var pos = player.blockPosition();
                            var biome = level.getBiome(pos);

                            // Only allow hurricanes in oceans or tropical biomes
                            if (!biome.is(BiomeTags.IS_OCEAN) && !biome.is(ModTags.Biomes.TROPICAL_BIOMES)) {
                                ctx.getSource().sendFailure(Component.literal("Hurricanes can only spawn in warm oceans."));
                                return 0;
                            }

                            // Seasonal restriction
                            Season.SubSeason sub = SeasonHelper.getSeasonState(level).getSubSeason();
                            if (sub.ordinal() < Season.SubSeason.LATE_SPRING.ordinal()
                                    || sub.ordinal() > Season.SubSeason.EARLY_AUTUMN.ordinal()) {
                                ctx.getSource().sendFailure(Component.literal("Hurricanes only spawn between late spring and early fall."));
                                return 0;
                            }

                            int catInt = IntegerArgumentType.getInteger(ctx, "category");
                            HurricaneCategory cat = HurricaneCategory.fromId(catInt);

                            BiomeInstanceKey key = new BiomeInstanceKey(AtmosphereUtils.getBiomeLocation(pos, level), pos);
                            var wind = ForecastOrchestrator.getCurrentWind(key, level.getGameTime());

                            // Cloud + hurricane spawn
                            SimpleCloudsCompat.spawnCloudInBiome("custom_cumulonimbus", key, level, null, wind);
                            Vec3 spawnPos = new Vec3(player.getX(), level.getSeaLevel(), player.getZ());
                            HurricaneManager.spawnServer(level, spawnPos, 40f, wind, cat);

                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("🌀 Hurricane category " + catInt + " spawned."), true);
                            return 1;
                        }));

        event.getDispatcher().register(base);

        // Clear all hurricanes
        event.getDispatcher().register(Commands.literal("clearhurricanes")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    HurricaneManager.clearHurricanes();
                    ctx.getSource().sendSuccess(() -> Component.literal("🌀 All hurricanes cleared."), true);
                    return 1;
                }));

        // Remove one hurricane near the player
        event.getDispatcher().register(Commands.literal("removehurricane")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    Vec3 playerPos = player.position();

                    HurricaneInstance hurricane = HurricaneManager.getActiveHurricanes().stream()
                            .filter(h -> h.position.distanceToSqr(playerPos) < 400)
                            .findFirst().orElse(null);

                    if (hurricane != null) {
                        HurricaneManager.removeHurricane(hurricane);
                        ctx.getSource().sendSuccess(() -> Component.literal("🌀 Hurricane removed."), true);
                    } else {
                        ctx.getSource().sendFailure(Component.literal("No hurricane found near you."));
                    }
                    return 1;
                }));
    }
}
