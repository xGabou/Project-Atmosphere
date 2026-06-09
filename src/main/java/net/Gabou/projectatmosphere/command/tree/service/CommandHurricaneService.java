package net.Gabou.projectatmosphere.command.tree.service;

import net.Gabou.projectatmosphere.command.tree.util.PaCommandMessages;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandSupport;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneCategory;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneInstance;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneManager;
import net.Gabou.projectatmosphere.modules.temperature.command.TemperatureCommandHelper;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.seasons.SeasonStage;
import net.Gabou.projectatmosphere.seasons.SeasonTimeHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

public final class CommandHurricaneService {
    private static final double DEFAULT_REMOVE_RADIUS = 256.0D;

    private CommandHurricaneService() {
    }

    public static int spawnHurricane(CommandSourceStack source, int categoryId) {
        ServerPlayer player = PaCommandSupport.requirePlayer(source, "Hurricane spawning is only available to players.");
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.serverLevel();
        if (!TemperatureCommandHelper.isInOverworld(level)) {
            source.sendFailure(Component.literal("Hurricanes can only spawn in the Overworld."));
            return 0;
        }

        BlockPos pos = player.blockPosition();
        var biome = level.getBiome(pos);
        if (!biome.is(BiomeTags.IS_OCEAN)) {
            source.sendFailure(Component.literal("Hurricanes can only spawn in warm oceans."));
            return 0;
        }
        if (SeasonTimeHelper.stage(level) == SeasonStage.WINTER) {
            source.sendFailure(Component.literal("Hurricanes only spawn between spring and autumn."));
            return 0;
        }

        HurricaneCategory category = HurricaneCategory.fromId(categoryId);
        WindVector wind = ForecastOrchestrator.getWind(pos, level.getGameTime());
        Vec3 spawnPos = new Vec3(player.getX(), level.getSeaLevel(), player.getZ());
        HurricaneManager.spawnServer(level, spawnPos, 40.0F, wind, category);
        PaCommandMessages.success(
                source,
                true,
                "Hurricane spawned",
                "Category: " + categoryId,
                "Position: " + formatPos(spawnPos),
                "Wind: " + PaCommandSupport.formatWind(wind),
                "Result: forming"
        );
        return 1;
    }

    public static int removeHurricane(CommandSourceStack source, double maxDistance) {
        ServerPlayer player = PaCommandSupport.requirePlayer(source, "Hurricane removal is only available to players.");
        if (player == null) {
            return 0;
        }
        Vec3 playerPos = player.position();
        double maxDistanceSq = maxDistance * maxDistance;
        HurricaneInstance hurricane = HurricaneManager.getActiveHurricanes().stream()
                .filter(h -> h.position.distanceToSqr(playerPos) <= maxDistanceSq)
                .min(Comparator.comparingDouble(h -> h.position.distanceToSqr(playerPos)))
                .orElse(null);
        if (hurricane == null) {
            source.sendFailure(Component.literal("No hurricane found within " + Math.round(maxDistance) + " blocks."));
            return 0;
        }

        HurricaneManager.removeHurricane(hurricane);
        PaCommandMessages.success(source, true, "Hurricane removal requested");
        return 1;
    }

    public static int clearHurricanes(CommandSourceStack source) {
        HurricaneManager.clearHurricanes();
        PaCommandMessages.success(source, true, "All hurricanes cleared");
        return 1;
    }

    public static int sendHurricaneList(CommandSourceStack source) {
        List<HurricaneInstance> hurricanes = HurricaneManager.getActiveHurricanes();
        if (hurricanes.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[Project Atmosphere]\nAction: Hurricane list\nResult: no active hurricanes"), false);
            return 1;
        }

        StringBuilder message = new StringBuilder("[Project Atmosphere]\nAction: Hurricane list");
        message.append("\nActive hurricanes: ").append(hurricanes.size());
        for (int i = 0; i < hurricanes.size(); i++) {
            HurricaneInstance hurricane = hurricanes.get(i);
            message.append("\n").append(i + 1).append(". ")
                    .append(hurricane.getId())
                    .append(" pos=").append(formatPos(hurricane.position))
                    .append(" category=").append(hurricane.category)
                    .append(" phase=").append(hurricane.getPhase())
                    .append(" age=").append(hurricane.getAgeTicks());
        }
        source.sendSuccess(() -> Component.literal(message.toString()), false);
        return 1;
    }

    public static int sendHurricaneInfo(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        HurricaneInstance hurricane = player == null
                ? HurricaneManager.getActiveHurricanes().stream().findFirst().orElse(null)
                : HurricaneManager.getActiveHurricanes().stream()
                .min(Comparator.comparingDouble(h -> h.position.distanceToSqr(player.position())))
                .orElse(null);
        if (hurricane == null) {
            source.sendFailure(Component.literal("No active hurricane found."));
            return 0;
        }

        PaCommandMessages.success(
                source,
                false,
                "Hurricane info",
                "Id: " + hurricane.getId(),
                "Category: " + hurricane.category,
                "Phase: " + hurricane.getPhase(),
                "Position: " + formatPos(hurricane.position),
                "Core radius: " + String.format(java.util.Locale.ROOT, "%.1f", hurricane.getCoreRadius()),
                "Storm extent: " + String.format(java.util.Locale.ROOT, "%.1f", hurricane.getStormExtentRadius()),
                "Wind: " + PaCommandSupport.formatWind(hurricane.wind)
        );
        return 1;
    }

    private static String formatPos(Vec3 pos) {
        return String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", pos.x, pos.y, pos.z);
    }
}
