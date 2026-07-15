package net.Gabou.projectatmosphere.clouds.field;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * Evolves persistent CloudField values toward resolved backend/weather targets.
 */
public final class CloudFieldEvolutionController {
    private final Config config;

    public CloudFieldEvolutionController(Config config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Creates the evolution controller used by the default CloudField runtime
     * pipeline.
     */
    public static CloudFieldEvolutionController createDefault() {
        return new CloudFieldEvolutionController(Config.DEFAULT);
    }

    /**
     * Returns the next persistent field state for one tick. This method does
     * not create snapshots, send packets, or render.
     */
    public CloudField evolve(
            CloudField currentField,
            CloudFieldTarget target,
            CloudFieldTickContext context
    ) {
        Objects.requireNonNull(currentField, "currentField");
        CloudFieldTarget resolvedTarget = target == null ? CloudFieldTarget.fromField(currentField) : target;
        CloudFieldTickContext tickContext = context == null
                ? CloudFieldTickContext.of(currentField.center(), 0L, 0.0F)
                : context;
        float ticks = Math.max(0.0F, tickContext.deltaTicks());

        Vec3 wind = approachVec(
                currentField.windVector(),
                resolvedTarget.windInfluence(),
                config.windChangePerTick() * ticks
        );
        Vec3 windMovedCenter = currentField.center().add(wind.scale(ticks));
        Vec3 center = approachVecByDistance(
                windMovedCenter,
                resolvedTarget.center(),
                Math.max(config.centerCorrectionPerTick() * ticks, wind.length() * ticks * 0.35D)
        );

        float decayPressure = resolvedTarget.decayPressure();
        float densityRate = config.densityChangePerTick() * (decayPressure > 0.70F ? 1.65F : 1.0F);
        float coverageRate = config.coverageChangePerTick() * (decayPressure > 0.70F ? 1.45F : 1.0F);
        float stormRate = config.stormPotentialPerTick() * (resolvedTarget.stormPotential() > currentField.stormPotential() ? 1.0F : 2.4F);

        float density = approach(currentField.density(), resolvedTarget.density(), densityRate * ticks);
        float coverage = approach(currentField.coverage(), resolvedTarget.coverage(), coverageRate * ticks);
        float growth = approach(currentField.growth(), resolvedTarget.growth(), config.growthChangePerTick() * ticks);
        float decay = approach(
                currentField.decay(),
                Math.max(resolvedTarget.decay(), decayPressure),
                config.decayChangePerTick() * ticks
        );
        float humidityInfluence = approach(
                currentField.humidityInfluence(),
                resolvedTarget.targetHydration(),
                config.hydrationChangePerTick() * ticks
        );

        int cloudletCount = approachInt(
                currentField.cloudletCount(),
                resolvedTarget.cloudletCount(),
                Math.max(1, Math.round(config.cloudletChangePerTick() * ticks))
        );

        long ageTicks = currentField.ageTicks() + Math.round(ticks);
        long lifetimeTicks = resolvedTarget.lifetimeTicks() > 0L
                ? resolvedTarget.lifetimeTicks()
                : currentField.lifetimeTicks();

        return new CloudField(
                currentField.fieldId(),
                currentField.seed(),
                currentField.dimensionId(),
                center,
                approach(currentField.radius(), resolvedTarget.radius(), config.radiusBlocksPerTick() * ticks),
                approach(currentField.baseY(), resolvedTarget.baseY(), config.verticalBoundsBlocksPerTick() * ticks),
                approach(currentField.topY(), resolvedTarget.topY(), config.verticalBoundsBlocksPerTick() * ticks),
                density,
                coverage,
                growth,
                decay,
                humidityInfluence,
                wind,
                approach(
                        currentField.verticalDevelopment(),
                        resolvedTarget.verticalDevelopment(),
                        config.verticalDevelopmentPerTick() * ticks
                ),
                approach(currentField.stormPotential(), resolvedTarget.stormPotential(), stormRate * ticks),
                resolvedTarget.cloudTypeId(),
                resolvedTarget.morphologyFamily(),
                currentField.morphologyMembership(),
                approach(currentField.anvilStrength(), resolvedTarget.anvilStrength(), stormRate * ticks),
                approach(
                        currentField.precipitationIntensity(),
                        resolvedTarget.precipitationIntensity(),
                        stormRate * ticks
                ),
                cloudletCount,
                ageTicks,
                lifetimeTicks
        );
    }

    public Config config() {
        return config;
    }

    private static float approach(float current, float target, float maxDelta) {
        float safeCurrent = finite(current, 0.0F);
        float safeTarget = finite(target, safeCurrent);
        float safeDelta = Math.max(0.0F, finite(maxDelta, 0.0F));
        float delta = safeTarget - safeCurrent;
        if (Math.abs(delta) <= safeDelta) {
            return safeTarget;
        }
        return safeCurrent + Math.copySign(safeDelta, delta);
    }

    private static int approachInt(int current, int target, int maxDelta) {
        int delta = target - current;
        int step = Math.max(0, maxDelta);
        if (Math.abs(delta) <= step) {
            return Math.max(0, target);
        }
        return Math.max(0, current + (delta > 0 ? step : -step));
    }

    private static Vec3 approachVec(Vec3 current, Vec3 target, double maxDelta) {
        Vec3 safeCurrent = current == null ? Vec3.ZERO : current;
        Vec3 safeTarget = target == null ? Vec3.ZERO : target;
        return approachVecByDistance(safeCurrent, safeTarget, maxDelta);
    }

    private static Vec3 approachVecByDistance(Vec3 current, Vec3 target, double maxDistance) {
        Vec3 safeCurrent = current == null ? Vec3.ZERO : current;
        Vec3 safeTarget = target == null ? Vec3.ZERO : target;
        double safeDistance = Math.max(0.0D, Double.isFinite(maxDistance) ? maxDistance : 0.0D);
        Vec3 delta = safeTarget.subtract(safeCurrent);
        double length = delta.length();
        if (length <= safeDistance || length <= 0.000001D) {
            return safeTarget;
        }
        return safeCurrent.add(delta.scale(safeDistance / length));
    }

    private static float finite(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    public record Config(
            float centerCorrectionPerTick,
            float radiusBlocksPerTick,
            float verticalBoundsBlocksPerTick,
            float densityChangePerTick,
            float coverageChangePerTick,
            float hydrationChangePerTick,
            float growthChangePerTick,
            float decayChangePerTick,
            float verticalDevelopmentPerTick,
            float stormPotentialPerTick,
            float windChangePerTick,
            float cloudletChangePerTick
    ) {
        public static final Config DEFAULT = new Config(
                0.55F,
                0.45F,
                0.35F,
                0.0065F,
                0.0055F,
                0.0060F,
                0.0060F,
                0.0080F,
                0.0035F,
                0.0015F,
                0.0150F,
                2.0F
        );

        public Config {
            centerCorrectionPerTick = nonNegative(centerCorrectionPerTick, 0.55F);
            radiusBlocksPerTick = nonNegative(radiusBlocksPerTick, 0.45F);
            verticalBoundsBlocksPerTick = nonNegative(verticalBoundsBlocksPerTick, 0.35F);
            densityChangePerTick = nonNegative(densityChangePerTick, 0.0065F);
            coverageChangePerTick = nonNegative(coverageChangePerTick, 0.0055F);
            hydrationChangePerTick = nonNegative(hydrationChangePerTick, 0.0060F);
            growthChangePerTick = nonNegative(growthChangePerTick, 0.0060F);
            decayChangePerTick = nonNegative(decayChangePerTick, 0.0080F);
            verticalDevelopmentPerTick = nonNegative(verticalDevelopmentPerTick, 0.0035F);
            stormPotentialPerTick = nonNegative(stormPotentialPerTick, 0.0015F);
            windChangePerTick = nonNegative(windChangePerTick, 0.0150F);
            cloudletChangePerTick = nonNegative(cloudletChangePerTick, 2.0F);
        }

        private static float nonNegative(float value, float fallback) {
            float safeValue = Float.isFinite(value) ? value : fallback;
            return Math.max(0.0F, safeValue);
        }
    }
}
