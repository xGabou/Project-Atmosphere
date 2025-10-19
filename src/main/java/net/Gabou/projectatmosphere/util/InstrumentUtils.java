package net.Gabou.projectatmosphere.util;

import net.Gabou.projectatmosphere.client.BiomeClientTemperatureCache;
import net.Gabou.projectatmosphere.client.HUDOverlayRenderer;
import net.Gabou.projectatmosphere.compat.ColdSweatCompat;
import net.Gabou.projectatmosphere.compat.CompatHandler;

import net.Gabou.projectatmosphere.compat.ToughAsNailsCompat;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class InstrumentUtils {

    public static void displayWind(Level level, Player player) {
        if (!level.isClientSide) return;

        BlockPos pos = player.blockPosition();
        BiomeInstanceKey key = new BiomeInstanceKey(
                AtmosphereUtils.getBiomeLocation(pos, level), pos
        );
        WindVector wind = ForecastOrchestrator.getCurrentWind(key, level.getDayTime());
        String msg = "Wind: " + UnitFormatter.formatWindSpeed(wind.baseSpeed()) +
                " at " + String.format("%.0f°", Math.toDegrees(wind.angleRadians()));
        HUDOverlayRenderer.showTemperatureOverlay(msg);
    }

    public static void displayTemperature(Level level, Player player) {
        if (!level.isClientSide) return;

        BlockPos pos = player.blockPosition();
        float temp;

        switch (CompatHandler.getActiveTemperatureMod()) {
            case TOUGH_AS_NAILS -> temp = ToughAsNailsCompat.getLiveTemperatureTAN(level, pos);
            case COLD_SWEAT -> temp = ColdSweatCompat.getLiveTemperatureColdSweat(level, pos);
            default -> temp = getForecastTemperature(level, pos);
        }

        String msg = "Current temperature: " + UnitFormatter.formatTemperature(temp);
        HUDOverlayRenderer.showTemperatureOverlay(msg);
    }

    private static float getForecastTemperature(Level level, BlockPos pos) {
        return BiomeClientTemperatureCache.getTemperature(AtmosphereUtils.getBiomeLocation(pos, level),level);
    }

    public static void displayHumidity(Level level, Player player) {
        if (!level.isClientSide) return;

        BlockPos pos = player.blockPosition();
        BiomeInstanceKey key = new BiomeInstanceKey(
                AtmosphereUtils.getBiomeLocation(pos, level), pos
        );
        float humidity = ForecastOrchestrator.getCurrentHumidity(key, level.getDayTime());
        String msg = humidity < 0.01f ? "Humidity: Loading..." :
                "Current humidity: " + UnitFormatter.formatHumidity(humidity);
        HUDOverlayRenderer.showTemperatureOverlay(msg);
    }

    public static void displayPressure(Level level, Player player) {
        if (!level.isClientSide) return;

        BlockPos pos = player.blockPosition();
        BiomeInstanceKey key = new BiomeInstanceKey(
                AtmosphereUtils.getBiomeLocation(pos, level), pos
        );
        double pressure = ForecastOrchestrator.getCurrentPressure(key, level.getDayTime());
        String msg = "Current pressure: " + UnitFormatter.formatPressure(pressure);
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

