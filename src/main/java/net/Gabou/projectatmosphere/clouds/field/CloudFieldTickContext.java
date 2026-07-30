package net.Gabou.projectatmosphere.clouds.field;

import net.minecraft.world.phys.Vec3;

/**
 * Immutable input for one CloudField runtime tick.
 */
public record CloudFieldTickContext(
        Vec3 cameraPosition,
        long worldTime,
        float deltaTicks,
        float partialTick,
        CloudFieldDistanceClassifier distanceClassifier
) {
    public CloudFieldTickContext {
        cameraPosition = cameraPosition == null ? Vec3.ZERO : cameraPosition;
        deltaTicks = Math.max(0.0F, finite(deltaTicks, 0.0F));
        partialTick = finite(partialTick, 0.0F);
        distanceClassifier = distanceClassifier == null
                ? CloudFieldDistanceClassifier.defaultClassifier()
                : distanceClassifier;
    }

    public static CloudFieldTickContext of(Vec3 cameraPosition, long worldTime, float deltaTicks) {
        return new CloudFieldTickContext(
                cameraPosition,
                worldTime,
                deltaTicks,
                0.0F,
                CloudFieldDistanceClassifier.defaultClassifier()
        );
    }

    private static float finite(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }
}
