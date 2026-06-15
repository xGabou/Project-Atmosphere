package net.Gabou.projectatmosphere.clouds;

import net.Gabou.projectatmosphere.clouds.network.CloudRegionPacketDispatcher;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

/**
 * Shared precipitation queries for PA cloud-local weather.
 */
public final class WeatherCloudQueries {
    private static final float MIN_STRENGTH = 0.001F;

    private WeatherCloudQueries() {
    }

    public static boolean isRainingAt(Level level, BlockPos pos) {
        return sampleAt(level, pos, true).hasRain();
    }

    public static boolean isThunderingAt(Level level, BlockPos pos) {
        return sampleAt(level, pos, true).hasThunder();
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

    public static float getLocalRainLevel(Level level, BlockPos pos) {
        return sampleAt(level, pos, true).rainStrength();
    }

    public static float getLocalThunderLevel(Level level, BlockPos pos) {
        return sampleAt(level, pos, true).thunderStrength();
    }

    public static @NotNull CloudWeatherSample sampleAt(Level level, BlockPos pos, boolean requireSky) {
        if (level == null || pos == null) {
            return CloudWeatherSample.NONE;
        }

        boolean canPrecipitateAtPosition = !requireSky || level.canSeeSky(pos);
        if (!canPrecipitateAtPosition) {
            return CloudWeatherSample.NONE;
        }

        Collection<?> regions = getRegions(level);
        String dimensionId = level.dimension().location().toString();
        boolean snowing = level.getBiome(pos).value().coldEnoughToSnow(pos);
        CloudWeatherSample strongest = CloudWeatherSample.NONE;
        for (Object region : regions) {
            CloudWeatherSample sample = sampleLocalRegion(region, dimensionId, pos, snowing);
            if (sample.rainStrength() > strongest.rainStrength()) {
                strongest = sample;
            }
        }
        return strongest;
    }

    private static Collection<?> getRegions(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return CloudRegionStateStore.createRenderDataForActiveRegions(serverLevel);
        }
        return CloudRegionPacketDispatcher.getClientRegions();
    }

    public static @Nullable CloudRegionRenderData getCloudRegionAt(Level level, BlockPos pos) {
        String dimensionId = level.dimension().location().toString();
        Collection<CloudRegionRenderData> regions =
                level instanceof ServerLevel server
                        ? CloudRegionStateStore.createRenderDataForActiveRegions(server)
                        : CloudRegionPacketDispatcher.getClientRegions();

        CloudRegionRenderData best = null;
        float bestScore = -1.0F;

        double px = pos.getX() + 0.5D;
        double py = pos.getY() + 0.5D;
        double pz = pos.getZ() + 0.5D;

        for (CloudRegionRenderData region : regions) {
            if (!region.isActive() || !dimensionId.equals(region.getDimensionId())) {
                continue;
            }

            double dx = px - region.getCenter().x();
            double dz = pz - region.getCenter().z();
            if (dx * dx + dz * dz > (double) region.getRadius() * region.getRadius()) {
                continue;
            }

            if (py < region.getBaseY() || py > region.getTopY()) {
                continue;
            }

            float score = region.getDensity() * region.getCoverage();
            if (score > bestScore) {
                bestScore = score;
                best = region;
            }
        }

        return best;
    }


    private static float sampleGlobalRainLevel(Level level) {
        if (level == null) {
            return 0.0F;
        }

        float strongest = 0.0F;
        String dimensionId = level.dimension().location().toString();
        for (Object region : getRegions(level)) {
            strongest = Math.max(strongest, sampleGlobalRegion(region, dimensionId, false));
        }
        return strongest;
    }

    private static float sampleGlobalThunderLevel(Level level) {
        if (level == null) {
            return 0.0F;
        }

        float strongest = 0.0F;
        String dimensionId = level.dimension().location().toString();
        for (Object region : getRegions(level)) {
            strongest = Math.max(strongest, sampleGlobalRegion(region, dimensionId, true));
        }
        return strongest;
    }

    private static float sampleGlobalRegion(Object region, String dimensionId, boolean thunderOnly) {
        RegionWeatherInputs inputs = readInputs(region, dimensionId);
        if (inputs == null) {
            return 0.0F;
        }
        return samplePeakStrength(inputs, thunderOnly);
    }

    private static @NotNull CloudWeatherSample sampleLocalRegion(Object region, String dimensionId, BlockPos pos, boolean snowing) {
        RegionWeatherInputs inputs = readInputs(region, dimensionId);
        if (inputs == null || pos == null) {
            return CloudWeatherSample.NONE;
        }
        if (!CloudTypeRegistry.isPrecipitatingCloud(inputs.cloudTypeId())) {
            return CloudWeatherSample.NONE;
        }
        if (inputs.precipitationCoreStrength() <= 0.0F || inputs.center() == null || inputs.radius() <= 0.0F) {
            return CloudWeatherSample.NONE;
        }
        if ((float) pos.getY() + 0.5F > inputs.topY()) {
            return CloudWeatherSample.NONE;
        }

        float lifecycleFactor = getLifecycleFactor(inputs.growth(), inputs.decay());
        float effectiveDensity = Mth.clamp(inputs.density() * inputs.densityMultiplier() * lifecycleFactor, 0.0F, 1.0F);
        float effectiveCoverage = Mth.clamp(inputs.coverage() * inputs.coverageMultiplier() * lifecycleFactor, 0.0F, 1.0F);
        if (effectiveDensity <= 0.0F || effectiveCoverage <= 0.0F) {
            return CloudWeatherSample.NONE;
        }

        double dx = (pos.getX() + 0.5D) - inputs.center().x();
        double dz = (pos.getZ() + 0.5D) - inputs.center().z();
        float horizontalDistance = (float) Math.sqrt(dx * dx + dz * dz);
        float normalizedHorizontal = horizontalDistance / inputs.radius();
        if (normalizedHorizontal >= 1.0F) {
            return CloudWeatherSample.NONE;
        }

        float horizontalFade = horizontalFade(normalizedHorizontal, inputs.edgeSoftness());
        float y = (float) pos.getY() + 0.5F;
        float verticalFade = y <= inputs.baseY()
                ? 1.0F
                : 1.0F - smoothstep(inputs.baseY(), inputs.topY(), y);

        float cloudCoverStrength = Mth.clamp(effectiveCoverage * horizontalFade, 0.0F, 1.0F);
        float rainStrength = Mth.clamp(
                effectiveDensity * effectiveCoverage * inputs.precipitationCoreStrength() * horizontalFade * verticalFade,
                0.0F,
                1.0F
        );
        if (rainStrength <= MIN_STRENGTH) {
            return CloudWeatherSample.NONE;
        }

        float thunderStrength = CloudTypeRegistry.isThunderCloud(inputs.cloudTypeId()) ? rainStrength : 0.0F;
        return new CloudWeatherSample(
                rainStrength,
                thunderStrength,
                cloudCoverStrength,
                true,
                true,
                snowing,
                inputs.cloudTypeId(),
                inputs.regionId()
        );
    }

    private static float samplePeakStrength(@NotNull RegionWeatherInputs inputs, boolean thunderOnly) {
        if (!matchesCloudType(inputs.cloudTypeId(), thunderOnly)) {
            return 0.0F;
        }
        if (inputs.precipitationCoreStrength() <= 0.0F) {
            return 0.0F;
        }
        float lifecycleFactor = getLifecycleFactor(inputs.growth(), inputs.decay());
        return Mth.clamp(
                inputs.density()
                        * inputs.coverage()
                        * inputs.densityMultiplier()
                        * inputs.coverageMultiplier()
                        * lifecycleFactor
                        * inputs.precipitationCoreStrength(),
                0.0F,
                1.0F
        );
    }

    private static @Nullable RegionWeatherInputs readInputs(Object region, String dimensionId) {
        if (region instanceof CloudRegionRenderData data) {
            if (!data.isActive() || !data.getDimensionId().equals(dimensionId)) {
                return null;
            }
            String cloudTypeId = CloudTypeRegistry.getOrDefault(data.getCloudTypeId()).getId();
            return new RegionWeatherInputs(
                    data.getRegionId(),
                    data.getCenter(),
                    data.getRadius(),
                    data.getBaseY(),
                    data.getTopY(),
                    data.getDensity(),
                    data.getCoverage(),
                    data.getEdgeSoftness(),
                    cloudTypeId,
                    data.getDensityMultiplier(),
                    data.getCoverageMultiplier(),
                    data.getPrecipitationCoreStrength(),
                    data.getGrowth(),
                    data.getDecay()
            );
        }
        return null;
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

    private static float horizontalFade(float normalizedHorizontal, float edgeSoftness) {
        return edgeSoftness <= 0.0F
                ? 1.0F - smoothstep(0.82F, 1.0F, normalizedHorizontal)
                : 1.0F - smoothstep(1.0F - Mth.clamp(edgeSoftness, 0.0F, 1.0F), 1.0F, normalizedHorizontal);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        if (edge0 == edge1) {
            return value < edge0 ? 0.0F : 1.0F;
        }

        float t = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private record RegionWeatherInputs(
            UUID regionId,
            Vec3 center,
            float radius,
            float baseY,
            float topY,
            float density,
            float coverage,
            float edgeSoftness,
            String cloudTypeId,
            float densityMultiplier,
            float coverageMultiplier,
            float precipitationCoreStrength,
            float growth,
            float decay
    ) {
    }
}
