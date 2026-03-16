package net.Gabou.projectatmosphere.util;

import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.network.InstrumentReadoutPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class InstrumentUtils {
    private InstrumentUtils() { }

    public static void displayWind(Level level, Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        BlockPos pos = player.blockPosition();
        BiomeInstanceKey key = AtmosphereUtils.getBiomeKey(serverLevel, pos);
        WindVector wind = ForecastOrchestrator.getCurrentWind(key, serverLevel.getGameTime());
        String msg = "Wind: " + UnitFormatter.formatWindSpeed(wind.baseSpeed())
                + " at " + String.format("%.0f deg", Math.toDegrees(wind.angleRadians()));
        send(serverPlayer, msg);
    }

    public static void displayTemperature(Level level, Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        BlockPos pos = player.blockPosition();
        BiomeInstanceKey key = AtmosphereUtils.getBiomeKey(serverLevel, pos);
        float temp = ForecastOrchestrator.getCurrentTemperature(key, serverLevel.getGameTime());
        send(serverPlayer, "Current temperature: " + UnitFormatter.formatTemperature(temp));
    }

    public static void displayHumidity(Level level, Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        BlockPos pos = player.blockPosition();
        BiomeInstanceKey key = AtmosphereUtils.getBiomeKey(serverLevel, pos);
        float humidity = ForecastOrchestrator.getCurrentHumidity(key, serverLevel.getGameTime());
        String msg = humidity < 0.01f
                ? "Humidity: Loading..."
                : "Current humidity: " + UnitFormatter.formatHumidity(humidity);
        send(serverPlayer, msg);
    }

    public static void displayPressure(Level level, Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        BlockPos pos = player.blockPosition();
        BiomeInstanceKey key = AtmosphereUtils.getBiomeKey(serverLevel, pos);
        double pressure = ForecastOrchestrator.getCurrentPressure(key, serverLevel.getGameTime());
        send(serverPlayer, "Current pressure: " + UnitFormatter.formatPressure(pressure));
    }

    public static void displayStorm(Level level, Player player) {
        if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        String msg;
        if (level.isThundering()) {
            msg = "Storm detected!";
        } else if (level.isRaining()) {
            msg = "Rain detected.";
        } else {
            msg = "Skies clear.";
        }
        send(serverPlayer, msg);
    }

    private static void send(ServerPlayer player, String message) {
        player.connection.send(new InstrumentReadoutPacket(message));
    }
}

