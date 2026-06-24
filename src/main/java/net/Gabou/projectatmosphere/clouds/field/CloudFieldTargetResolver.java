package net.Gabou.projectatmosphere.clouds.field;

import net.Gabou.projectatmosphere.clouds.field.backend.CloudFieldFactory;
import net.Gabou.projectatmosphere.clouds.field.backend.CloudFieldSource;

import java.util.Objects;

/**
 * Converts neutral backend/source data into CloudField evolution targets.
 */
public final class CloudFieldTargetResolver {
    private static final double CENTER_EPSILON = 2.5D;
    private static final float RADIUS_EPSILON = 4.0F;
    private static final float VERTICAL_BOUNDS_EPSILON = 2.0F;
    private static final float SCALAR_EPSILON = 0.015F;
    private static final float STORM_EPSILON = 0.020F;
    private static final double WIND_EPSILON = 0.0125D;

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
        return resolve(currentField, source, 0);
    }

    /**
     * Resolves a target with source-loss context. While a source is missing,
     * persistent fields receive a gradually degrading target instead of being
     * deleted immediately.
     */
    public CloudFieldTarget resolve(CloudField currentField, CloudFieldSource source, int missingSourceTicks) {
        Objects.requireNonNull(currentField, "currentField");
        if (missingSourceTicks > 0) {
            return missingSourceTarget(currentField, source, missingSourceTicks);
        }
        if (source == null || !source.isUsable()) {
            return CloudFieldTarget.fromField(currentField);
        }

        float drynessPressure = clamp01(1.0F - source.humidityInfluence());
        float coverageGap = clamp01(1.0F - source.coverage());
        float decayPressure = clamp01(source.decay() + drynessPressure * coverageGap * 0.35F);

        CloudFieldTarget target = new CloudFieldTarget(
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
        return stabilizeTarget(currentField, target);
    }

    private CloudFieldTarget missingSourceTarget(CloudField currentField, CloudFieldSource source, int missingSourceTicks) {
        float missingRatio = clamp01((float) Math.max(0, missingSourceTicks) / (float) CloudFieldStore.DEFAULT_MISSING_SOURCE_GRACE_TICKS);
        float densityTarget = clamp01(currentField.density() * (1.0F - missingRatio * 0.45F));
        float coverageTarget = clamp01(currentField.coverage() * (1.0F - missingRatio * 0.55F));
        float hydrationTarget = clamp01(currentField.humidityInfluence() * (1.0F - missingRatio * 0.35F));
        float decayPressure = clamp01(Math.max(currentField.decay(), missingRatio * 0.92F));
        float growthTarget = clamp01(currentField.growth() * (1.0F - missingRatio * 0.60F));

        return new CloudFieldTarget(
                currentField.center(),
                currentField.radius(),
                currentField.baseY(),
                currentField.topY(),
                densityTarget,
                coverageTarget,
                hydrationTarget,
                currentField.verticalDevelopment(),
                currentField.stormPotential(),
                decayPressure,
                source == null ? currentField.windVector() : source.wind(),
                growthTarget,
                decayPressure,
                currentField.cloudletCount(),
                currentField.lifetimeTicks()
        );
    }

    private static CloudFieldTarget stabilizeTarget(CloudField currentField, CloudFieldTarget target) {
        return new CloudFieldTarget(
                holdSmallCenterDelta(currentField.center(), target.center(), CENTER_EPSILON),
                holdSmallDelta(currentField.radius(), target.radius(), RADIUS_EPSILON),
                holdSmallDelta(currentField.baseY(), target.baseY(), VERTICAL_BOUNDS_EPSILON),
                holdSmallDelta(currentField.topY(), target.topY(), VERTICAL_BOUNDS_EPSILON),
                holdSmallDelta(currentField.density(), target.density(), SCALAR_EPSILON),
                holdSmallDelta(currentField.coverage(), target.coverage(), SCALAR_EPSILON),
                holdSmallDelta(currentField.humidityInfluence(), target.targetHydration(), SCALAR_EPSILON),
                holdSmallDelta(currentField.verticalDevelopment(), target.verticalDevelopment(), SCALAR_EPSILON),
                holdSmallDelta(currentField.stormPotential(), target.stormPotential(), STORM_EPSILON),
                target.decayPressure(),
                holdSmallWindDelta(currentField.windVector(), target.windInfluence(), WIND_EPSILON),
                stabilizeGrowth(currentField, target),
                stabilizeDecay(currentField, target),
                target.cloudletCount(),
                target.lifetimeTicks()
        );
    }

    private static float stabilizeGrowth(CloudField currentField, CloudFieldTarget target) {
        if (target.decayPressure() > 0.20F && Math.abs(currentField.growth() - target.growth()) <= SCALAR_EPSILON * 2.0F) {
            return currentField.growth();
        }
        return holdSmallDelta(currentField.growth(), target.growth(), SCALAR_EPSILON);
    }

    private static float stabilizeDecay(CloudField currentField, CloudFieldTarget target) {
        if (target.growth() > 0.20F && Math.abs(currentField.decay() - target.decay()) <= SCALAR_EPSILON * 2.0F) {
            return currentField.decay();
        }
        return holdSmallDelta(currentField.decay(), target.decay(), SCALAR_EPSILON);
    }

    private static float holdSmallDelta(float current, float target, float epsilon) {
        if (!Float.isFinite(current)) {
            return target;
        }
        if (!Float.isFinite(target)) {
            return current;
        }
        return Math.abs(current - target) <= epsilon ? current : target;
    }

    private static net.minecraft.world.phys.Vec3 holdSmallCenterDelta(
            net.minecraft.world.phys.Vec3 current,
            net.minecraft.world.phys.Vec3 target,
            double epsilon
    ) {
        net.minecraft.world.phys.Vec3 safeCurrent = current == null ? net.minecraft.world.phys.Vec3.ZERO : current;
        net.minecraft.world.phys.Vec3 safeTarget = target == null ? net.minecraft.world.phys.Vec3.ZERO : target;
        return safeCurrent.distanceTo(safeTarget) <= epsilon ? safeCurrent : safeTarget;
    }

    private static net.minecraft.world.phys.Vec3 holdSmallWindDelta(
            net.minecraft.world.phys.Vec3 current,
            net.minecraft.world.phys.Vec3 target,
            double epsilon
    ) {
        net.minecraft.world.phys.Vec3 safeCurrent = current == null ? net.minecraft.world.phys.Vec3.ZERO : current;
        net.minecraft.world.phys.Vec3 safeTarget = target == null ? net.minecraft.world.phys.Vec3.ZERO : target;
        return safeCurrent.distanceTo(safeTarget) <= epsilon ? safeCurrent : safeTarget;
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
