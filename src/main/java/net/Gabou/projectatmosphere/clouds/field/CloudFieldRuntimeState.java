package net.Gabou.projectatmosphere.clouds.field;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.UUID;

/**
 * Runtime-only state that should not be part of the pure CloudField
 * definition. This is client/cache state used to smooth LOD hydration.
 */
public record CloudFieldRuntimeState(
        UUID fieldId,
        CloudLodBand currentLodBand,
        CloudLodBand previousLodBand,
        CloudFieldHydrationState hydrationState,
        float hydrationProgress,
        long lastUpdateWorldTime,
        int currentCloudletCount,
        Vec3 previousCenter
) {
    public CloudFieldRuntimeState {
        fieldId = Objects.requireNonNull(fieldId, "fieldId");
        currentLodBand = currentLodBand == null ? CloudLodBand.HAZE : currentLodBand;
        previousLodBand = previousLodBand == null ? currentLodBand : previousLodBand;
        hydrationState = hydrationState == null ? CloudFieldHydrationState.NOT_HYDRATED : hydrationState;
        hydrationProgress = clamp01(hydrationProgress);
        currentCloudletCount = Math.max(0, currentCloudletCount);
        previousCenter = previousCenter == null ? Vec3.ZERO : previousCenter;
    }

    public static CloudFieldRuntimeState initial(CloudField field, CloudLodBand lodBand, long worldTime) {
        Objects.requireNonNull(field, "field");
        CloudLodBand band = lodBand == null ? CloudLodBand.HAZE : lodBand;
        return new CloudFieldRuntimeState(
                field.fieldId(),
                band,
                band,
                CloudFieldHydrationState.NOT_HYDRATED,
                0.0F,
                worldTime,
                0,
                field.center()
        );
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, Float.isFinite(value) ? value : 0.0F));
    }
}
