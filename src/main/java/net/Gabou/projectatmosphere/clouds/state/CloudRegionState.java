package net.Gabou.projectatmosphere.clouds.state;

import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Region container for one or more cloud clusters.
 */
public final class CloudRegionState {

    private static final String TAG_REGION_ID = "RegionId";
    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_CENTER_X = "CenterX";
    private static final String TAG_CENTER_Y = "CenterY";
    private static final String TAG_CENTER_Z = "CenterZ";
    private static final String TAG_RADIUS = "Radius";
    private static final String TAG_BASE_Y = "BaseY";
    private static final String TAG_TOP_Y = "TopY";
    private static final String TAG_ACTIVE = "Active";
    private static final String TAG_DENSITY = "Density";
    private static final String TAG_COVERAGE = "Coverage";
    private static final String TAG_EDGE_SOFTNESS = "EdgeSoftness";
    private static final String TAG_PREVIOUS_CENTER_X = "PreviousCenterX";
    private static final String TAG_PREVIOUS_CENTER_Y = "PreviousCenterY";
    private static final String TAG_PREVIOUS_CENTER_Z = "PreviousCenterZ";
    private static final String TAG_VELOCITY_X = "VelocityX";
    private static final String TAG_VELOCITY_Y = "VelocityY";
    private static final String TAG_VELOCITY_Z = "VelocityZ";
    private static final String TAG_AGE_TICKS = "AgeTicks";
    private static final String TAG_LIFETIME_TICKS = "LifetimeTicks";
    private static final String TAG_GROWTH = "Growth";
    private static final String TAG_DECAY = "Decay";
    private static final String TAG_MERGE_PRESSURE = "MergePressure";
    private static final String TAG_CLOUD_TYPE_ID = "CloudTypeId";
    private static final String TAG_PREVIOUS_CLOUD_TYPE_ID = "PreviousCloudTypeId";
    private static final String TAG_MORPHOLOGY_FAMILY = "MorphologyFamily";
    private static final String TAG_CLOUD_TYPE_TICKS = "CloudTypeTicks";
    private static final String TAG_CLOUD_SEED = "CloudSeed";
    private static final String TAG_SOURCE_REGION = "SourceRegion";
    private static final String TAG_CURRENT_REGION = "CurrentRegion";
    private static final String TAG_REGION_X = "RegionX";
    private static final String TAG_REGION_Z = "RegionZ";
    private static final String TAG_REGION_SIZE = "RegionSize";
    private static final String TAG_CLUSTERS = "Clusters";

    private final UUID regionId;
    private final ResourceKey<Level> dimension;
    private final List<CloudClusterState> clusters = new ArrayList<>();
    private final Map<UUID, Float> interactionStrengths = new LinkedHashMap<>();

    private RegionInstanceKey sourceRegionKey;
    private RegionInstanceKey currentRegionKey;

    public CloudRegionState(
            UUID regionId,
            ResourceKey<Level> dimension,
            Vec3 center,
            float radius,
            float baseY,
            float topY,
            float density,
            float coverage,
            float edgeSoftness,
            @Nullable RegionInstanceKey sourceRegionKey
    ) {
        this.regionId = Objects.requireNonNull(regionId, "regionId");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.sourceRegionKey = sourceRegionKey;
        this.currentRegionKey = sourceRegionKey;
        this.clusters.add(new CloudClusterState(
                UUID.randomUUID(),
                dimension,
                center,
                radius,
                baseY,
                topY,
                density,
                coverage,
                edgeSoftness
        ));
    }

    public CloudRegionState(
            UUID regionId,
            ResourceKey<Level> dimension,
            Vec3 center,
            float radius,
            float baseY,
            float topY,
            @Nullable RegionInstanceKey sourceRegionKey
    ) {
        this(
                regionId,
                dimension,
                center,
                radius,
                baseY,
                topY,
                0.65F,
                0.75F,
                0.35F,
                sourceRegionKey
        );
    }

    public UUID getRegionId() {
        return regionId;
    }

    public ResourceKey<Level> getDimension() {
        return dimension;
    }

    public @Nullable RegionInstanceKey getSourceRegionKey() {
        return sourceRegionKey;
    }

    public void setSourceRegionKey(@Nullable RegionInstanceKey sourceRegionKey) {
        this.sourceRegionKey = sourceRegionKey;
    }

    public @Nullable RegionInstanceKey getCurrentRegionKey() {
        return currentRegionKey;
    }

    public void setCurrentRegionKey(@Nullable RegionInstanceKey currentRegionKey) {
        this.currentRegionKey = currentRegionKey;
    }

    public @NotNull List<CloudClusterState> getClusters() {
        return List.copyOf(clusters);
    }

    public int getClusterCount() {
        return clusters.size();
    }

    public boolean isEmpty() {
        return clusters.isEmpty();
    }

    public void addCluster(@NotNull CloudClusterState cluster) {
        Objects.requireNonNull(cluster, "cluster");
        if (!dimension.equals(cluster.getDimension())) {
            throw new IllegalArgumentException("cluster dimension must match region dimension");
        }
        clusters.add(cluster);
    }

    public void addClusters(@NotNull Collection<CloudClusterState> newClusters) {
        for (CloudClusterState cluster : newClusters) {
            if (cluster != null) {
                addCluster(cluster);
            }
        }
    }

    public boolean removeCluster(@NotNull UUID clusterId) {
        for (int i = 0; i < clusters.size(); i++) {
            if (clusters.get(i).getClusterId().equals(clusterId)) {
                clusters.remove(i);
                return true;
            }
        }
        return false;
    }

    public boolean removeCluster(@NotNull CloudClusterState cluster) {
        return clusters.remove(cluster);
    }

    public void clearClusters() {
        clusters.clear();
    }

    public void clearInteractions() {
        interactionStrengths.clear();
    }

    public void linkInteraction(@NotNull UUID otherRegionId, float strength) {
        interactionStrengths.put(otherRegionId, clamp01(strength));
    }

    public float getInteractionStrength(@NotNull UUID otherRegionId) {
        return interactionStrengths.getOrDefault(otherRegionId, 0.0F);
    }

    public float getStrongestInteractionStrength() {
        float strongest = 0.0F;
        for (float strength : interactionStrengths.values()) {
            strongest = Math.max(strongest, strength);
        }
        return strongest;
    }

    public @NotNull Map<UUID, Float> getInteractionStrengths() {
        return Collections.unmodifiableMap(interactionStrengths);
    }

    public boolean isActive() {
        for (CloudClusterState cluster : clusters) {
            if (cluster != null && cluster.isActive()) {
                return true;
            }
        }
        return false;
    }

    public void setActive(boolean active) {
        for (CloudClusterState cluster : clusters) {
            if (cluster != null) {
                cluster.setActive(active);
            }
        }
    }

    public Vec3 getCenter() {
        return aggregateCenter();
    }

    public void setCenter(Vec3 center) {
        requirePrimaryCluster().setCenter(center);
    }

    public Vec3 getPreviousCenter() {
        return aggregatePreviousCenter();
    }

    public void setPreviousCenter(Vec3 previousCenter) {
        requirePrimaryCluster().setPreviousCenter(previousCenter);
    }

    public Vec3 getVelocity() {
        return aggregateVelocity();
    }

    public void setVelocity(Vec3 velocity) {
        requirePrimaryCluster().setVelocity(velocity);
    }

    public float getRadius() {
        return aggregateRadius();
    }

    public void setRadius(float radius) {
        requirePrimaryCluster().setRadius(radius);
    }

    public float getBaseY() {
        float lowest = Float.POSITIVE_INFINITY;
        for (CloudClusterState cluster : clusters) {
            if (cluster != null) {
                lowest = Math.min(lowest, cluster.getBaseY());
            }
        }
        return lowest == Float.POSITIVE_INFINITY ? 0.0F : lowest;
    }

    public float getTopY() {
        float highest = Float.NEGATIVE_INFINITY;
        for (CloudClusterState cluster : clusters) {
            if (cluster != null) {
                highest = Math.max(highest, cluster.getTopY());
            }
        }
        return highest == Float.NEGATIVE_INFINITY ? 0.0F : highest;
    }

    public void setVerticalBounds(float baseY, float topY) {
        requirePrimaryCluster().setVerticalBounds(baseY, topY);
    }

    public float getDensity() {
        return aggregateScalar(CloudClusterState::getDensity);
    }

    public void setDensity(float density) {
        requirePrimaryCluster().setDensity(density);
    }

    public float getCoverage() {
        return aggregateScalar(CloudClusterState::getCoverage);
    }

    public void setCoverage(float coverage) {
        requirePrimaryCluster().setCoverage(coverage);
    }

    public float getEdgeSoftness() {
        return aggregateScalar(CloudClusterState::getEdgeSoftness);
    }

    public void setEdgeSoftness(float edgeSoftness) {
        requirePrimaryCluster().setEdgeSoftness(edgeSoftness);
    }

    public int getAgeTicks() {
        return getPrimaryCluster().map(CloudClusterState::getAgeTicks).orElse(0);
    }

    public void setAgeTicks(int ageTicks) {
        requirePrimaryCluster().setAgeTicks(ageTicks);
    }

    public int getLifetimeTicks() {
        return getPrimaryCluster().map(CloudClusterState::getLifetimeTicks).orElse(1);
    }

    public void setLifetimeTicks(int lifetimeTicks) {
        requirePrimaryCluster().setLifetimeTicks(lifetimeTicks);
    }

    public float getGrowth() {
        return getPrimaryCluster().map(CloudClusterState::getGrowth).orElse(0.0F);
    }

    public void setGrowth(float growth) {
        requirePrimaryCluster().setGrowth(growth);
    }

    public float getDecay() {
        return getPrimaryCluster().map(CloudClusterState::getDecay).orElse(0.0F);
    }

    public void setDecay(float decay) {
        requirePrimaryCluster().setDecay(decay);
    }

    public float getMergePressure() {
        return getPrimaryCluster().map(CloudClusterState::getMergePressure).orElse(0.0F);
    }

    public void setMergePressure(float mergePressure) {
        requirePrimaryCluster().setMergePressure(mergePressure);
    }

    public String getCloudTypeId() {
        return getPrimaryCluster().map(CloudClusterState::getCloudTypeId).orElse(CloudTypeRegistry.DEFAULT_CLOUD_TYPE_ID);
    }

    public void setCloudTypeId(String cloudTypeId) {
        requirePrimaryCluster().setCloudTypeId(cloudTypeId);
    }

    public String getPreviousCloudTypeId() {
        return getPrimaryCluster().map(CloudClusterState::getPreviousCloudTypeId).orElse(CloudTypeRegistry.DEFAULT_CLOUD_TYPE_ID);
    }

    public void setPreviousCloudTypeId(String previousCloudTypeId) {
        requirePrimaryCluster().setPreviousCloudTypeId(previousCloudTypeId);
    }

    public CloudMorphologyFamily getMorphologyFamily() {
        return getPrimaryCluster()
                .map(CloudClusterState::getMorphologyFamily)
                .orElse(CloudTypeRegistry.getOrDefault(getCloudTypeId()).getMorphologyFamily());
    }

    public void setMorphologyFamily(CloudMorphologyFamily morphologyFamily) {
        requirePrimaryCluster().setMorphologyFamily(morphologyFamily);
    }

    public int getCloudTypeTicks() {
        return getPrimaryCluster().map(CloudClusterState::getCloudTypeTicks).orElse(0);
    }

    public void setCloudTypeTicks(int cloudTypeTicks) {
        requirePrimaryCluster().setCloudTypeTicks(cloudTypeTicks);
    }

    public void incrementCloudTypeTicks() {
        requirePrimaryCluster().incrementCloudTypeTicks();
    }

    public float getTransitionBlend() {
        return getPrimaryCluster().map(CloudClusterState::getTransitionBlend).orElse(0.0F);
    }

    public int getCloudSeed() {
        return getPrimaryCluster().map(CloudClusterState::getCloudSeed).orElse(0);
    }

    public void setCloudSeed(int cloudSeed) {
        requirePrimaryCluster().setCloudSeed(cloudSeed);
    }

    public void changeCloudType(String newCloudTypeId) {
        requirePrimaryCluster().changeCloudType(newCloudTypeId);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        tag.putUUID(TAG_REGION_ID, regionId);
        tag.putString(TAG_DIMENSION, dimension.location().toString());
        if (sourceRegionKey != null) {
            tag.put(TAG_SOURCE_REGION, saveRegionKey(sourceRegionKey));
        }
        if (currentRegionKey != null) {
            tag.put(TAG_CURRENT_REGION, saveRegionKey(currentRegionKey));
        }

        CloudClusterState legacyView = getPrimaryCluster().orElse(null);
        if (legacyView != null) {
            tag.putDouble(TAG_CENTER_X, legacyView.getCenter().x());
            tag.putDouble(TAG_CENTER_Y, legacyView.getCenter().y());
            tag.putDouble(TAG_CENTER_Z, legacyView.getCenter().z());
            tag.putFloat(TAG_RADIUS, legacyView.getRadius());
            tag.putFloat(TAG_BASE_Y, legacyView.getBaseY());
            tag.putFloat(TAG_TOP_Y, legacyView.getTopY());
            tag.putBoolean(TAG_ACTIVE, legacyView.isActive());
            tag.putFloat(TAG_DENSITY, legacyView.getDensity());
            tag.putFloat(TAG_COVERAGE, legacyView.getCoverage());
            tag.putFloat(TAG_EDGE_SOFTNESS, legacyView.getEdgeSoftness());
            tag.putString(TAG_CLOUD_TYPE_ID, legacyView.getCloudTypeId());
            tag.putString(TAG_PREVIOUS_CLOUD_TYPE_ID, legacyView.getPreviousCloudTypeId());
            tag.putString(TAG_MORPHOLOGY_FAMILY, legacyView.getMorphologyFamily().name());
            tag.putInt(TAG_CLOUD_TYPE_TICKS, legacyView.getCloudTypeTicks());
            tag.putFloat(TAG_GROWTH, legacyView.getGrowth());
            tag.putFloat(TAG_DECAY, legacyView.getDecay());
            tag.putFloat(TAG_MERGE_PRESSURE, legacyView.getMergePressure());
            tag.putInt(TAG_AGE_TICKS, legacyView.getAgeTicks());
            tag.putInt(TAG_LIFETIME_TICKS, legacyView.getLifetimeTicks());
            tag.putInt(TAG_CLOUD_SEED, legacyView.getCloudSeed());
            tag.putDouble(TAG_PREVIOUS_CENTER_X, legacyView.getPreviousCenter().x());
            tag.putDouble(TAG_PREVIOUS_CENTER_Y, legacyView.getPreviousCenter().y());
            tag.putDouble(TAG_PREVIOUS_CENTER_Z, legacyView.getPreviousCenter().z());
            tag.putDouble(TAG_VELOCITY_X, legacyView.getVelocity().x());
            tag.putDouble(TAG_VELOCITY_Y, legacyView.getVelocity().y());
            tag.putDouble(TAG_VELOCITY_Z, legacyView.getVelocity().z());
        }

        ListTag clusterTags = new ListTag();
        for (CloudClusterState cluster : clusters) {
            if (cluster != null) {
                clusterTags.add(cluster.save());
            }
        }
        tag.put(TAG_CLUSTERS, clusterTags);
        return tag;
    }

    public static CloudRegionState load(CompoundTag tag) {
        UUID regionId = tag.hasUUID(TAG_REGION_ID) ? tag.getUUID(TAG_REGION_ID) : UUID.randomUUID();

        String dimensionId = tag.contains(TAG_DIMENSION, Tag.TAG_STRING)
                ? tag.getString(TAG_DIMENSION)
                : Level.OVERWORLD.location().toString();
        ResourceLocation dimensionLocation = ResourceLocation.parse(dimensionId);
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionLocation);

        RegionInstanceKey sourceRegionKey = null;
        if (tag.contains(TAG_SOURCE_REGION, Tag.TAG_COMPOUND)) {
            sourceRegionKey = loadRegionKey(tag.getCompound(TAG_SOURCE_REGION));
        }

        RegionInstanceKey currentRegionKey = null;
        if (tag.contains(TAG_CURRENT_REGION, Tag.TAG_COMPOUND)) {
            currentRegionKey = loadRegionKey(tag.getCompound(TAG_CURRENT_REGION));
        } else {
            currentRegionKey = sourceRegionKey;
        }

        Vec3 center = new Vec3(
                tag.getDouble(TAG_CENTER_X),
                tag.getDouble(TAG_CENTER_Y),
                tag.getDouble(TAG_CENTER_Z)
        );

        float radius = tag.contains(TAG_RADIUS) ? tag.getFloat(TAG_RADIUS) : 64.0F;
        if (radius <= 0.0F) {
            radius = 64.0F;
        }

        float baseY = tag.contains(TAG_BASE_Y) ? tag.getFloat(TAG_BASE_Y) : 128.0F;
        float topY = tag.contains(TAG_TOP_Y) ? tag.getFloat(TAG_TOP_Y) : 144.0F;
        if (topY <= baseY) {
            topY = baseY + 16.0F;
        }

        float density = tag.contains(TAG_DENSITY) ? tag.getFloat(TAG_DENSITY) : 0.65F;
        float coverage = tag.contains(TAG_COVERAGE) ? tag.getFloat(TAG_COVERAGE) : 0.75F;
        float edgeSoftness = tag.contains(TAG_EDGE_SOFTNESS) ? tag.getFloat(TAG_EDGE_SOFTNESS) : 0.35F;

        CloudRegionState state = new CloudRegionState(
                regionId,
                dimension,
                center,
                radius,
                baseY,
                topY,
                density,
                coverage,
                edgeSoftness,
                sourceRegionKey
        );
        state.setCurrentRegionKey(currentRegionKey);

        state.getClusters().stream().findFirst().ifPresent(cluster -> {
            if (tag.contains(TAG_PREVIOUS_CENTER_X)) {
                cluster.setPreviousCenter(new Vec3(
                        tag.getDouble(TAG_PREVIOUS_CENTER_X),
                        tag.getDouble(TAG_PREVIOUS_CENTER_Y),
                        tag.getDouble(TAG_PREVIOUS_CENTER_Z)
                ));
            }
            if (tag.contains(TAG_VELOCITY_X)) {
                cluster.setVelocity(new Vec3(
                        tag.getDouble(TAG_VELOCITY_X),
                        tag.getDouble(TAG_VELOCITY_Y),
                        tag.getDouble(TAG_VELOCITY_Z)
                ));
            }
            if (tag.contains(TAG_AGE_TICKS)) {
                cluster.setAgeTicks(tag.getInt(TAG_AGE_TICKS));
            }
            if (tag.contains(TAG_LIFETIME_TICKS)) {
                cluster.setLifetimeTicks(tag.getInt(TAG_LIFETIME_TICKS));
            }
            if (tag.contains(TAG_GROWTH)) {
                cluster.setGrowth(tag.getFloat(TAG_GROWTH));
            }
            if (tag.contains(TAG_DECAY)) {
                cluster.setDecay(tag.getFloat(TAG_DECAY));
            }
            if (tag.contains(TAG_MERGE_PRESSURE)) {
                cluster.setMergePressure(tag.getFloat(TAG_MERGE_PRESSURE));
            }
            if (tag.contains(TAG_CLOUD_TYPE_ID, Tag.TAG_STRING)) {
                cluster.setCloudTypeId(tag.getString(TAG_CLOUD_TYPE_ID));
            }
            if (tag.contains(TAG_PREVIOUS_CLOUD_TYPE_ID, Tag.TAG_STRING)) {
                cluster.setPreviousCloudTypeId(tag.getString(TAG_PREVIOUS_CLOUD_TYPE_ID));
            }
            if (tag.contains(TAG_MORPHOLOGY_FAMILY, Tag.TAG_STRING)) {
                cluster.setMorphologyFamily(CloudMorphologyFamily.byId(
                        tag.getString(TAG_MORPHOLOGY_FAMILY),
                        CloudTypeRegistry.getOrDefault(cluster.getCloudTypeId()).getMorphologyFamily()
                ));
            }
            if (tag.contains(TAG_CLOUD_TYPE_TICKS)) {
                cluster.setCloudTypeTicks(tag.getInt(TAG_CLOUD_TYPE_TICKS));
            }
            if (tag.contains(TAG_CLOUD_SEED)) {
                cluster.setCloudSeed(tag.getInt(TAG_CLOUD_SEED));
            }
        });

        if (tag.contains(TAG_CLUSTERS, Tag.TAG_LIST)) {
            state.clearClusters();
            ListTag clustersTag = tag.getList(TAG_CLUSTERS, Tag.TAG_COMPOUND);
            for (int i = 0; i < clustersTag.size(); i++) {
                state.addCluster(CloudClusterState.load(clustersTag.getCompound(i)));
            }
        }

        if (state.isEmpty()) {
            state.addCluster(new CloudClusterState(
                    UUID.randomUUID(),
                    dimension,
                    center,
                    radius,
                    baseY,
                    topY,
                    density,
                    coverage,
                    edgeSoftness
            ));
        }

        return state;
    }

    public void mergeRegionFrom(@NotNull CloudRegionState other) {
        if (other == this) {
            return;
        }

        addClusters(other.clusters);
        if (sourceRegionKey == null) {
            sourceRegionKey = other.sourceRegionKey;
        }
        if (currentRegionKey == null) {
            currentRegionKey = other.currentRegionKey;
        }
    }

    private @NotNull CloudClusterState requirePrimaryCluster() {
        CloudClusterState cluster = selectPrimaryCluster();
        if (cluster == null) {
            throw new IllegalStateException("cloud region has no clusters");
        }
        return cluster;
    }

    private @Nullable CloudClusterState selectPrimaryCluster() {
        CloudClusterState best = null;
        float bestWeight = -1.0F;
        for (CloudClusterState cluster : clusters) {
            if (cluster == null) {
                continue;
            }
            float weight = cluster.getFootprint();
            if (best == null || weight > bestWeight || (weight == bestWeight && cluster.getClusterId().compareTo(best.getClusterId()) < 0)) {
                best = cluster;
                bestWeight = weight;
            }
        }
        return best;
    }

    private java.util.Optional<CloudClusterState> getPrimaryCluster() {
        return java.util.Optional.ofNullable(selectPrimaryCluster());
    }

    private float aggregateScalar(java.util.function.ToDoubleFunction<CloudClusterState> extractor) {
        double weighted = 0.0D;
        double weightSum = 0.0D;
        for (CloudClusterState cluster : clusters) {
            if (cluster == null) {
                continue;
            }
            double weight = cluster.getFootprint();
            weighted += extractor.applyAsDouble(cluster) * weight;
            weightSum += weight;
        }
        if (weightSum <= 0.0D) {
            return 0.0F;
        }
        return (float) (weighted / weightSum);
    }

    private Vec3 aggregateCenter() {
        return aggregateVec(CloudClusterState::getCenter);
    }

    private Vec3 aggregatePreviousCenter() {
        return aggregateVec(CloudClusterState::getPreviousCenter);
    }

    private Vec3 aggregateVelocity() {
        return aggregateVec(CloudClusterState::getVelocity);
    }

    private Vec3 aggregateVec(java.util.function.Function<CloudClusterState, Vec3> extractor) {
        double weightedX = 0.0D;
        double weightedY = 0.0D;
        double weightedZ = 0.0D;
        double weightSum = 0.0D;

        for (CloudClusterState cluster : clusters) {
            if (cluster == null) {
                continue;
            }
            float weight = cluster.getFootprint();
            Vec3 vec = extractor.apply(cluster);
            weightedX += vec.x() * weight;
            weightedY += vec.y() * weight;
            weightedZ += vec.z() * weight;
            weightSum += weight;
        }

        if (weightSum <= 0.0D) {
            return Vec3.ZERO;
        }

        return new Vec3(weightedX / weightSum, weightedY / weightSum, weightedZ / weightSum);
    }

    private float aggregateRadius() {
        Vec3 center = aggregateCenter();
        float radius = 0.0F;
        for (CloudClusterState cluster : clusters) {
            if (cluster == null) {
                continue;
            }
            double dx = cluster.getCenter().x() - center.x();
            double dz = cluster.getCenter().z() - center.z();
            float horizontalDistance = (float) Math.sqrt((dx * dx) + (dz * dz));
            radius = Math.max(radius, horizontalDistance + cluster.getRadius());
        }
        return radius;
    }

    private static float clamp01(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }
        if (value > 1.0F) {
            return 1.0F;
        }
        return value;
    }

    private static CompoundTag saveRegionKey(RegionInstanceKey key) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_REGION_X, key.regionX());
        tag.putInt(TAG_REGION_Z, key.regionZ());
        tag.putInt(TAG_REGION_SIZE, key.regionSize());
        return tag;
    }

    private static RegionInstanceKey loadRegionKey(CompoundTag tag) {
        return new RegionInstanceKey(
                tag.getInt(TAG_REGION_X),
                tag.getInt(TAG_REGION_Z),
                tag.getInt(TAG_REGION_SIZE)
        );
    }
}
