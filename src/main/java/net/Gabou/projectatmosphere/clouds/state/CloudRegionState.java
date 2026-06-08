package net.Gabou.projectatmosphere.clouds.state;

import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
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

/**
 * État backend d'une région de nuage contrôlée par Project Atmosphere.
 * Cette classe représente où le nuage existe et ses limites de simulation de base.
 * Elle ne fait aucun rendu et ne lit aucun état client.
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

    private static final String TAG_SOURCE_REGION = "SourceRegion";
    private static final String TAG_CURRENT_REGION = "CurrentRegion";

    private static final String TAG_REGION_X = "RegionX";
    private static final String TAG_REGION_Z = "RegionZ";
    private static final String TAG_REGION_SIZE = "RegionSize";

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
    private static final String TAG_CLOUD_TYPE_ID = "CloudTypeId";
    private static final String TAG_PREVIOUS_CLOUD_TYPE_ID = "PreviousCloudTypeId";
    private static final String TAG_CLOUD_TYPE_TICKS = "CloudTypeTicks";

    private static final float DEFAULT_RADIUS = 64.0F;
    private static final float DEFAULT_BASE_Y = 128.0F;
    private static final float DEFAULT_TOP_Y = 144.0F;
    private static final float DEFAULT_DENSITY = 0.65F;
    private static final float DEFAULT_COVERAGE = 0.75F;
    private static final float DEFAULT_EDGE_SOFTNESS = 0.35F;
    private static final int DEFAULT_LIFETIME_TICKS = 20 * 60 * 10;

    // Identité stable de cette région de nuage.
    private final UUID regionId;

    // Dimension Minecraft dans laquelle le nuage existe.
    private final ResourceKey<Level> dimension;

    // Région météo PA qui a créé ou influence ce nuage.
    // Peut être null si le nuage a été créé sans source météo connue.
    private final RegionInstanceKey sourceRegionKey;

    // Centre actuel du nuage en coordonnées monde.
    private Vec3 center;

    // Rayon horizontal du nuage.
    private float radius;

    // Limite verticale basse du volume du nuage.
    private float baseY;

    // Limite verticale haute du volume du nuage.
    private float topY;

    // Indique si le nuage est encore actif et peut être rendu.
    private boolean active;

    // Région météo actuelle du nuage. Utile plus tard si le nuage se déplace.
    private RegionInstanceKey currentRegionKey;

    private float density;
    private float coverage;
    private float edgeSoftness;

    // Centre précédent du nuage. Sert plus tard à l'interpolation client.
    private Vec3 previousCenter;

    // Vitesse actuelle du nuage en blocs par tick.
    private Vec3 velocity;

    // Âge actuel du nuage en ticks.
    private int ageTicks;

    // Durée de vie prévue du nuage en ticks.
    private int lifetimeTicks;

    // Facteur de croissance du nuage entre 0 et 1.
    private float growth;

    // Facteur de disparition du nuage entre 0 et 1.
    private float decay;

    // Identifiant du type de nuage courant. La définition complète reste dans CloudTypeRegistry.
    private String cloudTypeId;

    // Identifiant du type précédent, utile pour les transitions futures.
    private String previousCloudTypeId;

    // Durée passée dans le type courant.
    private int cloudTypeTicks;




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
        this.cloudTypeId = CloudTypeRegistry.DEFAULT_CLOUD_TYPE_ID;
        this.previousCloudTypeId = CloudTypeRegistry.DEFAULT_CLOUD_TYPE_ID;
        this.cloudTypeTicks = 0;

        this.active = true;
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
                DEFAULT_DENSITY,
                DEFAULT_COVERAGE,
                DEFAULT_EDGE_SOFTNESS,
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

    public Vec3 getCenter() {
        return center;
    }

    public void setCenter(Vec3 center) {
        this.center = Objects.requireNonNull(center, "center");
    }

    public float getRadius() {
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

    private static float clamp01(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }

        if (value > 1.0F) {
            return 1.0F;
        }

        return value;
    }

    /**
     * Met à jour les limites verticales du nuage en gardant un état valide.
     *
     * @param baseY limite basse du volume
     * @param topY limite haute du volume
     */
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

    public String getCloudTypeId() {
        return cloudTypeId;
    }

    public void setCloudTypeId(String cloudTypeId) {
        this.cloudTypeId = normalizeCloudTypeId(cloudTypeId);
    }

    public String getPreviousCloudTypeId() {
        return previousCloudTypeId;
    }

    public void setPreviousCloudTypeId(String previousCloudTypeId) {
        this.previousCloudTypeId = normalizeCloudTypeId(previousCloudTypeId);
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

    /**
     * Change le type courant du nuage en conservant l'ancien type.
     *
     * @param newCloudTypeId nouvel identifiant de type
     */
    public void changeCloudType(String newCloudTypeId) {
        String normalizedTypeId = normalizeCloudTypeId(newCloudTypeId);
        if (normalizedTypeId.equals(cloudTypeId)) {
            return;
        }

        previousCloudTypeId = cloudTypeId;
        cloudTypeId = normalizedTypeId;
        cloudTypeTicks = 0;
    }

    public @Nullable RegionInstanceKey getCurrentRegionKey() {
        return currentRegionKey;
    }

    public void setCurrentRegionKey(@Nullable RegionInstanceKey currentRegionKey) {
        this.currentRegionKey = currentRegionKey;
    }

    /**
     * Sauvegarde cet état de nuage dans un tag NBT.
     *
     * @return état de nuage sérialisé
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        tag.putUUID(TAG_REGION_ID, regionId);
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
        tag.putString(TAG_CLOUD_TYPE_ID, cloudTypeId);
        tag.putString(TAG_PREVIOUS_CLOUD_TYPE_ID, previousCloudTypeId);
        tag.putInt(TAG_CLOUD_TYPE_TICKS, cloudTypeTicks);

        if (sourceRegionKey != null) {
            tag.put(TAG_SOURCE_REGION, saveRegionKey(sourceRegionKey));
        }

        if (currentRegionKey != null) {
            tag.put(TAG_CURRENT_REGION, saveRegionKey(currentRegionKey));
        }

        return tag;
    }

    /**
     * Charge un état de nuage depuis un tag NBT.
     *
     * @param tag état de nuage sérialisé
     * @return état de nuage chargé
     */
    public static CloudRegionState load(CompoundTag tag) {
        UUID regionId = tag.hasUUID(TAG_REGION_ID) ? tag.getUUID(TAG_REGION_ID) : UUID.randomUUID();

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
        String cloudTypeId = tag.contains(TAG_CLOUD_TYPE_ID, Tag.TAG_STRING)
                ? tag.getString(TAG_CLOUD_TYPE_ID)
                : CloudTypeRegistry.DEFAULT_CLOUD_TYPE_ID;
        String previousCloudTypeId = tag.contains(TAG_PREVIOUS_CLOUD_TYPE_ID, Tag.TAG_STRING)
                ? tag.getString(TAG_PREVIOUS_CLOUD_TYPE_ID)
                : cloudTypeId;
        int cloudTypeTicks = tag.contains(TAG_CLOUD_TYPE_TICKS) ? tag.getInt(TAG_CLOUD_TYPE_TICKS) : 0;

        RegionInstanceKey sourceRegionKey = null;
        if (tag.contains(TAG_SOURCE_REGION, Tag.TAG_COMPOUND)) {
            sourceRegionKey = loadRegionKey(tag.getCompound(TAG_SOURCE_REGION));
        }

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

        state.setActive(!tag.contains(TAG_ACTIVE) || tag.getBoolean(TAG_ACTIVE));
        state.setPreviousCenter(previousCenter);
        state.setVelocity(velocity);
        state.setAgeTicks(ageTicks);
        state.setLifetimeTicks(lifetimeTicks);
        state.setGrowth(growth);
        state.setDecay(decay);
        state.setCloudTypeId(cloudTypeId);
        state.setPreviousCloudTypeId(previousCloudTypeId);
        state.setCloudTypeTicks(cloudTypeTicks);

        if (tag.contains(TAG_CURRENT_REGION, Tag.TAG_COMPOUND)) {
            state.setCurrentRegionKey(loadRegionKey(tag.getCompound(TAG_CURRENT_REGION)));
        } else {
            state.setCurrentRegionKey(sourceRegionKey);
        }

        return state;
    }

    /**
     * Sauvegarde une clé de région météo dans un tag NBT.
     *
     * @param key clé de région météo
     * @return clé de région météo sérialisée
     */
    private static CompoundTag saveRegionKey(RegionInstanceKey key) {
        CompoundTag tag = new CompoundTag();

        tag.putInt(TAG_REGION_X, key.regionX());
        tag.putInt(TAG_REGION_Z, key.regionZ());
        tag.putInt(TAG_REGION_SIZE, key.regionSize());

        return tag;
    }

    /**
     * Charge une clé de région météo depuis un tag NBT.
     *
     * @param tag clé de région météo sérialisée
     * @return clé de région météo chargée
     */
    private static RegionInstanceKey loadRegionKey(CompoundTag tag) {
        return new RegionInstanceKey(
                tag.getInt(TAG_REGION_X),
                tag.getInt(TAG_REGION_Z),
                tag.getInt(TAG_REGION_SIZE)
        );
    }

    private static String normalizeCloudTypeId(String cloudTypeId) {
        return CloudTypeRegistry.getOrDefault(cloudTypeId).getId();
    }
}
