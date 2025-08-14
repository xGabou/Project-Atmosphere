package net.Gabou.projectatmosphere.util;

import net.Gabou.projectatmosphere.client.HUDOverlayRenderer;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class InstrumentUtils {

    public static void displayWind(Level level, Player player) {
        if (!level.isClientSide) return;

        BlockPos pos = player.blockPosition();
        BiomeInstanceKey key = new BiomeInstanceKey(
                AtmosphereUtils.getBiomeLocation(pos, level), pos
        );
        WindVector wind = ForecastOrchestrator.getCurrentWind(key, level.getDayTime());
        String msg = "Wind: " + String.format("%.1fm/s at %.0f°", wind.baseSpeed(), Math.toDegrees(wind.angleRadians()));
        HUDOverlayRenderer.showTemperatureOverlay(msg);
    }

    public static void displayTemperature(Level level, Player player) {
        if (!level.isClientSide) return;

        BlockPos pos = player.blockPosition();
        BiomeInstanceKey key = new BiomeInstanceKey(
                AtmosphereUtils.getBiomeLocation(pos, level), pos
        );
        float temp = ForecastOrchestrator.getCurrentTemperature(key, level.getDayTime());
        String msg = "Current temperature: " + String.format("%.1f°C", temp);
        HUDOverlayRenderer.showTemperatureOverlay(msg);
    }

    public static void displayHumidity(Level level, Player player) {
        if (!level.isClientSide) return;

        BlockPos pos = player.blockPosition();
        BiomeInstanceKey key = new BiomeInstanceKey(
                AtmosphereUtils.getBiomeLocation(pos, level), pos
        );
        float humidity = ForecastOrchestrator.getCurrentHumidity(key, level.getDayTime());
        String msg = humidity < 0.01f ? "Humidity: Loading..." :
                "Current humidity: " + String.format("%.1f%%", humidity);
        HUDOverlayRenderer.showTemperatureOverlay(msg);
    }

    public static void displayPressure(Level level, Player player) {
        if (!level.isClientSide) return;

        BlockPos pos = player.blockPosition();
        BiomeInstanceKey key = new BiomeInstanceKey(
                AtmosphereUtils.getBiomeLocation(pos, level), pos
        );
        double pressure = ForecastOrchestrator.getCurrentPressure(key, level.getDayTime());
        String msg = "Current pressure: " + String.format("%.1fhPa", pressure);
        HUDOverlayRenderer.showTemperatureOverlay(msg);
    }

    public static void displayStorm(Level level, Player player) {
        if (!level.isClientSide) return;

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
