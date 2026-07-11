package net.Gabou.projectatmosphere.command.tree.service;

import net.Gabou.projectatmosphere.api.WindVectorApi;
import net.Gabou.projectatmosphere.clouds.cell.CloudCell;
import net.Gabou.projectatmosphere.clouds.cell.sim.CloudCellSimulationManager;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandMessages;
import net.Gabou.projectatmosphere.command.tree.util.PaCommandSupport;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.weather.StormSeverityScale;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Native cloud-cell tornado commands; safe when Simple Clouds is absent. */
public final class NativeTornadoCommandService {
    private static final float DEFAULT_RADIUS = 14.0F;

    private NativeTornadoCommandService() {
    }

    public static int spawn(CommandSourceStack source) {
        if (!AtmoCommonConfig.ENABLE_TORNADOES.get()) {
            source.sendFailure(Component.literal("Tornadoes are disabled in Project Atmosphere config."));
            return 0;
        }
        ServerPlayer player = PaCommandSupport.requirePlayer(source, "Native tornado spawning is only available to players.");
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.serverLevel();
        RegionInstanceKey key = RegionInstanceKey.from(player.blockPosition());
        WindVectorApi.WindSample sample = WindVectorApi.getOrFallback(key, level.getGameTime());
        WindVector wind = WindVector.fromBase(sample.speedMps(), (float) Math.toRadians(sample.directionDeg()));
        int stormLevel = StormSeverityScale.resolve(level, key, level.getGameTime());
        Vec3 position = player.position();
        boolean spawned = CloudCellSimulationManager.getInstance().spawnNativeTornado(
                level, position, DEFAULT_RADIUS, wind, stormLevel
        );
        if (!spawned) {
            source.sendFailure(Component.literal("Unable to create a native tornado cell: the cell budget is full."));
            return 0;
        }
        PaCommandMessages.success(source, true,
                "Native tornado spawned",
                "Position: " + format(position),
                "Wind: " + PaCommandSupport.formatWind(wind),
                "Renderer: PA cloud-cell funnel");
        return 1;
    }

    public static int remove(CommandSourceStack source, double radius) {
        ServerPlayer player = PaCommandSupport.requirePlayer(source, "Native tornado removal is only available to players.");
        if (player == null) {
            return 0;
        }
        boolean removed = CloudCellSimulationManager.getInstance().dissipateNearestNativeTornado(
                player.serverLevel(), player.position(), radius
        );
        if (!removed) {
            source.sendFailure(Component.literal("No native tornado found within " + Mth.floor(radius) + " blocks."));
            return 0;
        }
        PaCommandMessages.success(source, true, "Native tornado dissipated");
        return 1;
    }

    public static int clear(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        int cleared = CloudCellSimulationManager.getInstance().clearNativeTornadoes(level);
        PaCommandMessages.success(source, true, "Native tornadoes cleared", "Count: " + cleared);
        return 1;
    }

    public static int list(CommandSourceStack source) {
        List<CloudCell> cells = CloudCellSimulationManager.getInstance().nativeTornadoCells(source.getLevel());
        if (cells.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[Project Atmosphere] No native tornadoes are active."), false);
            return 1;
        }
        StringBuilder message = new StringBuilder("[Project Atmosphere] Native tornadoes: ").append(cells.size());
        for (CloudCell cell : cells) {
            message.append("\n").append(cell.id())
                    .append(" pos=").append(String.format(java.util.Locale.ROOT, "%.1f %.1f", cell.x(), cell.z()))
                    .append(" strength=").append(String.format(java.util.Locale.ROOT, "%.2f", cell.funnelStrength()));
        }
        source.sendSuccess(() -> Component.literal(message.toString()), false);
        return 1;
    }

    private static String format(Vec3 position) {
        return String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", position.x, position.y, position.z);
    }
}
