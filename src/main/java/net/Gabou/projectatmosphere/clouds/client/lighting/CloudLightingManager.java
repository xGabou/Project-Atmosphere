package net.Gabou.projectatmosphere.clouds.client.lighting;

import net.Gabou.projectatmosphere.clouds.visual.CloudLightingEvaluation;
import net.Gabou.projectatmosphere.clouds.visual.CloudVisualState;
import net.Gabou.projectatmosphere.clouds.visual.CloudVisualStateManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
