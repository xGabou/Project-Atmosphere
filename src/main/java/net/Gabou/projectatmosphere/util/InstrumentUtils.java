package net.Gabou.projectatmosphere.util;

import net.Gabou.projectatmosphere.client.HUDOverlayRenderer;
import net.Gabou.projectatmosphere.clouds.WeatherCloudQueries;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.Gabou.projectatmosphere.network.InstrumentReadoutPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

public class InstrumentUtils {

    public static void displayWind(Level level, Player player) {
        if (level.isClientSide) {
            ClientDisplay.displayWind(level, player);
            return;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        BlockPos pos = player.blockPosition();
        if (!ensureForecastReady(serverLevel, serverPlayer, pos)) {
            return;
        }
        WindVector wind = ForecastOrchestrator.getWind(pos, serverLevel.getDayTime());
        String msg = "Wind: " + UnitFormatter.formatWindSpeed(wind.baseSpeed()) +
                " at " + String.format("%.0f\u00B0", Math.toDegrees(wind.angleRadians()));
        send(serverPlayer, msg);
    }

    public static void displayTemperature(Level level, Player player) {
        if (level.isClientSide) {
            ClientDisplay.displayTemperature(level, player);
            return;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        BlockPos pos = player.blockPosition();
        if (!ensureForecastReady(serverLevel, serverPlayer, pos)) {
            return;
        }
        float temp = ForecastOrchestrator.getCurrentTemperature(serverLevel, pos, serverLevel.getGameTime());
        String msg = "Current temperature: " + UnitFormatter.formatTemperature(temp);
        send(serverPlayer, msg);
    }

    public static void displayHumidity(Level level, Player player) {
        if (level.isClientSide) {
            ClientDisplay.displayHumidity(level, player);
            return;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        BlockPos pos = player.blockPosition();
        if (!ensureForecastReady(serverLevel, serverPlayer, pos)) {
            return;
        }
        float humidity = ForecastOrchestrator.getCurrentHumidity(serverLevel, pos, serverLevel.getGameTime());
        String msg = "Current humidity: " + UnitFormatter.formatHumidity(humidity);
        send(serverPlayer, msg);
    }

    public static void displayPressure(Level level, Player player) {
        if (level.isClientSide) {
            ClientDisplay.displayPressure(level, player);
            return;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        BlockPos pos = player.blockPosition();
        if (!ensureForecastReady(serverLevel, serverPlayer, pos)) {
            return;
        }
        double pressure = ForecastOrchestrator.getCurrentPressure(serverLevel, pos, serverLevel.getGameTime());
        String msg = "Current pressure: " + UnitFormatter.formatPressure(pressure);
        send(serverPlayer, msg);
    }

    public static void displayStorm(Level level, Player player) {
        if (level.isClientSide) {
            ClientDisplay.displayStorm(level, player);
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        String msg;
        BlockPos pos = player.blockPosition();
        if (WeatherCloudQueries.isThunderingAt(level, pos)) {
            msg = "Storm detected!";
        } else if (WeatherCloudQueries.isRainingAt(level, pos)) {
            msg = "Rain detected.";
        } else {
            msg = "Skies clear.";
        }
        send(serverPlayer, msg);
    }

    private static void send(ServerPlayer player, String message) {
        PacketDistributor.sendToPlayer(player, new InstrumentReadoutPacket(message));
    }

    private static boolean ensureForecastReady(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!AtmosphereManager.isInitialGenerationDone || ForecastOrchestrator.isRegenerating()) {
            send(player, "Forecast not ready.");
            return false;
        }
        if (!AtmosphereManager.isPlayerReady(player.getUUID())) {
            send(player, "Forecast not ready.");
            return false;
        }
        ForecastRegion region = ForecastOrchestrator.getRegionForecast(level, pos);
        if (region == null) {
            send(player, "Forecast not ready.");
            return false;
        }
        return true;
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientDisplay {
        private static void displayWind(Level level, Player player) {
            HUDOverlayRenderer.showTemperatureOverlay("Forecast not ready.");
        }

        private static void displayTemperature(Level level, Player player) {
            HUDOverlayRenderer.showTemperatureOverlay("Forecast not ready.");
        }

        private static void displayHumidity(Level level, Player player) {
            HUDOverlayRenderer.showTemperatureOverlay("Forecast not ready.");
        }

        private static void displayPressure(Level level, Player player) {
            HUDOverlayRenderer.showTemperatureOverlay("Forecast not ready.");
        }

        private static void displayStorm(Level level, Player player) {
            String msg;
            BlockPos pos = player.blockPosition();
            if (WeatherCloudQueries.isThunderingAt(level, pos)) {
                msg = "Storm detected!";
            } else if (WeatherCloudQueries.isRainingAt(level, pos)) {
                msg = "Rain detected.";
            } else {
                msg = "Skies clear.";
            }
            HUDOverlayRenderer.showTemperatureOverlay(msg);
        }
    }
}
