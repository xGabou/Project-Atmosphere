package net.Gabou.projectatmosphere.util;

import javax.annotation.Nullable;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

public final class HumidityGuard {
    private static final float MIN_PERCENT = 0f;
    private static final float MAX_PERCENT = 100f;
    private static final float MIN_NORMALIZED = 0f;
    private static final float MAX_NORMALIZED = 1f;

    private HumidityGuard() {
    }

    public static float clampPercent(float value,
                                     float fallback,
                                     String context,
                                     @Nullable RegionInstanceKey regionKey,
                                     @Nullable ResourceLocation biomeKey,
                                     @Nullable ResourceKey<Level> dimension,
                                     @Nullable BlockPos pos) {
        float safeFallback = Float.isFinite(fallback) ? fallback : 0f;
        boolean nonFinite = !Float.isFinite(value);
        float safe = nonFinite ? safeFallback : value;
        float clamped = Mth.clamp(safe, MIN_PERCENT, MAX_PERCENT);
        if (nonFinite || clamped != safe) {
            String reason = nonFinite ? "non-finite" : "out-of-range";
            logClamp(context, value, clamped, reason, regionKey, biomeKey, dimension, pos, null, null);
        }
        return clamped;
    }

    public static float clampNormalized(float value,
                                        float fallback,
                                        String context,
                                        @Nullable RegionInstanceKey regionKey,
                                        @Nullable ResourceLocation biomeKey,
                                        @Nullable ResourceKey<Level> dimension,
                                        @Nullable BlockPos pos) {
        float safeFallback = Float.isFinite(fallback) ? fallback : 0f;
        boolean nonFinite = !Float.isFinite(value);
        float safe = nonFinite ? safeFallback : value;
        float clamped = Mth.clamp(safe, MIN_NORMALIZED, MAX_NORMALIZED);
        if (nonFinite || clamped != safe) {
            String reason = nonFinite ? "non-finite" : "out-of-range";
            Float originalPercent = Float.isFinite(value) ? value * 100f : null;
            Float clampedPercent = clamped * 100f;
            logClamp(context, value, clamped, reason, regionKey, biomeKey, dimension, pos, originalPercent, clampedPercent);
        }
        return clamped;
    }

    public static float[][] clampWeekPercent(float[][] week,
                                             float fallback,
                                             String context,
                                             @Nullable RegionInstanceKey regionKey,
                                             @Nullable ResourceLocation biomeKey,
                                             @Nullable ResourceKey<Level> dimension,
                                             @Nullable BlockPos pos) {
        if (week == null) {
            float fallbackPercent = clampPercent(fallback, fallback, context, regionKey, biomeKey, dimension, pos);
            float[][] out = new float[7][2];
            for (int d = 0; d < out.length; d++) {
                out[d][0] = fallbackPercent;
                out[d][1] = fallbackPercent;
            }
            return out;
        }
        for (int d = 0; d < week.length; d++) {
            if (week[d] == null) {
                continue;
            }
            for (int c = 0; c < week[d].length; c++) {
                week[d][c] = clampPercent(week[d][c], fallback, context, regionKey, biomeKey, dimension, pos);
            }
        }
        return week;
    }

    private static void logClamp(String context,
                                 float original,
                                 float clamped,
                                 String reason,
                                 @Nullable RegionInstanceKey regionKey,
                                 @Nullable ResourceLocation biomeKey,
                                 @Nullable ResourceKey<Level> dimension,
                                 @Nullable BlockPos pos,
                                 @Nullable Float originalPercent,
                                 @Nullable Float clampedPercent) {
        String dimensionId = dimension == null ? "null" : dimension.location().toString();
        if (originalPercent != null && clampedPercent != null) {
            ProjectAtmosphere.LOGGER.error(
                    "[Atmosphere] Humidity clamp ({}) context={} originalNorm={} clampedNorm={} originalPercent={} clampedPercent={} region={} biome={} dimension={} pos={} thread={}",
                    reason,
                    context,
                    original,
                    clamped,
                    originalPercent,
                    clampedPercent,
                    regionKey,
                    biomeKey,
                    dimensionId,
                    pos,
                    Thread.currentThread().getName(),
                    new RuntimeException("Humidity clamp trace")
            );
            return;
        }
        ProjectAtmosphere.LOGGER.error(
                "[Atmosphere] Humidity clamp ({}) context={} original={} clamped={} region={} biome={} dimension={} pos={} thread={}",
                reason,
                context,
                original,
                clamped,
                regionKey,
                biomeKey,
                dimensionId,
                pos,
                Thread.currentThread().getName(),
                new RuntimeException("Humidity clamp trace")
        );
    }
}
