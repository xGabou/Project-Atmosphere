package net.Gabou.projectatmosphere.clouds.state;

import net.Gabou.projectatmosphere.clouds.type.CloudFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Real cloud body state inside a cloud region.
 */
public final class CloudClusterState {

    private static final String TAG_CLUSTER_ID = "ClusterId";
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

    private static final float DEFAULT_RADIUS = 64.0F;
    private static final float DEFAULT_BASE_Y = 128.0F;
    private static final float DEFAULT_TOP_Y = 144.0F;
    private static final float DEFAULT_DENSITY = 0.65F;
    private static final float DEFAULT_COVERAGE = 0.75F;
    private static final float DEFAULT_EDGE_SOFTNESS = 0.35F;
    private static final int DEFAULT_LIFETIME_TICKS = 20 * 60 * 10;
    private static final int TRANSITION_BLEND_TICKS = 20 * 15;

    private final UUID clusterId;
    private final ResourceKey<Level> dimension;
    private Vec3 center;
    private float radius;
    private float baseY;
    private float topY;
    private boolean active;
    private float density;
    private float coverage;
    private float edgeSoftness;
    private Vec3 previousCenter;
    private Vec3 velocity;
    private int ageTicks;
    private int lifetimeTicks;
    private float growth;
    private float decay;
    private float mergePressure;
    private String cloudTypeId;
    private String previousCloudTypeId;
    private CloudMorphologyFamily morphologyFamily;
    private int cloudTypeTicks;
    private int cloudSeed;

    public CloudClusterState(
            UUID clusterId,
            ResourceKey<Level> dimension,
            Vec3 center,
            float radius,
            float baseY,
            float topY,
            float density,
            float coverage,
            float edgeSoftness
    ) {
        this.clusterId = Objects.requireNonNull(clusterId, "clusterId");
        this.dimension = Objects.requireNonNull(dimension, "dimension");

        setCenter(center);
        setRadius(radius);
        setVerticalBounds(baseY, topY);
        setDensity(density);
        setCoverage(coverage);
        setEdgeSoftness(edgeSoftness);
        this.previousCenter = center;
        this.velocity = Vec3.ZERO;
        this.ageTicks = 0;
        this.lifetimeTicks = DEFAULT_LIFETIME_TICKS;
        this.growth = 1.0F;
        this.decay = 0.0F;
        this.mergePressure = 0.0F;
        this.cloudTypeId = CloudTypeRegistry.DEFAULT_CLOUD_TYPE_ID;
        this.previousCloudTypeId = CloudTypeRegistry.DEFAULT_CLOUD_TYPE_ID;
        this.morphologyFamily = CloudTypeRegistry.getOrDefault(CloudTypeRegistry.DEFAULT_CLOUD_TYPE_ID).getMorphologyFamily();
        this.cloudTypeTicks = 0;
        this.cloudSeed = createRandomCloudSeed();
        this.active = true;
    }

    public CloudClusterState(
            UUID clusterId,
            ResourceKey<Level> dimension,
            Vec3 center,
            float radius,
            float baseY,
            float topY
    ) {
        this(
                clusterId,
                dimension,
                center,
                radius,
                baseY,
                topY,
                DEFAULT_DENSITY,
                DEFAULT_COVERAGE,
                DEFAULT_EDGE_SOFTNESS
        );
    }

    public UUID getClusterId() {
        return clusterId;
    }

    public ResourceKey<Level> getDimension() {
        return dimension;
    }

    public CloudFamily getCloudFamily() {
        return CloudTypeRegistry.getOrDefault(cloudTypeId).getFamily();
    }

    public CloudMorphologyFamily getMorphologyFamily() {
        return morphologyFamily;
    }

    public Vec3 getCenter() {
        return center;
    }

    public void setCenter(Vec3 center) {
        this.center = Objects.requireNonNull(center, "center");
    }

    public Vec3 getPreviousCenter() {
        return previousCenter;
    }

    public void setPreviousCenter(Vec3 previousCenter) {
        this.previousCenter = Objects.requireNonNull(previousCenter, "previousCenter");
    }

    public Vec3 getVelocity() {
        return velocity;
    }

    public void setVelocity(Vec3 velocity) {
        this.velocity = Objects.requireNonNull(velocity, "velocity");
    }

    public float getRadius() {
        return radius;
    }

    public float getSize() {
        return radius;
    }

    public void setRadius(float radius) {
        if (radius <= 0.0F) {
            throw new IllegalArgumentException("radius must be greater than 0");
        }

        this.radius = radius;
    }

    public float getBaseY() {
        return baseY;
    }

    public float getTopY() {
        return topY;
    }

    public void setVerticalBounds(float baseY, float topY) {
        if (topY <= baseY) {
            throw new IllegalArgumentException("topY must be greater than baseY");
        }

        this.baseY = baseY;
        this.topY = topY;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public float getDensity() {
        return density;
    }

    public void setDensity(float density) {
        this.density = clamp01(density);
    }

    public float getCoverage() {
        return coverage;
    }

    public void setCoverage(float coverage) {
        this.coverage = clamp01(coverage);
    }

    public float getEdgeSoftness() {
        return edgeSoftness;
    }

    public void setEdgeSoftness(float edgeSoftness) {
        this.edgeSoftness = clamp01(edgeSoftness);
    }

    public int getAgeTicks() {
        return ageTicks;
    }

    public void setAgeTicks(int ageTicks) {
        this.ageTicks = Math.max(0, ageTicks);
    }

    public int getLifetimeTicks() {
        return lifetimeTicks;
    }

    public void setLifetimeTicks(int lifetimeTicks) {
        this.lifetimeTicks = Math.max(1, lifetimeTicks);
    }

    public float getGrowth() {
        return growth;
    }

    public void setGrowth(float growth) {
        this.growth = clamp01(growth);
    }

    public float getDecay() {
        return decay;
    }

    public void setDecay(float decay) {
        this.decay = clamp01(decay);
    }

    public float getMergePressure() {
        return mergePressure;
    }

    public void setMergePressure(float mergePressure) {
        this.mergePressure = clamp01(mergePressure);
    }

    public String getCloudTypeId() {
        return cloudTypeId;
    }

    public void setCloudTypeId(String cloudTypeId) {
        this.cloudTypeId = normalizeCloudTypeId(cloudTypeId);
        this.morphologyFamily = CloudTypeRegistry.getOrDefault(this.cloudTypeId).getMorphologyFamily();
    }

    public String getPreviousCloudTypeId() {
        return previousCloudTypeId;
    }

    public void setPreviousCloudTypeId(String previousCloudTypeId) {
        this.previousCloudTypeId = normalizeCloudTypeId(previousCloudTypeId);
    }

    public void setMorphologyFamily(CloudMorphologyFamily morphologyFamily) {
        this.morphologyFamily = morphologyFamily == null
                ? CloudTypeRegistry.getOrDefault(cloudTypeId).getMorphologyFamily()
                : morphologyFamily;
    }

    public int getCloudTypeTicks() {
        return cloudTypeTicks;
    }

    public void setCloudTypeTicks(int cloudTypeTicks) {
        this.cloudTypeTicks = Math.max(0, cloudTypeTicks);
    }

    public void incrementCloudTypeTicks() {
        this.cloudTypeTicks++;
    }

    public float getTransitionBlend() {
        if (cloudTypeTicks <= 0) {
            return 0.0F;
        }

        return clamp01((float) cloudTypeTicks / (float) TRANSITION_BLEND_TICKS);
    }

    public int getCloudSeed() {
        return cloudSeed;
    }

    public void setCloudSeed(int cloudSeed) {
        this.cloudSeed = cloudSeed;
    }

    public float getFootprint() {
        return Math.max(1.0F, radius * coverage * density);
    }

    public void changeCloudType(String newCloudTypeId) {
        String normalizedTypeId = normalizeCloudTypeId(newCloudTypeId);
        if (normalizedTypeId.equals(cloudTypeId)) {
            return;
        }

        previousCloudTypeId = cloudTypeId;
        cloudTypeId = normalizedTypeId;
        morphologyFamily = CloudTypeRegistry.getOrDefault(cloudTypeId).getMorphologyFamily();
        cloudTypeTicks = 0;
    }

    public void absorb(@Nullable CloudClusterState other) {
        if (other == null || other == this) {
            return;
        }

        float thisWeight = getFootprint();
        float otherWeight = other.getFootprint();
        float totalWeight = Math.max(0.001F, thisWeight + otherWeight);

        setPreviousCenter(weightedVec(previousCenter, thisWeight, other.previousCenter, otherWeight));
        setCenter(weightedVec(center, thisWeight, other.center, otherWeight));
        setVelocity(weightedVec(velocity, thisWeight, other.velocity, otherWeight));
        setRadius(Math.max(radius, other.radius) + (Math.min(radius, other.radius) * 0.25F));
        setVerticalBounds(Math.min(baseY, other.baseY), Math.max(topY, other.topY));
        setDensity(weightedFloat(density, thisWeight, other.density, otherWeight));
        setCoverage(weightedFloat(coverage, thisWeight, other.coverage, otherWeight));
        setEdgeSoftness(weightedFloat(edgeSoftness, thisWeight, other.edgeSoftness, otherWeight));
        setAgeTicks(Math.round(weightedFloat((float) ageTicks, thisWeight, (float) other.ageTicks, otherWeight)));
        setLifetimeTicks(Math.round(Math.max(lifetimeTicks, other.lifetimeTicks)));
        setGrowth(weightedFloat(growth, thisWeight, other.growth, otherWeight));
        setDecay(weightedFloat(decay, thisWeight, other.decay, otherWeight));
        setMergePressure(Math.max(mergePressure, other.mergePressure));
        setCloudSeed(mixSeeds(cloudSeed, other.cloudSeed));

        if (otherWeight > thisWeight && !cloudTypeId.equals(other.cloudTypeId)) {
            previousCloudTypeId = cloudTypeId;
            cloudTypeId = normalizeCloudTypeId(other.cloudTypeId);
            morphologyFamily = other.morphologyFamily;
            cloudTypeTicks = Math.max(cloudTypeTicks, other.cloudTypeTicks);
        } else {
            cloudTypeTicks = Math.max(cloudTypeTicks, other.cloudTypeTicks);
        }

        active = active || other.active;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        tag.putUUID(TAG_CLUSTER_ID, clusterId);
        tag.putString(TAG_DIMENSION, dimension.location().toString());
        tag.putDouble(TAG_CENTER_X, center.x());
        tag.putDouble(TAG_CENTER_Y, center.y());
        tag.putDouble(TAG_CENTER_Z, center.z());
        tag.putFloat(TAG_RADIUS, radius);
        tag.putFloat(TAG_BASE_Y, baseY);
        tag.putFloat(TAG_TOP_Y, topY);
        tag.putBoolean(TAG_ACTIVE, active);
        tag.putFloat(TAG_DENSITY, density);
        tag.putFloat(TAG_COVERAGE, coverage);
        tag.putFloat(TAG_EDGE_SOFTNESS, edgeSoftness);
        tag.putDouble(TAG_PREVIOUS_CENTER_X, previousCenter.x());
        tag.putDouble(TAG_PREVIOUS_CENTER_Y, previousCenter.y());
        tag.putDouble(TAG_PREVIOUS_CENTER_Z, previousCenter.z());
        tag.putDouble(TAG_VELOCITY_X, velocity.x());
        tag.putDouble(TAG_VELOCITY_Y, velocity.y());
        tag.putDouble(TAG_VELOCITY_Z, velocity.z());
        tag.putInt(TAG_AGE_TICKS, ageTicks);
        tag.putInt(TAG_LIFETIME_TICKS, lifetimeTicks);
        tag.putFloat(TAG_GROWTH, growth);
        tag.putFloat(TAG_DECAY, decay);
        tag.putFloat(TAG_MERGE_PRESSURE, mergePressure);
        tag.putString(TAG_CLOUD_TYPE_ID, cloudTypeId);
        tag.putString(TAG_PREVIOUS_CLOUD_TYPE_ID, previousCloudTypeId);
        tag.putString(TAG_MORPHOLOGY_FAMILY, morphologyFamily.name());
        tag.putInt(TAG_CLOUD_TYPE_TICKS, cloudTypeTicks);
        tag.putInt(TAG_CLOUD_SEED, cloudSeed);

        return tag;
    }

    public static CloudClusterState load(CompoundTag tag) {
        UUID clusterId = tag.hasUUID(TAG_CLUSTER_ID) ? tag.getUUID(TAG_CLUSTER_ID) : UUID.randomUUID();

        String dimensionId = tag.contains(TAG_DIMENSION, Tag.TAG_STRING)
                ? tag.getString(TAG_DIMENSION)
                : Level.OVERWORLD.location().toString();
        ResourceLocation dimensionLocation = new ResourceLocation(dimensionId);
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionLocation);

        Vec3 center = new Vec3(
                tag.getDouble(TAG_CENTER_X),
                tag.getDouble(TAG_CENTER_Y),
                tag.getDouble(TAG_CENTER_Z)
        );

        float radius = tag.contains(TAG_RADIUS) ? tag.getFloat(TAG_RADIUS) : DEFAULT_RADIUS;
        if (radius <= 0.0F) {
            radius = DEFAULT_RADIUS;
        }

        float baseY = tag.contains(TAG_BASE_Y) ? tag.getFloat(TAG_BASE_Y) : DEFAULT_BASE_Y;
        float topY = tag.contains(TAG_TOP_Y) ? tag.getFloat(TAG_TOP_Y) : DEFAULT_TOP_Y;
        if (topY <= baseY) {
            topY = baseY + (DEFAULT_TOP_Y - DEFAULT_BASE_Y);
        }

        float density = tag.contains(TAG_DENSITY) ? tag.getFloat(TAG_DENSITY) : DEFAULT_DENSITY;
        float coverage = tag.contains(TAG_COVERAGE) ? tag.getFloat(TAG_COVERAGE) : DEFAULT_COVERAGE;
        float edgeSoftness = tag.contains(TAG_EDGE_SOFTNESS) ? tag.getFloat(TAG_EDGE_SOFTNESS) : DEFAULT_EDGE_SOFTNESS;

        Vec3 previousCenter = tag.contains(TAG_PREVIOUS_CENTER_X)
                ? new Vec3(
                tag.getDouble(TAG_PREVIOUS_CENTER_X),
                tag.getDouble(TAG_PREVIOUS_CENTER_Y),
                tag.getDouble(TAG_PREVIOUS_CENTER_Z)
        )
                : center;

        Vec3 velocity = tag.contains(TAG_VELOCITY_X)
                ? new Vec3(
                tag.getDouble(TAG_VELOCITY_X),
                tag.getDouble(TAG_VELOCITY_Y),
                tag.getDouble(TAG_VELOCITY_Z)
        )
                : Vec3.ZERO;

        int ageTicks = tag.contains(TAG_AGE_TICKS) ? tag.getInt(TAG_AGE_TICKS) : 0;
        int lifetimeTicks = tag.contains(TAG_LIFETIME_TICKS) ? tag.getInt(TAG_LIFETIME_TICKS) : DEFAULT_LIFETIME_TICKS;
        float growth = tag.contains(TAG_GROWTH) ? tag.getFloat(TAG_GROWTH) : 1.0F;
        float decay = tag.contains(TAG_DECAY) ? tag.getFloat(TAG_DECAY) : 0.0F;
        float mergePressure = tag.contains(TAG_MERGE_PRESSURE) ? tag.getFloat(TAG_MERGE_PRESSURE) : 0.0F;
        String cloudTypeId = tag.contains(TAG_CLOUD_TYPE_ID, Tag.TAG_STRING)
                ? tag.getString(TAG_CLOUD_TYPE_ID)
                : CloudTypeRegistry.DEFAULT_CLOUD_TYPE_ID;
        String previousCloudTypeId = tag.contains(TAG_PREVIOUS_CLOUD_TYPE_ID, Tag.TAG_STRING)
                ? tag.getString(TAG_PREVIOUS_CLOUD_TYPE_ID)
                : cloudTypeId;
        CloudMorphologyFamily morphologyFamily = tag.contains(TAG_MORPHOLOGY_FAMILY, Tag.TAG_STRING)
                ? CloudMorphologyFamily.byId(tag.getString(TAG_MORPHOLOGY_FAMILY), CloudTypeRegistry.getOrDefault(cloudTypeId).getMorphologyFamily())
                : CloudTypeRegistry.getOrDefault(cloudTypeId).getMorphologyFamily();
        int cloudTypeTicks = tag.contains(TAG_CLOUD_TYPE_TICKS) ? tag.getInt(TAG_CLOUD_TYPE_TICKS) : 0;
        int cloudSeed = tag.contains(TAG_CLOUD_SEED) ? tag.getInt(TAG_CLOUD_SEED) : deriveCloudSeed(clusterId);

        CloudClusterState state = new CloudClusterState(
                clusterId,
                dimension,
                center,
                radius,
                baseY,
                topY,
                density,
                coverage,
                edgeSoftness
        );

        state.setActive(!tag.contains(TAG_ACTIVE) || tag.getBoolean(TAG_ACTIVE));
        state.setPreviousCenter(previousCenter);
        state.setVelocity(velocity);
        state.setAgeTicks(ageTicks);
        state.setLifetimeTicks(lifetimeTicks);
        state.setGrowth(growth);
        state.setDecay(decay);
        state.setMergePressure(mergePressure);
        state.setCloudTypeId(cloudTypeId);
        state.setPreviousCloudTypeId(previousCloudTypeId);
        state.setMorphologyFamily(morphologyFamily);
        state.setCloudTypeTicks(cloudTypeTicks);
        state.setCloudSeed(cloudSeed);

        return state;
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

    private static Vec3 weightedVec(Vec3 first, float firstWeight, Vec3 second, float secondWeight) {
        float total = Math.max(0.001F, firstWeight + secondWeight);
        return new Vec3(
                ((first.x() * firstWeight) + (second.x() * secondWeight)) / total,
                ((first.y() * firstWeight) + (second.y() * secondWeight)) / total,
                ((first.z() * firstWeight) + (second.z() * secondWeight)) / total
        );
    }

    private static float weightedFloat(float first, float firstWeight, float second, float secondWeight) {
        float total = Math.max(0.001F, firstWeight + secondWeight);
        return ((first * firstWeight) + (second * secondWeight)) / total;
    }

    private static int mixSeeds(int first, int second) {
        int mixed = first ^ Integer.rotateLeft(second, 13);
        mixed ^= (mixed >>> 16);
        return mixed;
    }

    private static String normalizeCloudTypeId(String cloudTypeId) {
        return CloudTypeRegistry.getOrDefault(cloudTypeId).getId();
    }

    private static int createRandomCloudSeed() {
        return ThreadLocalRandom.current().nextInt();
    }

    private static int deriveCloudSeed(UUID clusterId) {
        long mixed = clusterId.getMostSignificantBits() ^ Long.rotateLeft(clusterId.getLeastSignificantBits(), 21);
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        return (int) mixed;
    }
}
