package net.Gabou.projectatmosphere.modules.atmosphere;

import net.Gabou.projectatmosphere.util.RegionInstanceKey;

import java.util.List;

final class CycloneImpactApplier {
    private CycloneImpactApplier() {
    }

    static void apply(CycloneStep step) {
        if (step.deltas().isEmpty()) {
            return;
        }
        for (CycloneDelta delta : step.deltas()) {
            RegionAtmosphereState state = AtmosphericStateRegistry.getState(delta.key());
            if (state == null) {
                continue;
            }
            state.adjustTemperature(delta.temperatureDelta());
            state.adjustHumidity(delta.humidityDelta());
            state.adjustPressure(delta.pressureDelta());
            state.applyCycloneVisualFloor(delta.cloudCeil(), delta.rainCeil());
            state.setRainIntensity(Math.min(1f, Math.max(state.getRainIntensity(), delta.rainCeil())));
            state.setCloudCover(Math.min(1f, Math.max(state.getCloudCover(), delta.cloudCeil())));
            float cycloneWaterFloor = Math.min(1.05f, delta.cloudCeil() * 0.26f + delta.rainCeil() * 0.46f);
            state.setCloudWater(Math.max(state.getCloudWater(), cycloneWaterFloor));
        }
    }

    record CycloneStep(boolean remove, List<CycloneDelta> deltas) {
    }

    record CycloneDelta(RegionInstanceKey key,
                        float temperatureDelta,
                        float humidityDelta,
                        float pressureDelta,
                        float rainCeil,
                        float cloudCeil) {
    }
}
