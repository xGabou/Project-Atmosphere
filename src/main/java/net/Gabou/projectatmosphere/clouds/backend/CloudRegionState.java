package net.Gabou.projectatmosphere.clouds.backend;

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

        setCenter(center);
        setRadius(radius);
        setVerticalBounds(baseY, topY);
        setDensity(density);
        setCoverage(coverage);
        setEdgeSoftness(edgeSoftness);

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
    ){
        this.regionId = Objects.requireNonNull(regionId, "regionId");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.sourceRegionKey = sourceRegionKey;

        setCenter(center);
        setRadius(radius);
        setVerticalBounds(baseY, topY);
        this.active = true;

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
        UUID regionId = tag.getUUID(TAG_REGION_ID);

        ResourceLocation dimensionLocation = new ResourceLocation(tag.getString(TAG_DIMENSION));
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionLocation);

        Vec3 center = new Vec3(
                tag.getDouble(TAG_CENTER_X),
                tag.getDouble(TAG_CENTER_Y),
                tag.getDouble(TAG_CENTER_Z)
        );

        float radius = tag.getFloat(TAG_RADIUS);
        float baseY = tag.getFloat(TAG_BASE_Y);
        float topY = tag.getFloat(TAG_TOP_Y);

        float density = tag.contains(TAG_DENSITY) ? tag.getFloat(TAG_DENSITY) : 0.65F;
        float coverage = tag.contains(TAG_COVERAGE) ? tag.getFloat(TAG_COVERAGE) : 0.75F;
        float edgeSoftness = tag.contains(TAG_EDGE_SOFTNESS) ? tag.getFloat(TAG_EDGE_SOFTNESS) : 0.35F;

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

        state.setActive(tag.getBoolean(TAG_ACTIVE));

        if (tag.contains(TAG_CURRENT_REGION, Tag.TAG_COMPOUND)) {
            state.setCurrentRegionKey(loadRegionKey(tag.getCompound(TAG_CURRENT_REGION)));
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
}