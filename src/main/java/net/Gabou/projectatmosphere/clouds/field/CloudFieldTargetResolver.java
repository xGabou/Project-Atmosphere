package net.Gabou.projectatmosphere.clouds.field;

import net.Gabou.projectatmosphere.clouds.field.backend.CloudFieldFactory;
import net.Gabou.projectatmosphere.clouds.field.backend.CloudFieldSource;

import java.util.Objects;

/**
 * Converts neutral backend/source data into CloudField evolution targets.
 */
public final class CloudFieldTargetResolver {
    private final CloudFieldFactory fieldFactory;

    public CloudFieldTargetResolver(CloudFieldFactory fieldFactory) {
        this.fieldFactory = fieldFactory == null ? new CloudFieldFactory() : fieldFactory;
    }

    /**
     * Creates the resolver used by the default CloudField runtime pipeline.
     */
    public static CloudFieldTargetResolver createDefault() {
        return new CloudFieldTargetResolver(new CloudFieldFactory());
    }

    /**
     * Resolves the desired state for a current field without mutating field
     * storage or renderer state.
     */
    public CloudFieldTarget resolve(CloudField currentField, CloudFieldSource source) {
        Objects.requireNonNull(currentField, "currentField");
        if (source == null || !source.isUsable()) {
            return CloudFieldTarget.fromField(currentField);
        }

        float drynessPressure = clamp01(1.0F - source.humidityInfluence());
        float coverageGap = clamp01(1.0F - source.coverage());
        float decayPressure = clamp01(source.decay() + drynessPressure * coverageGap * 0.35F);

        return new CloudFieldTarget(
                source.center(),
                source.radius(),
                source.baseY(),
                source.topY(),
                source.density(),
                source.coverage(),
                source.humidityInfluence(),
                source.verticalDevelopment(),
                source.stormPotential(),
                decayPressure,
                source.wind(),
                source.growth(),
                source.decay(),
                fieldFactory.cloudletCountFor(source),
                source.lifetimeTicks()
        );
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
