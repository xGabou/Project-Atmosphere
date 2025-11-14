package net.Gabou.projectatmosphere.modules.ocean.influence;

import net.Gabou.projectatmosphere.modules.ocean.OceanBasin;
import net.Gabou.projectatmosphere.modules.ocean.OceanInfluence;
import net.Gabou.projectatmosphere.modules.ocean.OceanUpdateContext;
import net.minecraft.util.Mth;

/**
 * Slow relaxation of basin state toward its long term reservoirs.
 */
public final class BasinThermalMemoryInfluence implements OceanInfluence {
    private final float surfaceRelaxation;
    private final float memoryRelaxation;
    private final float humidityRecovery;
    private final float anomalyDecay;

    public BasinThermalMemoryInfluence(float surfaceRelaxation, float memoryRelaxation, float humidityRecovery, float anomalyDecay) {
        this.surfaceRelaxation = surfaceRelaxation;
        this.memoryRelaxation = memoryRelaxation;
        this.humidityRecovery = humidityRecovery;
        this.anomalyDecay = anomalyDecay;
    }

    public BasinThermalMemoryInfluence() {
        this(0.0009f, 0.00015f, 0.0006f, 0.0004f);
    }

    @Override
    public void applyTo(OceanBasin basin, OceanUpdateContext context) {
        float targetSurface = basin.getThermalMemory() + basin.getMultiDayAnomaly();
        float surface = Mth.lerp(surfaceRelaxation, basin.getSurfaceTemperature(), targetSurface);
        basin.setSurfaceTemperature(surface);

        float memory = Mth.lerp(memoryRelaxation, basin.getThermalMemory(), basin.getBaseSurfaceTemperature());
        basin.setThermalMemory(memory);

        float humidity = Mth.lerp(humidityRecovery, basin.getHumidityReservoir(), basin.getBaseHumidity());
        basin.setHumidityReservoir(humidity);

        float anomaly = basin.getMultiDayAnomaly() * (1f - anomalyDecay);
        basin.setMultiDayAnomaly(anomaly);
    }
}
