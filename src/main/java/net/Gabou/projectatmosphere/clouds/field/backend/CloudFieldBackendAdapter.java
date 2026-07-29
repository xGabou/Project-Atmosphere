package net.Gabou.projectatmosphere.clouds.field.backend;

import net.Gabou.projectatmosphere.clouds.backend.CloudBackendBridgeSnapshot;
import net.Gabou.projectatmosphere.clouds.field.CloudMorphologyMembership;
import net.Gabou.projectatmosphere.clouds.state.CloudClusterState;
import net.Gabou.projectatmosphere.clouds.state.CloudRegionState;
import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderData;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Converts existing PA backend/transport state into neutral CloudField sources.
 * This class does not mutate managers, packets, renderers, or shaders.
 */
public final class CloudFieldBackendAdapter {
    public CloudFieldSourceSnapshot fromRegions(Collection<CloudRegionState> regions, long gameTime) {
        List<CloudFieldSource> sources = new ArrayList<>();
        String dimensionId = "";
        if (regions != null) {
            for (CloudRegionState region : regions) {
                if (region != null) {
                    if (dimensionId.isBlank()) {
                        dimensionId = region.getDimension().location().toString();
                    }
                    sources.add(fromRegion(region));
                }
            }
        }
        return CloudFieldSourceSnapshot.of(sources, gameTime, dimensionId, "pa-region-state");
    }

    public CloudFieldSource fromRegion(CloudRegionState region) {
        if (region == null) {
            throw new IllegalArgumentException("region cannot be null");
        }
        String cloudTypeId = region.getCloudTypeId();
        String morphologyFamily = region.getMorphologyFamily().name();
        return new CloudFieldSource(
                region.getRegionId().toString(),
                CloudFieldSourceType.PA_REGION,
                region.getDimension().location().toString(),
                region.getCenter(),
                region.getRadius(),
                region.getBaseY(),
                region.getTopY(),
                region.getDensity(),
                region.getCoverage(),
                humidityFrom(region.getDensity(), region.getCoverage()),
                region.getVelocity(),
                region.getGrowth(),
                region.getDecay(),
                verticalDevelopment(region.getRadius(), region.getBaseY(), region.getTopY(), cloudTypeId, morphologyFamily),
                stormPotential(cloudTypeId, morphologyFamily),
                derivedPrecipitation(
                        cloudTypeId,
                        region.getDensity(),
                        region.getCoverage(),
                        stormPotential(cloudTypeId, morphologyFamily)
                ),
                seedFrom(region.getCloudSeed(), region.getRegionId()),
                region.getAgeTicks(),
                region.getLifetimeTicks(),
                0,
                cloudTypeId,
                morphologyFamily,
                region.isActive()
        );
    }

    public CloudFieldSourceSnapshot fromRegionClusters(CloudRegionState region, long gameTime) {
        if (region == null) {
            return CloudFieldSourceSnapshot.of(List.of(), gameTime, "", "pa-region-clusters");
        }

        List<CloudFieldSource> sources = new ArrayList<>();
        for (CloudClusterState cluster : region.getClusters()) {
            if (cluster != null) {
                sources.add(fromCluster(region, cluster));
            }
        }
        return CloudFieldSourceSnapshot.of(
                sources,
                gameTime,
                region.getDimension().location().toString(),
                "pa-region-clusters"
        );
    }

    public CloudFieldSource fromCluster(CloudRegionState region, CloudClusterState cluster) {
        if (region == null || cluster == null) {
            throw new IllegalArgumentException("region and cluster are required");
        }
        String cloudTypeId = cluster.getCloudTypeId();
        String morphologyFamily = cluster.getMorphologyFamily().name();
        return new CloudFieldSource(
                cluster.getClusterId().toString(),
                CloudFieldSourceType.PA_CLUSTER,
                cluster.getDimension().location().toString(),
                cluster.getCenter(),
                cluster.getRadius(),
                cluster.getBaseY(),
                cluster.getTopY(),
                cluster.getDensity(),
                cluster.getCoverage(),
                humidityFrom(cluster.getDensity(), cluster.getCoverage()),
                cluster.getVelocity(),
                cluster.getGrowth(),
                cluster.getDecay(),
                verticalDevelopment(cluster.getRadius(), cluster.getBaseY(), cluster.getTopY(), cloudTypeId, morphologyFamily),
                stormPotential(cloudTypeId, morphologyFamily),
                derivedPrecipitation(
                        cloudTypeId,
                        cluster.getDensity(),
                        cluster.getCoverage(),
                        stormPotential(cloudTypeId, morphologyFamily)
                ),
                seedFrom(cluster.getCloudSeed(), cluster.getClusterId()),
                cluster.getAgeTicks(),
                cluster.getLifetimeTicks(),
                0,
                cloudTypeId,
                morphologyFamily,
                new CloudMorphologyMembership(
                        cluster.getMorphologyGroupId(),
                        cluster.getMorphologyIndex(),
                        cluster.getMorphologyCount(),
                        cluster.getMorphologyLayoutVersion(),
                        cluster.getMorphologyMemberTier()
                ),
                cluster.isActive()
        );
    }

    public CloudFieldSourceSnapshot fromRenderData(Collection<CloudRegionRenderData> regions, long gameTime) {
        List<CloudFieldSource> sources = new ArrayList<>();
        String dimensionId = "";
        if (regions != null) {
            for (CloudRegionRenderData region : regions) {
                if (region != null) {
                    if (dimensionId.isBlank()) {
                        dimensionId = region.getDimensionId();
                    }
                    sources.add(fromRenderData(region));
                }
            }
        }
        return CloudFieldSourceSnapshot.of(sources, gameTime, dimensionId, "cloud-region-render-data");
    }

    public CloudFieldSource fromRenderData(CloudRegionRenderData data) {
        if (data == null) {
            throw new IllegalArgumentException("render data cannot be null");
        }
        String cloudTypeId = data.getCloudTypeId();
        String morphologyFamily = data.getMorphologyFamily().name();
        float density = clamp01(data.getDensity() * data.getDensityMultiplier());
        float coverage = clamp01(data.getCoverage() * data.getCoverageMultiplier());
        return new CloudFieldSource(
                data.getRegionId() + "/" + data.getClusterId(),
                CloudFieldSourceType.PA_RENDER_DATA,
                data.getDimensionId(),
                data.getCenter(),
                data.getRadius(),
                data.getBaseY(),
                data.getTopY(),
                density,
                coverage,
                humidityFrom(density, coverage),
                data.getVelocity(),
                data.getGrowth(),
                data.getDecay(),
                Math.max(
                        data.getTowerStrength(),
                        verticalDevelopment(data.getRadius(), data.getBaseY(), data.getTopY(), cloudTypeId, morphologyFamily)
                ),
                Math.max(data.getLightningInfluence(), stormPotential(cloudTypeId, morphologyFamily)),
                precipitationFrom(data),
                seedFrom(data.getCloudSeed(), data.getClusterId()),
                data.getAgeTicks(),
                data.getLifetimeTicks(),
                0,
                cloudTypeId,
                morphologyFamily,
                data.isActive()
        );
    }

    public CloudFieldSource fromBackendBridgeSnapshot(
            CloudBackendBridgeSnapshot snapshot,
            String dimensionId
    ) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot cannot be null");
        }
        String sourceId = snapshot.sourceTrackingKey() == null || snapshot.sourceTrackingKey().isBlank()
                ? snapshot.sourceBackend() + ":" + snapshot.sourceTypeId()
                : snapshot.sourceTrackingKey();
        float height = (float) Math.max(1.0D, snapshot.height());
        float centerY = (float) snapshot.y();
        float baseY = centerY - height * 0.5F;
        float topY = centerY + height * 0.5F;
        String morphologyFamily = snapshot.sourceMorphologyFamily();
        String cloudTypeId = snapshot.sourceTypeId();
        return new CloudFieldSource(
                sourceId,
                CloudFieldSourceType.BACKEND_BRIDGE_SNAPSHOT,
                dimensionId,
                new Vec3(snapshot.x(), snapshot.y(), snapshot.z()),
                (float) snapshot.radius(),
                baseY,
                topY,
                (float) snapshot.density(),
                (float) snapshot.coverage(),
                humidityFrom((float) snapshot.density(), (float) snapshot.coverage()),
                Vec3.ZERO,
                1.0F,
                0.0F,
                verticalDevelopment((float) snapshot.radius(), baseY, topY, cloudTypeId, morphologyFamily),
                (float) snapshot.stormStrength(),
                derivedPrecipitation(
                        cloudTypeId,
                        (float) snapshot.density(),
                        (float) snapshot.coverage(),
                        (float) snapshot.stormStrength()
                ),
                seedFrom(0, sourceId),
                0L,
                0L,
                0,
                cloudTypeId,
                morphologyFamily,
                true
        );
    }

    public CloudFieldSource manual(
            String sourceId,
            String dimensionId,
            Vec3 center,
            float radius,
            float baseY,
            float topY,
            float density,
            float coverage,
            Vec3 wind,
            long seed
    ) {
        return new CloudFieldSource(
                sourceId,
                CloudFieldSourceType.MANUAL_DEBUG,
                dimensionId,
                center,
                radius,
                baseY,
                topY,
                density,
                coverage,
                humidityFrom(density, coverage),
                wind,
                1.0F,
                0.0F,
                verticalDevelopment(radius, baseY, topY, null, null),
                0.0F,
                0.0F,
                seed,
                0L,
                0L,
                0,
                null,
                null,
                true
        );
    }

    private static float humidityFrom(float density, float coverage) {
        return clamp01((density * 0.55F) + (coverage * 0.45F));
    }

    private static float verticalDevelopment(float radius, float baseY, float topY, String cloudTypeId, String morphologyFamily) {
        float geometric = clamp01((topY - baseY) / Math.max(1.0F, radius));
        String family = morphologyFamily == null ? "" : morphologyFamily.toLowerCase(Locale.ROOT);
        if (CloudTypeRegistry.isThunderCloud(cloudTypeId) || family.contains("storm")) {
            return Math.max(geometric, 0.90F);
        }
        if (family.contains("tower")) {
            return Math.max(geometric, 0.70F);
        }
        return geometric;
    }

    private static float stormPotential(String cloudTypeId, String morphologyFamily) {
        String family = morphologyFamily == null ? "" : morphologyFamily.toLowerCase(Locale.ROOT);
        if (CloudTypeRegistry.isThunderCloud(cloudTypeId) || family.contains("storm")) {
            return 0.90F;
        }
        String type = cloudTypeId == null ? "" : cloudTypeId.toLowerCase(Locale.ROOT);
        if (type.contains("nimbus") || type.contains("congestus") || family.contains("tower")) {
            return 0.45F;
        }
        return 0.0F;
    }

    private static float precipitationFrom(CloudRegionRenderData data) {
        float tierIntensity = data.getPrecipitationTier().getRepresentativeIntensity();
        if (tierIntensity <= 0.02F) {
            return 0.0F;
        }
        float coreStrength = clamp01(data.getPrecipitationCoreStrength());
        return clamp01(tierIntensity * (0.55F + coreStrength * 0.45F));
    }

    private static float derivedPrecipitation(
            String cloudTypeId,
            float density,
            float coverage,
            float stormPotential
    ) {
        if (!CloudTypeRegistry.isPrecipitatingCloud(cloudTypeId)) {
            return 0.0F;
        }
        float typeCore = CloudTypeRegistry.getOrDefault(cloudTypeId)
                .getVisualProfile()
                .getPrecipitationCoreStrength();
        float support = clamp01(density * 0.45F + coverage * 0.35F + stormPotential * 0.20F);
        return clamp01(typeCore * support);
    }

    private static long seedFrom(int cloudSeed, UUID fallbackId) {
        if (cloudSeed != 0) {
            return cloudSeed;
        }
        long most = fallbackId == null ? 0L : fallbackId.getMostSignificantBits();
        long least = fallbackId == null ? 0L : fallbackId.getLeastSignificantBits();
        return mix(most ^ Long.rotateLeft(least, 17));
    }

    private static long seedFrom(int cloudSeed, String fallbackId) {
        if (cloudSeed != 0) {
            return cloudSeed;
        }
        return mix(fallbackId == null ? 0L : fallbackId.hashCode());
    }

    private static long mix(long value) {
        long mixed = value ^ 0x9E3779B97F4A7C15L;
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        mixed *= 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;
        return mixed;
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
