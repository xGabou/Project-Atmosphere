package net.Gabou.projectatmosphere.manager;

import net.Gabou.projectatmosphere.api.CropStressEvent;
import net.Gabou.projectatmosphere.api.CropStressType;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;

import java.util.EnumSet;

/**
 * Provides server-side evaluation of crop stress based on current
 * temperature and humidity. This does not interact with crops directly,
 * but instead exposes a method and event for other systems to consume.
 */
public class CropStressManager {
    private static final float DROUGHT_THRESHOLD = 15f;
    private static final float OVERWATER_THRESHOLD = 90f;
    private static final float HEAT_THRESHOLD = 35f;
    private static final float COLD_THRESHOLD = 0f;

    /**
     * Evaluates environmental stress at the given position and posts a
     * {@link CropStressEvent} if any stress conditions are met.
     *
     * @param level the world to sample
     * @param pos   the position being evaluated
     * @return the set of stress types detected (empty if none)
     */
    public static EnumSet<CropStressType> evaluate(ServerLevel level, BlockPos pos) {
        BiomeInstanceKey key = AtmosphereUtils.getBiomeKey(level, pos);
        long tick = level.getGameTime();
        float humidity = ForecastOrchestrator.getCurrentHumidity(key, tick);
        float temperature = ForecastOrchestrator.getCurrentTemperature(key, tick);

        EnumSet<CropStressType> stresses = EnumSet.noneOf(CropStressType.class);
        if (humidity < DROUGHT_THRESHOLD) {
            stresses.add(CropStressType.DROUGHT);
        }
        if (humidity > OVERWATER_THRESHOLD) {
            stresses.add(CropStressType.OVERWATERED);
        }
        if (temperature > HEAT_THRESHOLD) {
            stresses.add(CropStressType.HEAT);
        }
        if (temperature < COLD_THRESHOLD) {
            stresses.add(CropStressType.COLD);
        }

        if (!stresses.isEmpty()) {
            NeoForge.EVENT_BUS.post(new CropStressEvent(level, pos, stresses, temperature, humidity));
        }
        return stresses;
    }
}

