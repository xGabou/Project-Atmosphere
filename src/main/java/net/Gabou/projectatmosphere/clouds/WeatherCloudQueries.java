package net.Gabou.projectatmosphere.clouds;

import net.Gabou.projectatmosphere.clouds.client.ClientCloudRegionDataCache;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionStateStore;
import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderData;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeDefinition;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.Gabou.projectatmosphere.clouds.type.CloudVisualProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;

/**
 * Shared precipitation queries for vanilla weather overrides.
 */
public final class WeatherCloudQueries {

    private WeatherCloudQueries() {
    }

    public static boolean isRainingAt(Level level, BlockPos pos) {
        return sampleRainLevel(level, pos, false) > 0.0F;
    }

    public static boolean isThunderingAt(Level level, BlockPos pos) {
        return sampleRainLevel(level, pos, true) > 0.0F;
    }

    public static boolean isRaining(Level level) {
        return sampleGlobalRainLevel(level) > 0.0F;
    }

    public static boolean isThundering(Level level) {
        return sampleGlobalThunderLevel(level) > 0.0F;
    }

    public static float getRainLevel(Level level, float partialTick) {
        return sampleGlobalRainLevel(level);
    }

    public static float getThunderLevel(Level level, float partialTick) {
        return sampleGlobalThunderLevel(level);
    }

    private static float sampleGlobalRainLevel(Level level) {
        if (level == null) {
            return 0.0F;
        }
        if (level instanceof ServerLevel serverLevel) {
            return sampleGlobalRainLevel(CloudRegionStateStore.getActiveRegions(serverLevel), serverLevel.dimension().location().toString());
        }
        return sampleGlobalRainLevel(ClientCloudRegionDataCache.getCurrentRegions(), level.dimension().location().toString());
    }

    private static float sampleGlobalThunderLevel(Level level) {
        if (level == null) {
            return 0.0F;
        }
        if (level instanceof ServerLevel serverLevel) {
            return sampleGlobalThunderLevel(CloudRegionStateStore.getActiveRegions(serverLevel), serverLevel.dimension().location().toString());
        }
        return sampleGlobalThunderLevel(ClientCloudRegionDataCache.getCurrentRegions(), level.dimension().location().toString());
    }

    private static float sampleRainLevel(Level level, BlockPos pos, boolean thunderOnly) {
        if (level == null || pos == null) {
            return 0.0F;
        }
        if (level instanceof ServerLevel serverLevel) {
            return sampleRainLevel(CloudRegionStateStore.getActiveRegions(serverLevel), serverLevel.dimension().location().toString(), pos, thunderOnly);
        }
        return sampleRainLevel(ClientCloudRegionDataCache.getCurrentRegions(), level.dimension().location().toString(), pos, thunderOnly);
    }

    private static float sampleGlobalRainLevel(Collection<?> regions, String dimensionId) {
        float strongest = 0.0F;
        for (Object region : regions) {
            strongest = Math.max(strongest, sampleGlobalRegion(region, dimensionId, false));
        }
        return strongest;
    }

    private static float sampleGlobalThunderLevel(Collection<?> regions, String dimensionId) {
        float strongest = 0.0F;
        for (Object region : regions) {
            strongest = Math.max(strongest, sampleGlobalRegion(region, dimensionId, true));
        }
        return strongest;
    }

    private static float sampleRainLevel(Collection<?> regions, String dimensionId, BlockPos pos, boolean thunderOnly) {
        float strongest = 0.0F;
        for (Object region : regions) {
            strongest = Math.max(strongest, sampleLocalRegion(region, dimensionId, pos, thunderOnly));
        }
        return strongest;
    }

    private static float sampleGlobalRegion(Object region, String dimensionId, boolean thunderOnly) {
        if (region instanceof CloudRegionState state) {
            if (!state.isActive() || !state.getDimension().location().toString().equals(dimensionId)) {
                return 0.0F;
            }
            return samplePeakStrength(
                    state.getDensity(),
                    state.getCoverage(),
                    state.getGrowth(),
                    state.getDecay(),
                    state.getCloudTypeId(),
                    thunderOnly,
                    getDensityMultiplier(state.getCloudTypeId()),
                    getCoverageMultiplier(state.getCloudTypeId()),
                    getPrecipitationCoreStrength(state.getCloudTypeId())
            );
        }
        if (region instanceof CloudRegionRenderData data) {
            if (!data.isActive() || !data.getDimensionId().equals(dimensionId)) {
                return 0.0F;
            }
            return samplePeakStrength(
                    data.getDensity(),
                    data.getCoverage(),
                    data.getGrowth(),
                    data.getDecay(),
                    data.getCloudTypeId(),
                    thunderOnly,
                    data.getDensityMultiplier(),
                    data.getCoverageMultiplier(),
                    data.getPrecipitationCoreStrength()
            );
        }
        return 0.0F;
    }

    private static float sampleLocalRegion(Object region, String dimensionId, BlockPos pos, boolean thunderOnly) {
        if (region instanceof CloudRegionState state) {
            if (!state.isActive() || !state.getDimension().location().toString().equals(dimensionId)) {
                return 0.0F;
            }
            return sampleStrength(
                    state.getCenter(),
                    state.getRadius(),
                    state.getBaseY(),
                    state.getTopY(),
                    state.getDensity(),
                    state.getCoverage(),
                    state.getEdgeSoftness(),
                    state.getCloudTypeId(),
                    thunderOnly,
                    pos,
                    getDensityMultiplier(state.getCloudTypeId()),
                    getCoverageMultiplier(state.getCloudTypeId()),
                    getPrecipitationCoreStrength(state.getCloudTypeId()),
                    state.getGrowth(),
                    state.getDecay()
            );
        }
        if (region instanceof CloudRegionRenderData data) {
            if (!data.isActive() || !data.getDimensionId().equals(dimensionId)) {
                return 0.0F;
            }
            return sampleStrength(
                    data.getCenter(),
                    data.getRadius(),
                    data.getBaseY(),
                    data.getTopY(),
                    data.getDensity(),
                    data.getCoverage(),
                    data.getEdgeSoftness(),
                    data.getCloudTypeId(),
                    thunderOnly,
                    pos,
                    data.getDensityMultiplier(),
                    data.getCoverageMultiplier(),
                    data.getPrecipitationCoreStrength(),
                    data.getGrowth(),
                    data.getDecay()
            );
        }
        return 0.0F;
    }

    private static float sampleStrength(
            Vec3 center,
            float radius,
            float baseY,
            float topY,
            float density,
            float coverage,
            float edgeSoftness,
            String cloudTypeId,
            boolean thunderOnly,
            BlockPos pos,
            float densityMultiplier,
            float coverageMultiplier,
            float precipitationCoreStrength,
            float growth,
            float decay
    ) {
        if (center == null || pos == null || radius <= 0.0F || topY <= baseY) {
            return 0.0F;
        }
        if (!matchesCloudType(cloudTypeId, thunderOnly)) {
            return 0.0F;
        }
        if (precipitationCoreStrength <= 0.0F) {
            return 0.0F;
        }
        float lifecycleFactor = getLifecycleFactor(growth, decay);
        float effectiveDensity = Mth.clamp(density * densityMultiplier * lifecycleFactor, 0.0F, 1.0F);
        float effectiveCoverage = Mth.clamp(coverage * coverageMultiplier * lifecycleFactor, 0.0F, 1.0F);
        if (effectiveDensity <= 0.0F || effectiveCoverage <= 0.0F) {
            return 0.0F;
        }
        if ((float) pos.getY() + 0.5F < baseY || (float) pos.getY() + 0.5F > topY) {
            return 0.0F;
        }

        double dx = (pos.getX() + 0.5D) - center.x();
        double dz = (pos.getZ() + 0.5D) - center.z();
        float horizontalDistance = (float) Math.sqrt(dx * dx + dz * dz);
        float normalizedHorizontal = horizontalDistance / radius;
        if (normalizedHorizontal >= 1.0F) {
            return 0.0F;
        }

        float horizontalFade = edgeSoftness <= 0.0F
                ? 1.0F - smoothstep(0.82F, 1.0F, normalizedHorizontal)
                : 1.0F - smoothstep(1.0F - Mth.clamp(edgeSoftness, 0.0F, 1.0F), 1.0F, normalizedHorizontal);

        float normalizedVertical = ((float) pos.getY() + 0.5F - baseY) / (topY - baseY);
        float verticalFade = smoothstep(0.0F, 0.15F, normalizedVertical)
                * (1.0F - smoothstep(0.85F, 1.0F, normalizedVertical));

        return Mth.clamp(effectiveDensity * effectiveCoverage * precipitationCoreStrength * horizontalFade * verticalFade, 0.0F, 1.0F);
    }

    private static float samplePeakStrength(
            float density,
            float coverage,
            float growth,
            float decay,
            String cloudTypeId,
            boolean thunderOnly,
            float densityMultiplier,
            float coverageMultiplier,
            float precipitationCoreStrength
    ) {
        if (!matchesCloudType(cloudTypeId, thunderOnly)) {
            return 0.0F;
        }
        if (precipitationCoreStrength <= 0.0F) {
            return 0.0F;
        }
        float lifecycleFactor = getLifecycleFactor(growth, decay);
        return Mth.clamp(density * coverage * densityMultiplier * coverageMultiplier * lifecycleFactor * precipitationCoreStrength, 0.0F, 1.0F);
    }

    private static boolean matchesCloudType(String cloudTypeId, boolean thunderOnly) {
        if (thunderOnly) {
            return CloudTypeRegistry.isThunderCloud(cloudTypeId);
        }
        return CloudTypeRegistry.isPrecipitatingCloud(cloudTypeId);
    }

    private static float getPrecipitationCoreStrength(String cloudTypeId) {
        return CloudTypeRegistry.getOrDefault(cloudTypeId).getVisualProfile().getPrecipitationCoreStrength();
    }

    private static float getDensityMultiplier(String cloudTypeId) {
        CloudTypeDefinition definition = CloudTypeRegistry.getOrDefault(cloudTypeId);
        CloudVisualProfile profile = definition.getVisualProfile();
        return profile.getDensityMultiplier();
    }

    private static float getCoverageMultiplier(String cloudTypeId) {
        CloudTypeDefinition definition = CloudTypeRegistry.getOrDefault(cloudTypeId);
        CloudVisualProfile profile = definition.getVisualProfile();
        return profile.getCoverageMultiplier();
    }

    private static float getLifecycleFactor(float growth, float decay) {
        return Mth.clamp(growth * (1.0F - decay), 0.0F, 1.0F);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        if (edge0 == edge1) {
            return value < edge0 ? 0.0F : 1.0F;
        }

        float t = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
