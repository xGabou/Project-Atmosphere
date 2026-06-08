package net.Gabou.projectatmosphere.clouds;

import net.Gabou.projectatmosphere.clouds.client.ClientCloudRegionDataCache;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionStateStore;
import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderData;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
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
        return sampleRainLevel(level, pos) > 0.0F;
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

    private static float sampleRainLevel(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return 0.0F;
        }
        if (level instanceof ServerLevel serverLevel) {
            return sampleRainLevel(CloudRegionStateStore.getActiveRegions(serverLevel), serverLevel.dimension().location().toString(), pos);
        }
        return sampleRainLevel(ClientCloudRegionDataCache.getCurrentRegions(), level.dimension().location().toString(), pos);
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

    private static float sampleRainLevel(Collection<?> regions, String dimensionId, BlockPos pos) {
        float strongest = 0.0F;
        for (Object region : regions) {
            strongest = Math.max(strongest, sampleLocalRegion(region, dimensionId, pos, false));
        }
        return strongest;
    }

    private static float sampleGlobalRegion(Object region, String dimensionId, boolean thunderOnly) {
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
                    state.getCloudTypeId(),
                    thunderOnly,
                    state.getCloudTypeId()
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
                    data.getCloudTypeId(),
                    thunderOnly,
                    data.getCloudTypeId()
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
                    state.getCloudTypeId(),
                    thunderOnly,
                    pos
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
                    data.getCloudTypeId(),
                    thunderOnly,
                    pos
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
            String cloudTypeId,
            boolean thunderOnly,
            BlockPos pos
    ) {
        if (center == null || pos == null || radius <= 0.0F || topY <= baseY) {
            return 0.0F;
        }
        if (!matchesCloudType(cloudTypeId, thunderOnly)) {
            return 0.0F;
        }
        float precipitationCoreStrength = getPrecipitationCoreStrength(cloudTypeId);
        if (precipitationCoreStrength <= 0.0F) {
            return 0.0F;
        }
        if ((float) pos.getY() + 0.5F < baseY || (float) pos.getY() + 0.5F > topY) {
            return 0.0F;
        }

        double dx = (pos.getX() + 0.5D) - center.x();
        double dz = (pos.getZ() + 0.5D) - center.z();
        float horizontalDistance = (float) Math.sqrt(dx * dx + dz * dz);
        if (horizontalDistance > radius) {
            return 0.0F;
        }

        float horizontalFade = 1.0F - Mth.clamp(horizontalDistance / radius, 0.0F, 1.0F);
        float verticalCenter = ((float) pos.getY() + 0.5F - baseY) / (topY - baseY);
        float verticalFade = 1.0F - Math.abs(Mth.clamp(verticalCenter, 0.0F, 1.0F) - 0.5F) * 2.0F;
        verticalFade = Mth.clamp(verticalFade, 0.0F, 1.0F);

        return Mth.clamp(density * coverage * precipitationCoreStrength * horizontalFade * verticalFade, 0.0F, 1.0F);
    }

    private static float sampleStrength(
            Vec3 center,
            float radius,
            float baseY,
            float topY,
            float density,
            float coverage,
            String cloudTypeId,
            boolean thunderOnly,
            String ignore
    ) {
        if (center == null || radius <= 0.0F || topY <= baseY) {
            return 0.0F;
        }
        if (!matchesCloudType(cloudTypeId, thunderOnly)) {
            return 0.0F;
        }
        float precipitationCoreStrength = getPrecipitationCoreStrength(cloudTypeId);
        if (precipitationCoreStrength <= 0.0F) {
            return 0.0F;
        }
        return Mth.clamp(density * coverage * precipitationCoreStrength, 0.0F, 1.0F);
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
}
