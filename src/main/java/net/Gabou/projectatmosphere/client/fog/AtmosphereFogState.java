package net.Gabou.projectatmosphere.client.fog;

import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.modules.fog.FogHeuristics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class AtmosphereFogState {
    private static final float HUMIDITY_TRACKING = 0.18F;
    private static final float RAIN_TRACKING = 0.22F;
    private static final float BIOME_TRACKING = 0.14F;
    private static final float DEBUG_TRACKING = 0.22F;

    private static float targetHumidityPercent;
    private static float visualHumidityPercent;
    private static float targetRainIntensity;
    private static float visualRainIntensity;
    private static float targetWetBiomeFactor;
    private static float visualWetBiomeFactor;
    private static float targetDebugStrength;
    private static float visualDebugStrength;
    private static int debugOverrideTicks;
    private static boolean hasServerSample;
    private static ResourceKey<Level> lastDimension;

    private AtmosphereFogState() {
    }

    public static void applyServerUpdate(float humidityPercent, float rainIntensity) {
        hasServerSample = true;
        targetHumidityPercent = Mth.clamp(humidityPercent, 0.0F, 100.0F);
        targetRainIntensity = Mth.clamp(rainIntensity, 0.0F, 1.0F);
    }

    public static void applyDebugOverride(float strength, int durationTicks) {
        targetDebugStrength = Mth.clamp(strength, 0.0F, 1.0F);
        debugOverrideTicks = Math.max(durationTicks, 0);
    }

    public static void clearDebugOverride() {
        debugOverrideTicks = 0;
        targetDebugStrength = 0.0F;
    }

    public static void tick(Minecraft minecraft) {
        tickDebugOverride();
        boolean fogEnabled = AtmoCommonConfig.FOG_ENABLED.get();
        boolean debugActive = hasDebugOverride();
        if (!fogEnabled && !debugActive) {
            clear();
            return;
        }

        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            clear();
            return;
        }

        ResourceKey<Level> dimension = level.dimension();
        if (lastDimension != null && !lastDimension.equals(dimension)) {
            clearVisuals();
            hasServerSample = false;
        }
        lastDimension = dimension;

        if (!fogEnabled || !Level.OVERWORLD.equals(dimension)) {
            targetHumidityPercent = 0.0F;
            targetRainIntensity = 0.0F;
            targetWetBiomeFactor = 0.0F;
            hasServerSample = false;
            trackVisuals();
            return;
        }

        BlockPos pos = minecraft.player.blockPosition();
        updateTargets(level, pos);
        trackVisuals();
    }

    public static FogHeuristics.FogProfile sample(ClientLevel level, Vec3 cameraPos, float partialTick) {
        FogHeuristics.FogProfile debugProfile = FogHeuristics.debugSample(visualDebugStrength);
        boolean fogEnabled = AtmoCommonConfig.FOG_ENABLED.get();
        if (level == null) {
            return debugProfile;
        }

        if (!fogEnabled || !Level.OVERWORLD.equals(level.dimension())) {
            return debugProfile;
        }

        BlockPos samplePos = BlockPos.containing(cameraPos);
        float localWetBiome = Math.max(visualWetBiomeFactor, FogBiomeClassifier.computeWetBiomeFactor(level, samplePos));
        float localRain = Math.max(visualRainIntensity, FogBiomeClassifier.computeClientRainIntensity(level, samplePos));
        FogHeuristics.FogProfile liveProfile = FogHeuristics.sample(visualHumidityPercent, localWetBiome, localRain);
        return FogHeuristics.max(liveProfile, debugProfile);
    }

    private static void trackVisuals() {
        visualHumidityPercent = Mth.lerp(HUMIDITY_TRACKING, visualHumidityPercent, targetHumidityPercent);
        visualRainIntensity = Mth.lerp(RAIN_TRACKING, visualRainIntensity, targetRainIntensity);
        visualWetBiomeFactor = Mth.lerp(BIOME_TRACKING, visualWetBiomeFactor, targetWetBiomeFactor);
        visualDebugStrength = Mth.lerp(DEBUG_TRACKING, visualDebugStrength, targetDebugStrength);
    }

    private static void tickDebugOverride() {
        if (debugOverrideTicks > 0) {
            debugOverrideTicks--;
            if (debugOverrideTicks == 0) {
                targetDebugStrength = 0.0F;
            }
        }
    }

    private static void updateTargets(ClientLevel level, BlockPos pos) {
        targetWetBiomeFactor = FogBiomeClassifier.computeWetBiomeFactor(level, pos);
        if (!hasServerSample) {
            targetHumidityPercent = FogBiomeClassifier.computeFallbackHumidityPercent(level, pos);
            targetRainIntensity = FogBiomeClassifier.computeClientRainIntensity(level, pos);
            return;
        }

        targetRainIntensity = Math.max(targetRainIntensity, FogBiomeClassifier.computeClientRainIntensity(level, pos) * 0.75F);
    }

    private static boolean hasDebugOverride() {
        return debugOverrideTicks > 0 || targetDebugStrength > 0.001F || visualDebugStrength > 0.001F;
    }

    private static void clearVisuals() {
        targetHumidityPercent = 0.0F;
        visualHumidityPercent = 0.0F;
        targetRainIntensity = 0.0F;
        visualRainIntensity = 0.0F;
        targetWetBiomeFactor = 0.0F;
        visualWetBiomeFactor = 0.0F;
        targetDebugStrength = 0.0F;
        visualDebugStrength = 0.0F;
        debugOverrideTicks = 0;
    }

    private static void clear() {
        clearVisuals();
        hasServerSample = false;
        lastDimension = null;
    }
}
