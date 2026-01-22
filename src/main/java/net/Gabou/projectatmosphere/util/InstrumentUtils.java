package net.Gabou.projectatmosphere.util;

import net.Gabou.projectatmosphere.client.BiomeClientTemperatureCache;
import net.Gabou.projectatmosphere.client.HUDOverlayRenderer;
import net.Gabou.projectatmosphere.compat.ColdSweatCompat;
import net.Gabou.projectatmosphere.compat.CompatHandler;
import net.Gabou.projectatmosphere.compat.LegendarySurvivalCompat;
import net.Gabou.projectatmosphere.compat.ToughAsNailsCompat;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.network.InstrumentReadoutPacket;
import net.Gabou.projectatmosphere.network.NetworkHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.PacketDistributor;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;

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
        WindVector wind = ForecastOrchestrator.getWind(serverLevel, pos, serverLevel.getDayTime());
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
        BiomeInstanceKey key = new BiomeInstanceKey(AtmosphereUtils.getBiomeLocation(pos, serverLevel), pos);
        float temp = ForecastOrchestrator.getCurrentTemperature(key, serverLevel.getGameTime());
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
        BiomeInstanceKey key = new BiomeInstanceKey(AtmosphereUtils.getBiomeLocation(pos, serverLevel), pos);
        float humidity = ForecastOrchestrator.getCurrentHumidity(key, serverLevel.getGameTime());
        String msg = humidity < 0.01f ? "Humidity: Loading..." :
                "Current humidity: " + UnitFormatter.formatHumidity(humidity);
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
        BiomeInstanceKey key = new BiomeInstanceKey(AtmosphereUtils.getBiomeLocation(pos, serverLevel), pos);
        double pressure = ForecastOrchestrator.getCurrentPressure(key, serverLevel.getGameTime());
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
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new InstrumentReadoutPacket(message));
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientDisplay {
        private static void displayWind(Level level, Player player) {
            BlockPos pos = player.blockPosition();
            BiomeInstanceKey key = new BiomeInstanceKey(
                    AtmosphereUtils.getBiomeLocation(pos, level), pos
            );
            WindVector wind = ForecastOrchestrator.getWind(key, level.getDayTime());
            String msg = "Wind: " + UnitFormatter.formatWindSpeed(wind.baseSpeed()) +
                    " at " + String.format("%.0f\u00B0", Math.toDegrees(wind.angleRadians()));
            HUDOverlayRenderer.showTemperatureOverlay(msg);
        }

        private static void displayTemperature(Level level, Player player) {
            BlockPos pos = player.blockPosition();
            float temp;

            switch (CompatHandler.getActiveTemperatureMod()) {
                case LEGENDARY_SURVIVAL -> temp = LegendarySurvivalCompat.getLiveTemperature(level, pos);
                case TOUGH_AS_NAILS -> temp = ToughAsNailsCompat.getLiveTemperatureTAN(level, pos);
                case COLD_SWEAT -> temp = ColdSweatCompat.getLiveTemperatureColdSweat(level, pos);
                default -> temp = getForecastTemperature(level, pos);
            }

            String msg = "Current temperature: " + UnitFormatter.formatTemperature(temp);
            HUDOverlayRenderer.showTemperatureOverlay(msg);
        }

        private static float getForecastTemperature(Level level, BlockPos pos) {
            return BiomeClientTemperatureCache.getTemperature(AtmosphereUtils.getBiomeLocation(pos, level), level);
        }

        private static void displayHumidity(Level level, Player player) {
            BlockPos pos = player.blockPosition();
            BiomeInstanceKey key = new BiomeInstanceKey(
                    AtmosphereUtils.getBiomeLocation(pos, level), pos
            );
            float humidity = ForecastOrchestrator.getCurrentHumidity(key, level.getDayTime());
            String msg = humidity < 0.01f ? "Humidity: Loading..." :
                    "Current humidity: " + UnitFormatter.formatHumidity(humidity);
            HUDOverlayRenderer.showTemperatureOverlay(msg);
        }

        private static void displayPressure(Level level, Player player) {
            BlockPos pos = player.blockPosition();
            BiomeInstanceKey key = new BiomeInstanceKey(
                    AtmosphereUtils.getBiomeLocation(pos, level), pos
            );
            double pressure = ForecastOrchestrator.getCurrentPressure(key, level.getDayTime());
            String msg = "Current pressure: " + UnitFormatter.formatPressure(pressure);
            HUDOverlayRenderer.showTemperatureOverlay(msg);
        }

        private static void displayStorm(Level level, Player player) {
            String msg;
            if (level.isThundering()) {
                msg = "Storm detected!";
            } else if (level.isRaining()) {
                msg = "Rain detected.";
            } else {
                msg = "Skies clear.";
            }
            HUDOverlayRenderer.showTemperatureOverlay(msg);
        }
    }
}
