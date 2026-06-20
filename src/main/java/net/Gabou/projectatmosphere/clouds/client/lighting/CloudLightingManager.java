package net.Gabou.projectatmosphere.clouds.client.lighting;

import net.Gabou.projectatmosphere.clouds.client.CloudRenderSnapshot;
import net.Gabou.projectatmosphere.clouds.client.render.CloudDensityProvider;
import net.Gabou.projectatmosphere.clouds.visual.CloudLightingEvaluation;
import net.Gabou.projectatmosphere.clouds.visual.CloudVisualState;
import net.Gabou.projectatmosphere.clouds.visual.CloudVisualStateManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Read-only client lighting state for fallback darkening and future shader or
 * Distant Horizons integration.
 */
public final class CloudLightingManager {
    private static final float SMOOTH_ATTACK = 0.16F;
    private static final float SMOOTH_RELEASE = 0.08F;

    private static volatile LightingFrame currentFrame = LightingFrame.EMPTY;

    private CloudLightingManager() {
    }

    public static void update(@Nullable ClientLevel level, @NotNull Vec3 playerPosition, float partialTick) {
        if (level == null) {
            currentFrame = LightingFrame.EMPTY;
            return;
        }

        List<CloudVisualState> candidates = CloudVisualStateManager.getFallbackDarkeningCandidates(level);
        updateFromCandidates(candidates, playerPosition, partialTick);
    }

    public static void updateFromSnapshots(
            @NotNull List<CloudRenderSnapshot> snapshots,
            @NotNull Vec3 playerPosition,
            float partialTick
    ) {
        if (snapshots.isEmpty()) {
            currentFrame = LightingFrame.EMPTY;
            return;
        }

        List<CloudVisualState> candidates = toFallbackCandidates(snapshots);
        float targetShadowIntensity = CloudLightingEvaluation.evaluateMapBackedShadow(playerPosition) * 0.55F;
        float targetCloudDarkness = targetShadowIntensity * 0.62F;
        float targetStormDarkness = targetShadowIntensity * 0.48F;
        CloudVisualState strongest = null;
        float strongestContribution = 0.0F;

        for (CloudRenderSnapshot snapshot : snapshots) {
            if (snapshot == null || !CloudDensityProvider.hasVisibleDensity(snapshot)) {
                continue;
            }
            CloudVisualState state = toVisualState(snapshot);
            if (!CloudLightingEvaluation.isFallbackDarkeningCandidate(state)) {
                continue;
            }

            float density = CloudDensityProvider.sampleFallbackDarkeningDensity(snapshot, playerPosition);
            if (density <= 0.001F) {
                continue;
            }

            float shadowContribution = Mth.clamp(density * state.shadowPotential(), 0.0F, 1.0F);
            float cloudContribution = Mth.clamp(density * CloudLightingEvaluation.evaluateCloudDarkness(state) * 0.78F, 0.0F, 1.0F);
            float stormContribution = Mth.clamp(density * CloudLightingEvaluation.evaluateStormDarkness(state) * 0.85F, 0.0F, 1.0F);

            targetShadowIntensity = CloudLightingEvaluation.combineShadowContributions(targetShadowIntensity, shadowContribution);
            targetCloudDarkness = CloudLightingEvaluation.combineShadowContributions(targetCloudDarkness, cloudContribution);
            targetStormDarkness = CloudLightingEvaluation.combineShadowContributions(targetStormDarkness, stormContribution);

            if (shadowContribution > strongestContribution) {
                strongestContribution = shadowContribution;
                strongest = state;
            }
        }

        publishFrame(candidates, strongest, targetShadowIntensity, targetCloudDarkness, targetStormDarkness, partialTick);
    }

    private static void updateFromCandidates(
            @NotNull List<CloudVisualState> candidates,
            @NotNull Vec3 playerPosition,
            float partialTick
    ) {
        float mapShadow = CloudLightingEvaluation.evaluateMapBackedShadow(playerPosition);
        float targetShadowIntensity = CloudLightingEvaluation.evaluateBlendedPlayerShadowIntensity(
                candidates,
                playerPosition,
                mapShadow
        );
        float targetCloudDarkness = CloudLightingEvaluation.evaluatePlayerCloudDarknessFactor(
                candidates,
                playerPosition,
                targetShadowIntensity
        );
        float targetStormDarkness = CloudLightingEvaluation.evaluatePlayerStormDarknessFactor(
                candidates,
                playerPosition,
                targetShadowIntensity
        );
        CloudVisualState strongest = CloudLightingEvaluation.findStrongestCandidate(candidates, playerPosition);

        publishFrame(candidates, strongest, targetShadowIntensity, targetCloudDarkness, targetStormDarkness, partialTick);
    }

    private static void publishFrame(
            @NotNull List<CloudVisualState> candidates,
            @Nullable CloudVisualState strongest,
            float targetShadowIntensity,
            float targetCloudDarkness,
            float targetStormDarkness,
            float partialTick
    ) {
        LightingFrame previous = currentFrame;
        float attackBlend = smoothBlend(SMOOTH_ATTACK, partialTick);
        float releaseBlend = smoothBlend(SMOOTH_RELEASE, partialTick);
        float shadowIntensity = smoothToward(
                previous.shadowIntensity(),
                targetShadowIntensity,
                attackBlend,
                releaseBlend
        );
        float cloudDarknessFactor = smoothToward(
                previous.cloudDarknessFactor(),
                targetCloudDarkness,
                attackBlend,
                releaseBlend
        );
        float stormDarknessFactor = smoothToward(
                previous.stormDarknessFactor(),
                targetStormDarkness,
                attackBlend,
                releaseBlend
        );

        currentFrame = new LightingFrame(
                true,
                List.copyOf(candidates),
                strongest,
                shadowIntensity,
                cloudDarknessFactor,
                stormDarknessFactor
        );
    }

    public static void clear() {
        currentFrame = LightingFrame.EMPTY;
    }

    public static @NotNull List<CloudVisualState> getActiveShadowCandidates() {
        return currentFrame.candidates();
    }

    @Nullable
    public static CloudVisualState getStrongestShadowCandidate() {
        return currentFrame.strongestCandidate();
    }

    public static float getPlayerShadowIntensity() {
        return currentFrame.shadowIntensity();
    }

    public static float getPlayerCloudDarknessFactor() {
        return currentFrame.cloudDarknessFactor();
    }

    public static float getPlayerStormDarknessFactor() {
        return currentFrame.stormDarknessFactor();
    }

    public static boolean hasActiveDarkening() {
        return currentFrame.active() && currentFrame.shadowIntensity() > 0.025F;
    }

    private static float smoothToward(float current, float target, float attackBlend, float releaseBlend) {
        float blend = target > current ? attackBlend : releaseBlend;
        return Mth.lerp(blend, current, target);
    }

    private static float smoothBlend(float rate, float partialTick) {
        float clampedTick = Mth.clamp(partialTick, 0.0F, 1.0F);
        return 1.0F - (float) Math.pow(1.0F - rate, clampedTick);
    }

    private static @NotNull List<CloudVisualState> toFallbackCandidates(@NotNull List<CloudRenderSnapshot> snapshots) {
        List<CloudVisualState> states = new ArrayList<>(snapshots.size());
        for (CloudRenderSnapshot snapshot : snapshots) {
            if (snapshot == null || !CloudDensityProvider.hasVisibleDensity(snapshot)) {
                continue;
            }
            CloudVisualState state = toVisualState(snapshot);
            if (CloudLightingEvaluation.isFallbackDarkeningCandidate(state)) {
                states.add(state);
            }
        }
        states.sort(Comparator.comparing(CloudVisualState::shadowPotential).reversed());
        return List.copyOf(states);
    }

    private static @NotNull CloudVisualState toVisualState(@NotNull CloudRenderSnapshot snapshot) {
        CloudDensityProvider.DensityInputs inputs = CloudDensityProvider.deriveInputs(snapshot);
        float density = inputs.effectiveDensity();
        float coverage = inputs.effectiveCoverage();
        float opacity = Mth.clamp(density * coverage, 0.0F, 1.0F);
        float precipitation = Mth.clamp(
                snapshot.getPrecipitationTier().getRepresentativeIntensity() * 0.58F
                        + snapshot.getPrecipitationCoreStrength() * 0.42F,
                0.0F,
                1.0F
        );
        float storm = Mth.clamp(
                snapshot.getStormVisualTier().getDarkness() * 0.48F
                        + snapshot.getStormVisualTier().getShadowBias() * 0.12F
                        + precipitation * 0.16F
                        + inputs.towerness() * 0.14F
                        + snapshot.getAnvilStrength() * 0.10F,
                0.0F,
                1.0F
        );
        float visualDarkness = Mth.clamp(
                snapshot.getMaterialProfile().getDarkness() * 0.34F
                        + snapshot.getMaterialProfile().getStormCoreDarkening() * 0.22F
                        + snapshot.getBaseDarkness() * 0.18F
                        + storm * 0.16F
                        + precipitation * 0.10F,
                0.0F,
                1.0F
        );
        float shadow = Mth.clamp(
                snapshot.getShadowContribution() * 0.28F
                        + opacity * 0.32F
                        + visualDarkness * 0.18F
                        + snapshot.getStormVisualTier().getShadowBias() * 0.12F
                        + snapshot.getMaterialProfile().getShadowContribution() * 0.10F,
                0.0F,
                1.0F
        );
        float verticalDevelopment = Mth.clamp(
                Mth.clamp((snapshot.getCloudTopY() - snapshot.getCloudBaseY()) / 192.0F, 0.0F, 1.0F) * 0.38F
                        + inputs.verticalThickness() * 0.12F
                        + inputs.towerness() * 0.30F
                        + snapshot.getAnvilStrength() * 0.20F,
                0.0F,
                1.0F
        );
        float visibility = Mth.clamp(
                Mth.clamp(snapshot.getRegionRadius() / 900.0F, 0.0F, 1.0F) * 0.30F
                        + opacity * 0.24F
                        + verticalDevelopment * 0.18F
                        + storm * 0.18F
                        + shadow * 0.10F,
                0.0F,
                1.0F
        );

        return new CloudVisualState(
                snapshot.getRegionId(),
                snapshot.getClusterId(),
                snapshot.getDimension(),
                snapshot.getCloudTypeId(),
                snapshot.getMorphologyFamily(),
                snapshot.getRegionCenter(),
                snapshot.getPreviousRegionCenter(),
                snapshot.getVelocity(),
                snapshot.getRegionRadius(),
                snapshot.getCloudBaseY(),
                snapshot.getCloudTopY(),
                density,
                coverage,
                opacity,
                precipitation,
                storm,
                visualDarkness,
                shadow,
                opacity,
                verticalDevelopment,
                visibility,
                snapshot.getStormVisualTier(),
                snapshot.getPrecipitationTier(),
                snapshot.getCloudSeed()
        );
    }

    private record LightingFrame(
            boolean active,
            List<CloudVisualState> candidates,
            @Nullable CloudVisualState strongestCandidate,
            float shadowIntensity,
            float cloudDarknessFactor,
            float stormDarknessFactor
    ) {
        private static final LightingFrame EMPTY = new LightingFrame(
                false,
                List.of(),
                null,
                0.0F,
                0.0F,
                0.0F
        );
    }
}
