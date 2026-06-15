package net.Gabou.projectatmosphere.modules.weathercell;

import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class WeatherCellState {
    private static final String TAG_ID = "Id";
    private static final String TAG_TYPE = "Type";
    private static final String TAG_SOURCE_REGION = "SourceRegion";
    private static final String TAG_CENTER_X = "CenterX";
    private static final String TAG_CENTER_Y = "CenterY";
    private static final String TAG_CENTER_Z = "CenterZ";
    private static final String TAG_RADIUS = "Radius";
    private static final String TAG_INTENSITY = "Intensity";
    private static final String TAG_MOISTURE = "Moisture";
    private static final String TAG_INSTABILITY = "Instability";
    private static final String TAG_PRESSURE_ANOMALY = "PressureAnomaly";
    private static final String TAG_WIND_INFLUENCE = "WindInfluence";
    private static final String TAG_CLOUD_WATER = "CloudWater";
    private static final String TAG_RAIN_INTENSITY = "RainIntensity";
    private static final String TAG_EVOLUTION_SCORE = "EvolutionScore";
    private static final String TAG_SEVERE_EVOLUTION_SCORE = "SevereEvolutionScore";
    private static final String TAG_AGE_TICKS = "AgeTicks";
    private static final String TAG_LIFETIME_TICKS = "LifetimeTicks";
    private static final String TAG_ACTIVE = "Active";
    private static final String TAG_LINKED_CLOUD_REGIONS = "LinkedCloudRegions";
    private static final String TAG_REGION_X = "RegionX";
    private static final String TAG_REGION_Z = "RegionZ";
    private static final String TAG_REGION_SIZE = "RegionSize";

    private final UUID id;
    private WeatherCellType type;
    private RegionInstanceKey sourceRegion;
    private Vec3 center;
    private float radius;
    private float intensity;
    private float moisture;
    private float instability;
    private float pressureAnomaly;
    private float windInfluence;
    private float cloudWater;
    private float rainIntensity;
    private float evolutionScore;
    private float severeEvolutionScore;
    private int ageTicks;
    private int lifetimeTicks;
    private boolean active;
    private final List<UUID> linkedNativeCloudRegionIds = new ArrayList<>();

    public WeatherCellState(UUID id,
                            WeatherCellType type,
                            @Nullable RegionInstanceKey sourceRegion,
                            Vec3 center,
                            float radius,
                            float intensity,
                            float moisture,
                            float instability,
                            float pressureAnomaly,
                            float windInfluence,
                            float cloudWater,
                            float rainIntensity,
                            int ageTicks,
                            int lifetimeTicks,
                            boolean active) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.type = type == null ? WeatherCellType.RAIN_CELL : type;
        this.sourceRegion = sourceRegion;
        this.center = center == null ? Vec3.ZERO : center;
        this.radius = Mth.clamp(radius, 64.0F, 1200.0F);
        this.intensity = clamp01(intensity);
        this.moisture = Mth.clamp(moisture, 0.0F, 1.2F);
        this.instability = clamp01(instability);
        this.pressureAnomaly = Mth.clamp(pressureAnomaly, -60.0F, 60.0F);
        this.windInfluence = clamp01(windInfluence);
        this.cloudWater = Mth.clamp(cloudWater, 0.0F, 1.2F);
        this.rainIntensity = Mth.clamp(rainIntensity, 0.0F, 1.0F);
        this.evolutionScore = clamp01(instability);
        this.severeEvolutionScore = this.type == WeatherCellType.SUPERCELL ? this.evolutionScore : 0.0F;
        this.ageTicks = Math.max(0, ageTicks);
        this.lifetimeTicks = Math.max(1, lifetimeTicks);
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public WeatherCellType getType() {
        return type;
    }

    public void setType(WeatherCellType type) {
        this.type = type == null ? WeatherCellType.RAIN_CELL : type;
    }

    public @Nullable RegionInstanceKey getSourceRegion() {
        return sourceRegion;
    }

    public void setSourceRegion(@Nullable RegionInstanceKey sourceRegion) {
        this.sourceRegion = sourceRegion;
    }

    public Vec3 getCenter() {
        return center;
    }

    public void setCenter(Vec3 center) {
        this.center = center == null ? Vec3.ZERO : center;
    }

    public float getRadius() {
        return radius;
    }

    public void setRadius(float radius) {
        this.radius = Mth.clamp(radius, 64.0F, 1200.0F);
    }

    public float getIntensity() {
        return intensity;
    }

    public void setIntensity(float intensity) {
        this.intensity = clamp01(intensity);
    }

    public float getMoisture() {
        return moisture;
    }

    public void setMoisture(float moisture) {
        this.moisture = Mth.clamp(moisture, 0.0F, 1.2F);
    }

    public float getInstability() {
        return instability;
    }

    public void setInstability(float instability) {
        this.instability = clamp01(instability);
    }

    public float getPressureAnomaly() {
        return pressureAnomaly;
    }

    public void setPressureAnomaly(float pressureAnomaly) {
        this.pressureAnomaly = Mth.clamp(pressureAnomaly, -60.0F, 60.0F);
    }

    public float getWindInfluence() {
        return windInfluence;
    }

    public void setWindInfluence(float windInfluence) {
        this.windInfluence = clamp01(windInfluence);
    }

    public float getCloudWater() {
        return cloudWater;
    }

    public void setCloudWater(float cloudWater) {
        this.cloudWater = Mth.clamp(cloudWater, 0.0F, 1.2F);
    }

    public float getRainIntensity() {
        return rainIntensity;
    }

    public void setRainIntensity(float rainIntensity) {
        this.rainIntensity = Mth.clamp(rainIntensity, 0.0F, 1.0F);
    }

    public float getEvolutionScore() {
        return evolutionScore;
    }

    public void setEvolutionScore(float evolutionScore) {
        this.evolutionScore = clamp01(evolutionScore);
    }

    public float getSevereEvolutionScore() {
        return severeEvolutionScore;
    }

    public void setSevereEvolutionScore(float severeEvolutionScore) {
        this.severeEvolutionScore = clamp01(severeEvolutionScore);
    }

    public int getAgeTicks() {
        return ageTicks;
    }

    public void setAgeTicks(int ageTicks) {
        this.ageTicks = Math.max(0, ageTicks);
    }

    public void incrementAge(int ticks) {
        setAgeTicks(ageTicks + Math.max(0, ticks));
    }

    public int getLifetimeTicks() {
        return lifetimeTicks;
    }

    public void setLifetimeTicks(int lifetimeTicks) {
        this.lifetimeTicks = Math.max(1, lifetimeTicks);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<UUID> getLinkedNativeCloudRegionIds() {
        return List.copyOf(linkedNativeCloudRegionIds);
    }

    public void addLinkedNativeCloudRegionId(UUID cloudRegionId) {
        if (cloudRegionId != null && !linkedNativeCloudRegionIds.contains(cloudRegionId)) {
            linkedNativeCloudRegionIds.add(cloudRegionId);
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_ID, id);
        tag.putString(TAG_TYPE, type.name());
        if (sourceRegion != null) {
            tag.put(TAG_SOURCE_REGION, saveRegionKey(sourceRegion));
        }
        tag.putDouble(TAG_CENTER_X, center.x());
        tag.putDouble(TAG_CENTER_Y, center.y());
        tag.putDouble(TAG_CENTER_Z, center.z());
        tag.putFloat(TAG_RADIUS, radius);
        tag.putFloat(TAG_INTENSITY, intensity);
        tag.putFloat(TAG_MOISTURE, moisture);
        tag.putFloat(TAG_INSTABILITY, instability);
        tag.putFloat(TAG_PRESSURE_ANOMALY, pressureAnomaly);
        tag.putFloat(TAG_WIND_INFLUENCE, windInfluence);
        tag.putFloat(TAG_CLOUD_WATER, cloudWater);
        tag.putFloat(TAG_RAIN_INTENSITY, rainIntensity);
        tag.putFloat(TAG_EVOLUTION_SCORE, evolutionScore);
        tag.putFloat(TAG_SEVERE_EVOLUTION_SCORE, severeEvolutionScore);
        tag.putInt(TAG_AGE_TICKS, ageTicks);
        tag.putInt(TAG_LIFETIME_TICKS, lifetimeTicks);
        tag.putBoolean(TAG_ACTIVE, active);

        ListTag linkedClouds = new ListTag();
        for (UUID linkedId : linkedNativeCloudRegionIds) {
            if (linkedId != null) {
                linkedClouds.add(StringTag.valueOf(linkedId.toString()));
            }
        }
        tag.put(TAG_LINKED_CLOUD_REGIONS, linkedClouds);
        return tag;
    }

    public static WeatherCellState load(CompoundTag tag) {
        UUID id = tag.hasUUID(TAG_ID) ? tag.getUUID(TAG_ID) : UUID.randomUUID();
        WeatherCellType type = parseType(tag.getString(TAG_TYPE));
        RegionInstanceKey sourceRegion = tag.contains(TAG_SOURCE_REGION, Tag.TAG_COMPOUND)
                ? loadRegionKey(tag.getCompound(TAG_SOURCE_REGION))
                : null;
        Vec3 center = new Vec3(
                tag.getDouble(TAG_CENTER_X),
                tag.getDouble(TAG_CENTER_Y),
                tag.getDouble(TAG_CENTER_Z)
        );
        WeatherCellState state = new WeatherCellState(
                id,
                type,
                sourceRegion,
                center,
                tag.contains(TAG_RADIUS, Tag.TAG_FLOAT) ? tag.getFloat(TAG_RADIUS) : 256.0F,
                tag.getFloat(TAG_INTENSITY),
                tag.getFloat(TAG_MOISTURE),
                tag.getFloat(TAG_INSTABILITY),
                tag.getFloat(TAG_PRESSURE_ANOMALY),
                tag.getFloat(TAG_WIND_INFLUENCE),
                tag.getFloat(TAG_CLOUD_WATER),
                tag.getFloat(TAG_RAIN_INTENSITY),
                tag.getInt(TAG_AGE_TICKS),
                tag.contains(TAG_LIFETIME_TICKS, Tag.TAG_INT) ? tag.getInt(TAG_LIFETIME_TICKS) : 12000,
                !tag.contains(TAG_ACTIVE, Tag.TAG_BYTE) || tag.getBoolean(TAG_ACTIVE)
        );

        ListTag linkedClouds = tag.getList(TAG_LINKED_CLOUD_REGIONS, Tag.TAG_STRING);
        state.setEvolutionScore(tag.contains(TAG_EVOLUTION_SCORE, Tag.TAG_FLOAT)
                ? tag.getFloat(TAG_EVOLUTION_SCORE)
                : state.getInstability());
        state.setSevereEvolutionScore(tag.contains(TAG_SEVERE_EVOLUTION_SCORE, Tag.TAG_FLOAT)
                ? tag.getFloat(TAG_SEVERE_EVOLUTION_SCORE)
                : (state.getType() == WeatherCellType.SUPERCELL ? state.getEvolutionScore() : 0.0F));
        for (int i = 0; i < linkedClouds.size(); i++) {
            try {
                state.addLinkedNativeCloudRegionId(UUID.fromString(linkedClouds.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return state;
    }

    private static WeatherCellType parseType(String value) {
        try {
            if (value != null && !value.isBlank()) {
                return WeatherCellType.valueOf(value);
            }
        } catch (IllegalArgumentException ignored) {
        }
        return WeatherCellType.RAIN_CELL;
    }

    private static CompoundTag saveRegionKey(RegionInstanceKey key) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_REGION_X, key.regionX());
        tag.putInt(TAG_REGION_Z, key.regionZ());
        tag.putInt(TAG_REGION_SIZE, key.regionSize());
        return tag;
    }

    private static RegionInstanceKey loadRegionKey(CompoundTag tag) {
        if (tag == null || !tag.contains(TAG_REGION_X, Tag.TAG_INT) || !tag.contains(TAG_REGION_Z, Tag.TAG_INT)) {
            return null;
        }
        int size = tag.contains(TAG_REGION_SIZE, Tag.TAG_INT) ? tag.getInt(TAG_REGION_SIZE) : RegionInstanceKey.DEFAULT_REGION_SIZE;
        return new RegionInstanceKey(tag.getInt(TAG_REGION_X), tag.getInt(TAG_REGION_Z), size);
    }

    private static float clamp01(float value) {
        return Mth.clamp(value, 0.0F, 1.0F);
    }
}
