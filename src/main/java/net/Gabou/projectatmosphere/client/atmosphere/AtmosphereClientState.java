package net.Gabou.projectatmosphere.client.atmosphere;

import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.Gabou.projectatmosphere.client.fog.FogBiomeClassifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class AtmosphereClientState {
    private static final float HUMIDITY_TRACKING = 0.18F;
    private static final float RAIN_TRACKING = 0.22F;
    private static final float CLOUD_TRACKING = 0.16F;
    private static final float CLEARING_TRACKING = 0.20F;
    private static final float RECENT_RAIN_GAIN = 0.14F;
    private static final float RECENT_RAIN_DECAY = 0.0045F;
    private static final int SERVER_SAMPLE_STALE_TICKS = 120;

    private static float targetHumidityPercent;
    private static float visualHumidityPercent;
    private static float targetRainIntensity;
    private static float visualRainIntensity;
    private static float targetCloudCover;
    private static float visualCloudCover;
    private static float recentRainFactor;
    private static float clearingTrend;
    private static boolean hasServerSample;
    private static int serverSampleAgeTicks;
    private static ResourceKey<Level> lastDimension;

    private AtmosphereClientState() {
    }

    public static void applyServerUpdate(float humidityPercent, float rainIntensity, float cloudCover) {
        hasServerSample = true;
        serverSampleAgeTicks = 0;
        targetHumidityPercent = Mth.clamp(humidityPercent, 0.0F, 100.0F);
        targetRainIntensity = Mth.clamp(rainIntensity, 0.0F, 1.0F);
        targetCloudCover = Mth.clamp(cloudCover, 0.0F, 1.0F);
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            clear();
            return;
        }

        ResourceKey<Level> dimension = level.dimension();
        if (lastDimension != null && !lastDimension.equals(dimension)) {
            clearVisuals();
            hasServerSample = false;
            serverSampleAgeTicks = 0;
        }
        lastDimension = dimension;

        if (serverSampleAgeTicks < Integer.MAX_VALUE) {
            serverSampleAgeTicks++;
        }
        if (serverSampleAgeTicks > SERVER_SAMPLE_STALE_TICKS) {
            hasServerSample = false;
        }

        BlockPos pos = minecraft.player.blockPosition();
        float fallbackHumidity = FogBiomeClassifier.computeFallbackHumidityPercent(level, pos);
        float fallbackRain = FogBiomeClassifier.computeClientRainIntensity(level, pos);
        float fallbackCloud = estimateFallbackCloudCover(level, pos, fallbackRain);

        if (!Level.OVERWORLD.equals(dimension)) {
            targetHumidityPercent = 0.0F;
            targetRainIntensity = 0.0F;
            targetCloudCover = 0.0F;
            hasServerSample = false;
        } else if (!hasServerSample) {
            targetHumidityPercent = fallbackHumidity;
            targetRainIntensity = fallbackRain;
            targetCloudCover = fallbackCloud;
        } else {
            targetHumidityPercent = Mth.clamp(targetHumidityPercent, 0.0F, 100.0F);
            targetRainIntensity = Math.max(Mth.clamp(targetRainIntensity, 0.0F, 1.0F), fallbackRain * 0.75F);
            targetCloudCover = Math.max(Mth.clamp(targetCloudCover, 0.0F, 1.0F), fallbackCloud * 0.65F);
        }

        float previousCloudCover = visualCloudCover;
        visualHumidityPercent = Mth.lerp(HUMIDITY_TRACKING, visualHumidityPercent, targetHumidityPercent);
        visualRainIntensity = Mth.lerp(RAIN_TRACKING, visualRainIntensity, targetRainIntensity);
        visualCloudCover = Mth.lerp(CLOUD_TRACKING, visualCloudCover, targetCloudCover);

        float cloudClearingSample = Mth.clamp((previousCloudCover - visualCloudCover) * 7.5F, 0.0F, 1.0F);
        clearingTrend = Mth.lerp(CLEARING_TRACKING, clearingTrend, cloudClearingSample);

        if (visualRainIntensity > 0.05F) {
            recentRainFactor = Mth.clamp(
                    Math.max(recentRainFactor, visualRainIntensity) + (visualRainIntensity * RECENT_RAIN_GAIN),
                    0.0F,
                    1.0F
            );
        } else {
            recentRainFactor = Math.max(0.0F, recentRainFactor - RECENT_RAIN_DECAY);
        }
    }

    public static Snapshot getSnapshot() {
        return new Snapshot(
                visualHumidityPercent,
                visualRainIntensity,
                visualCloudCover,
                recentRainFactor,
                clearingTrend
        );
    }

    public static float getRainIntensity() {
        return visualRainIntensity;
    }

    public static float getCloudCover() {
        return visualCloudCover;
    }

    public static float getHumidityPercent() {
        return visualHumidityPercent;
    }

    private static float estimateFallbackCloudCover(ClientLevel level, BlockPos pos, float fallbackRain) {
        try {
            return CloudManager.get(level).getCloudGenerator().getCloudAtWorldPosition(pos.getX() + 0.5F, pos.getZ() + 0.5F) != null
                    ? Math.max(0.65F, fallbackRain)
                    : (fallbackRain > 0.0F ? 0.72F : 0.0F);
        } catch (Exception ignored) {
            return fallbackRain > 0.0F ? 0.72F : 0.0F;
        }
    }

    private static void clearVisuals() {
        targetHumidityPercent = 0.0F;
        visualHumidityPercent = 0.0F;
        targetRainIntensity = 0.0F;
        visualRainIntensity = 0.0F;
        targetCloudCover = 0.0F;
        visualCloudCover = 0.0F;
        recentRainFactor = 0.0F;
        clearingTrend = 0.0F;
    }

    private static void clear() {
        clearVisuals();
        hasServerSample = false;
        serverSampleAgeTicks = 0;
        lastDimension = null;
    }

    public record Snapshot(
            float humidityPercent,
            float rainIntensity,
            float cloudCover,
            float recentRainFactor,
            float clearingTrend
    ) {
        public static final Snapshot NONE = new Snapshot(0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    }
}
