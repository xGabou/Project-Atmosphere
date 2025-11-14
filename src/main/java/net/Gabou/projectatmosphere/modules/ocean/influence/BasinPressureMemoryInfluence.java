package net.Gabou.projectatmosphere.modules.ocean.influence;

import net.Gabou.projectatmosphere.modules.ocean.OceanBasin;
import net.Gabou.projectatmosphere.modules.ocean.OceanInfluence;
import net.Gabou.projectatmosphere.modules.ocean.OceanUpdateContext;
import net.minecraft.util.Mth;

/**
 * Maintains a slow varying pressure offset and deep temperature drift.
 */
public final class BasinPressureMemoryInfluence implements OceanInfluence {
    private final float pressureRelaxation;
    private final float deepRelaxation;

    public BasinPressureMemoryInfluence(float pressureRelaxation, float deepRelaxation) {
        this.pressureRelaxation = pressureRelaxation;
        this.deepRelaxation = deepRelaxation;
    }

    public BasinPressureMemoryInfluence() {
        this(0.0007f, 0.0002f);
    }

    @Override
    public void applyTo(OceanBasin basin, OceanUpdateContext context) {
        float targetPressure = basin.getBasePressure() - 1013.25f;
        float pressure = Mth.lerp(pressureRelaxation, basin.getPressureOffset(), targetPressure);
        basin.setPressureOffset(pressure);

        float targetDeep = Mth.lerp(0.5f, basin.getBaseSurfaceTemperature(), basin.getDeepTemperature());
        float deepTemp = Mth.lerp(deepRelaxation, basin.getDeepTemperature(), targetDeep);
        basin.setDeepTemperature(deepTemp);
    }
}
