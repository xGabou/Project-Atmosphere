package net.Gabou.projectatmosphere.clouds.visual;

import net.Gabou.projectatmosphere.clouds.api.CloudShadowMapAccess;
import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily;
import net.Gabou.projectatmosphere.modules.weather.StormVisualTier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Shared cloud lighting calculations for fallback darkening and future shader
 * integration. Uses {@link CloudVisualState} and {@link CloudVisualMetrics}
 * without duplicating metric derivation from transport data.
 */
public final class CloudLightingEvaluation {
    private static final float MIN_CANDIDATE_OPACITY = 0.06F;
    private static final float WEAK_PUFF_SHADOW_CAP = 0.32F;
    private static final float WEAK_PUFF_STORM_CAP = 0.18F;
    private static final float WEAK_PUFF_RADIUS = 140.0F;

    private CloudLightingEvaluation() {
    }

    public static float evaluateCloudDarkness(@Nullable CloudVisualState state) {
        if (state == null) {
            return 0.0F;
        }
        float coverageInfluence = evaluateCoverageInfluence(state);
        float precipitationInfluence = evaluatePrecipitationInfluence(state);
        return CloudVisualMetrics.clamp01(
                state.visualDarkness() * 0.52F
                        + coverageInfluence * 0.22F
                        + precipitationInfluence * 0.16F
                        + state.stormStrength() * 0.10F
        );
    }

    public static float evaluateStormDarkness(@Nullable CloudVisualState state) {
        if (state == null) {
            return 0.0F;
        }
        float tierDarkness = state.stormVisualTier().getDarkness();
        return CloudVisualMetrics.clamp01(
                state.stormStrength() * 0.58F
                        + tierDarkness * 0.28F
                        + evaluatePrecipitationInfluence(state) * 0.14F
        );
    }

    public static float evaluateCoverageInfluence(@Nullable CloudVisualState state) {
        if (state == null) {
            return 0.0F;
        }
        return CloudVisualMetrics.clamp01(
                state.coverage() * 0.55F
                        + state.opacity() * 0.30F
                        + Mth.clamp(state.effectiveRadius() / 900.0F, 0.0F, 1.0F) * 0.15F
        );
    }

    public static float evaluatePrecipitationInfluence(@Nullable CloudVisualState state) {
        if (state == null) {
            return 0.0F;
        }
        return CloudVisualMetrics.clamp01(
                state.precipitationStrength() * 0.72F
                        + state.precipitationTier().getRepresentativeIntensity() * 0.28F
        );
    }

    public static float evaluateShadowIntensity(@Nullable CloudVisualState state, @NotNull Vec3 samplePosition) {
        if (state == null) {
            return 0.0F;
        }
        float footprint = evaluateHorizontalFootprint(state, samplePosition);
        if (footprint <= 0.001F) {
            return 0.0F;
        }
        float base = CloudVisualMetrics.clamp01(
                state.shadowPotential() * 0.46F
                        + evaluateCloudDarkness(state) * 0.24F
                        + evaluateStormDarkness(state) * 0.18F
                        + evaluateCoverageInfluence(state) * 0.12F
        );
        return CloudVisualMetrics.clamp01(base * footprint);
    }

    public static float evaluatePlayerShadowContribution(@Nullable CloudVisualState state, @NotNull Vec3 playerPosition) {
        return evaluateShadowIntensity(state, playerPosition);
    }

    public static float combineShadowContributions(float accumulated, float contribution) {
        contribution = CloudVisualMetrics.clamp01(contribution);
        if (contribution <= 0.0F) {
            return accumulated;
        }
        return CloudVisualMetrics.clamp01(1.0F - (1.0F - accumulated) * (1.0F - contribution));
    }

    public static float evaluateMapBackedShadow(@NotNull Vec3 playerPosition) {
        return CloudVisualMetrics.clamp01(CloudShadowMapAccess.sampleShadowAt(playerPosition.x, playerPosition.z));
    }

    public static float evaluateBlendedPlayerShadowIntensity(
            @NotNull Iterable<CloudVisualState> candidates,
            @NotNull Vec3 playerPosition,
            float mapShadow
    ) {
        float accumulated = CloudVisualMetrics.clamp01(mapShadow * 0.55F);
        for (CloudVisualState candidate : candidates) {
            if (!isFallbackDarkeningCandidate(candidate)) {
                continue;
            }
            accumulated = combineShadowContributions(
                    accumulated,
                    evaluatePlayerShadowContribution(candidate, playerPosition)
            );
        }
        return CloudVisualMetrics.clamp01(accumulated);
    }

    public static float evaluatePlayerCloudDarknessFactor(
            @NotNull Iterable<CloudVisualState> candidates,
            @NotNull Vec3 playerPosition,
            float shadowIntensity
    ) {
        float darkness = shadowIntensity * 0.62F;
        for (CloudVisualState candidate : candidates) {
            if (!isFallbackDarkeningCandidate(candidate)) {
                continue;
            }
            float footprint = evaluateHorizontalFootprint(candidate, playerPosition);
            if (footprint <= 0.001F) {
                continue;
            }
            darkness = combineShadowContributions(
                    darkness,
                    evaluateCloudDarkness(candidate) * footprint * 0.78F
            );
        }
        return CloudVisualMetrics.clamp01(darkness);
    }

    public static float evaluatePlayerStormDarknessFactor(
            @NotNull Iterable<CloudVisualState> candidates,
            @NotNull Vec3 playerPosition,
            float shadowIntensity
    ) {
        float stormDarkness = shadowIntensity * 0.48F;
        for (CloudVisualState candidate : candidates) {
            if (!isFallbackDarkeningCandidate(candidate)) {
                continue;
            }
            float footprint = evaluateHorizontalFootprint(candidate, playerPosition);
            if (footprint <= 0.001F) {
                continue;
            }
            stormDarkness = combineShadowContributions(
                    stormDarkness,
                    evaluateStormDarkness(candidate) * footprint * 0.85F
            );
        }
        return CloudVisualMetrics.clamp01(stormDarkness);
    }

    public static boolean isFallbackDarkeningCandidate(@Nullable CloudVisualState state) {
        if (state == null || state.opacity() < MIN_CANDIDATE_OPACITY) {
            return false;
        }

        CloudMorphologyFamily family = state.morphologyFamily();
        if (family == CloudMorphologyFamily.FILAMENT) {
            return false;
        }

        if (family == CloudMorphologyFamily.STORM_ANVIL && state.shadowPotential() >= 0.15F) {
            return true;
        }
        if (family == CloudMorphologyFamily.TOWER && state.stormStrength() >= 0.22F) {
            return true;
        }
        if (family == CloudMorphologyFamily.SPIRAL_STORM) {
            return true;
        }

        String typeId = normalizeTypeId(state.cloudTypeId());
        if (typeId.contains("nimbostratus") || typeId.contains("blizzard")) {
            return state.shadowPotential() >= 0.20F;
        }

        if ((family == CloudMorphologyFamily.SHEET || family == CloudMorphologyFamily.CELLULAR_SHEET)
                && state.visualDarkness() >= 0.40F
                && state.coverage() >= 0.52F
                && state.shadowPotential() >= 0.26F) {
            return true;
        }

        if (state.stormVisualTier().ordinal() >= StormVisualTier.THUNDER_CORE.ordinal()
                && state.shadowPotential() >= 0.20F) {
            return true;
        }

        if (family == CloudMorphologyFamily.PUFF) {
            if (state.stormStrength() < WEAK_PUFF_STORM_CAP
                    && state.shadowPotential() < WEAK_PUFF_SHADOW_CAP
                    && state.effectiveRadius() < WEAK_PUFF_RADIUS) {
                return false;
            }
        }

        return state.isShadowCandidate()
                && state.shadowPotential() >= 0.30F
                && state.stormStrength() >= 0.18F;
    }

    @Nullable
    public static CloudVisualState findStrongestCandidate(@NotNull Iterable<CloudVisualState> candidates, @NotNull Vec3 playerPosition) {
        CloudVisualState strongest = null;
        float strongestIntensity = 0.0F;
        for (CloudVisualState candidate : candidates) {
            if (!isFallbackDarkeningCandidate(candidate)) {
                continue;
            }
            float intensity = evaluatePlayerShadowContribution(candidate, playerPosition);
            if (intensity > strongestIntensity) {
                strongestIntensity = intensity;
                strongest = candidate;
            }
        }
        return strongest;
    }

    private static float evaluateHorizontalFootprint(@NotNull CloudVisualState state, @NotNull Vec3 samplePosition) {
        double dx = samplePosition.x() - state.position().x();
        double dz = samplePosition.z() - state.position().z();
        float radius = Math.max(48.0F, state.effectiveRadius() * 1.18F);
        float distanceNorm = (float) Math.sqrt(dx * dx + dz * dz) / radius;
        if (distanceNorm >= 1.0F) {
            return 0.0F;
        }
        return smoothstep(0.42F, 1.0F, distanceNorm);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        if (edge0 == edge1) {
            return value < edge0 ? 0.0F : 1.0F;
        }
        float t = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return 1.0F - t * t * (3.0F - 2.0F * t);
    }

    private static String normalizeTypeId(@Nullable String cloudTypeId) {
        if (cloudTypeId == null || cloudTypeId.isBlank()) {
            return "";
        }
        return cloudTypeId.trim().toLowerCase(Locale.ROOT);
    }
}
