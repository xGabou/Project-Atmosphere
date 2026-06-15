package net.Gabou.projectatmosphere.modules.ocean;

import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a coherent body of water that exchanges energy with the atmosphere.
 */
public final class OceanBasin {
    private final int id;
    private final Set<RegionInstanceKey> oceanCells;
    private final Map<RegionInstanceKey, Float> influenceWeights;
    private final List<OceanInfluence> basinInfluences = new ArrayList<>();
    private final List<AtmosVolumeInfluence> atmosphereInfluences = new ArrayList<>();

    private final float baseSurfaceTemperature;
    private final float baseHumidity;
    private final float basePressure;

    private float surfaceTemperature;
    private float deepTemperature;
    private float humidityReservoir;
    private float thermalMemory;
    private float multiDayAnomaly;
    private float pressureOffset;
    private WindVector windBias;

    OceanBasin(int id,
               Set<RegionInstanceKey> oceanCells,
               Map<RegionInstanceKey, Float> influenceWeights,
               float baseSurfaceTemperature,
               float baseHumidity,
               float basePressure,
               float deepTemperature,
               WindVector windBias) {
        this.id = id;
        this.oceanCells = Collections.unmodifiableSet(oceanCells);
        this.influenceWeights = new ConcurrentHashMap<>(influenceWeights);
        this.baseSurfaceTemperature = baseSurfaceTemperature;
        this.baseHumidity = baseHumidity;
        this.basePressure = basePressure;
        this.surfaceTemperature = baseSurfaceTemperature;
        this.deepTemperature = deepTemperature;
        this.humidityReservoir = baseHumidity;
        this.thermalMemory = baseSurfaceTemperature;
        this.multiDayAnomaly = 0f;
        this.pressureOffset = basePressure - 1013.25f;
        this.windBias = Objects.requireNonNullElse(windBias, WindVector.fromBase(0f, 0f));
    }

    public int getId() {
        return id;
    }

    public float getSurfaceTemperature() {
        return surfaceTemperature;
    }

    public void setSurfaceTemperature(float value) {
        surfaceTemperature = value;
    }

    public float getDeepTemperature() {
        return deepTemperature;
    }

    public void setDeepTemperature(float value) {
        deepTemperature = value;
    }

    public float getBaseSurfaceTemperature() {
        return baseSurfaceTemperature;
    }

    public float getBaseHumidity() {
        return baseHumidity;
    }

    public float getHumidityReservoir() {
        return humidityReservoir;
    }

    public void setHumidityReservoir(float value) {
        humidityReservoir = Mth.clamp(value, 0f, 1.5f);
    }

    public float getThermalMemory() {
        return thermalMemory;
    }

    public void setThermalMemory(float value) {
        thermalMemory = value;
    }

    public float getMultiDayAnomaly() {
        return multiDayAnomaly;
    }

    public void setMultiDayAnomaly(float value) {
        multiDayAnomaly = value;
    }

    public float getPressureOffset() {
        return pressureOffset;
    }

    public void setPressureOffset(float value) {
        pressureOffset = Mth.clamp(value, -35f, 35f);
    }

    public float getBasePressure() {
        return basePressure;
    }

    public WindVector getWindBias() {
        return windBias;
    }

    public void setWindBias(WindVector windBias) {
        this.windBias = Objects.requireNonNullElse(windBias, WindVector.fromBase(0f, 0f));
    }

    public Set<RegionInstanceKey> getOceanCells() {
        return oceanCells;
    }

    public Map<RegionInstanceKey, Float> getInfluenceWeights() {
        return influenceWeights;
    }

    public void addOceanInfluence(OceanInfluence influence) {
        basinInfluences.add(influence);
    }

    public void addAtmosphereInfluence(AtmosVolumeInfluence influence) {
        atmosphereInfluences.add(influence);
    }

    public void tick(OceanUpdateContext context, Set<RegionInstanceKey> activeKeys) {
        for (OceanInfluence influence : basinInfluences) {
            influence.applyTo(this, context);
        }
        if (atmosphereInfluences.isEmpty() || activeKeys.isEmpty()) {
            return;
        }
        for (Map.Entry<RegionInstanceKey, Float> entry : influenceWeights.entrySet()) {
            RegionInstanceKey key = entry.getKey();
            if (!activeKeys.contains(key)) {
                continue;
            }
            RegionAtmosphereState state = AtmosphericStateRegistry.getState(key);
            if (state == null) {
                continue;
            }
            float weight = Mth.clamp(entry.getValue(), 0f, 1.5f);
            AtmosphericVolume volume = new AtmosphericVolume(this, state, weight, oceanCells.contains(key));
            for (AtmosVolumeInfluence influence : atmosphereInfluences) {
                influence.applyTo(volume, context);
            }
        }
    }

    public boolean intersects(Set<RegionInstanceKey> activeKeys) {
        if (activeKeys.isEmpty()) {
            return false;
        }
        for (RegionInstanceKey key : activeKeys) {
            if (influenceWeights.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    CompoundTag savePersistentState() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", id);
        tag.putFloat("BaseSurfaceTemperature", baseSurfaceTemperature);
        tag.putFloat("BaseHumidity", baseHumidity);
        tag.putFloat("BasePressure", basePressure);
        tag.putFloat("SurfaceTemperature", surfaceTemperature);
        tag.putFloat("DeepTemperature", deepTemperature);
        tag.putFloat("HumidityReservoir", humidityReservoir);
        tag.putFloat("ThermalMemory", thermalMemory);
        tag.putFloat("MultiDayAnomaly", multiDayAnomaly);
        tag.putFloat("PressureOffset", pressureOffset);
        tag.put("WindBias", saveWind(windBias));

        ListTag cells = new ListTag();
        for (RegionInstanceKey key : oceanCells) {
            cells.add(saveRegionKey(key));
        }
        tag.put("OceanCells", cells);

        ListTag weights = new ListTag();
        for (Map.Entry<RegionInstanceKey, Float> entry : influenceWeights.entrySet()) {
            CompoundTag weightTag = saveRegionKey(entry.getKey());
            weightTag.putFloat("Weight", entry.getValue());
            weights.add(weightTag);
        }
        tag.put("InfluenceWeights", weights);
        return tag;
    }

    static OceanBasin loadPersistentState(CompoundTag tag) {
        if (tag == null || !tag.contains("Id", Tag.TAG_INT)) {
            return null;
        }
        Set<RegionInstanceKey> cells = ConcurrentHashMap.newKeySet();
        ListTag cellTags = tag.getList("OceanCells", Tag.TAG_COMPOUND);
        for (int i = 0; i < cellTags.size(); i++) {
            RegionInstanceKey key = loadRegionKey(cellTags.getCompound(i));
            if (key != null) {
                cells.add(key);
            }
        }

        Map<RegionInstanceKey, Float> weights = new ConcurrentHashMap<>();
        ListTag weightTags = tag.getList("InfluenceWeights", Tag.TAG_COMPOUND);
        for (int i = 0; i < weightTags.size(); i++) {
            CompoundTag weightTag = weightTags.getCompound(i);
            RegionInstanceKey key = loadRegionKey(weightTag);
            if (key != null) {
                weights.put(key, weightTag.getFloat("Weight"));
            }
        }

        OceanBasin basin = new OceanBasin(
                tag.getInt("Id"),
                cells,
                weights,
                tag.getFloat("BaseSurfaceTemperature"),
                tag.getFloat("BaseHumidity"),
                tag.getFloat("BasePressure"),
                tag.getFloat("DeepTemperature"),
                loadWind(tag.getCompound("WindBias"))
        );
        basin.setSurfaceTemperature(tag.getFloat("SurfaceTemperature"));
        basin.setHumidityReservoir(tag.getFloat("HumidityReservoir"));
        basin.setThermalMemory(tag.getFloat("ThermalMemory"));
        basin.setMultiDayAnomaly(tag.getFloat("MultiDayAnomaly"));
        basin.setPressureOffset(tag.getFloat("PressureOffset"));
        return basin;
    }

    private static CompoundTag saveRegionKey(RegionInstanceKey key) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("RegionX", key.regionX());
        tag.putInt("RegionZ", key.regionZ());
        tag.putInt("RegionSize", key.regionSize());
        return tag;
    }

    private static RegionInstanceKey loadRegionKey(CompoundTag tag) {
        if (tag == null || !tag.contains("RegionX", Tag.TAG_INT) || !tag.contains("RegionZ", Tag.TAG_INT)) {
            return null;
        }
        int size = tag.contains("RegionSize", Tag.TAG_INT) ? tag.getInt("RegionSize") : RegionInstanceKey.DEFAULT_REGION_SIZE;
        return new RegionInstanceKey(tag.getInt("RegionX"), tag.getInt("RegionZ"), size);
    }

    private static CompoundTag saveWind(WindVector wind) {
        CompoundTag tag = new CompoundTag();
        WindVector safeWind = Objects.requireNonNullElse(wind, WindVector.fromBase(0f, 0f));
        tag.putFloat("BaseSpeed", safeWind.baseSpeed());
        tag.putFloat("AngleRadians", safeWind.angleRadians());
        tag.putFloat("GustSpeed", safeWind.gustSpeed());
        return tag;
    }

    private static WindVector loadWind(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return WindVector.fromBase(0f, 0f);
        }
        float baseSpeed = tag.getFloat("BaseSpeed");
        float angle = tag.getFloat("AngleRadians");
        float gustSpeed = tag.getFloat("GustSpeed");
        if (!Float.isFinite(baseSpeed) || !Float.isFinite(angle) || !Float.isFinite(gustSpeed)) {
            return WindVector.fromBase(0f, 0f);
        }
        return new WindVector(Math.max(0f, baseSpeed), angle, Math.max(0f, gustSpeed));
    }
}
