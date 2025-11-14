package net.Gabou.projectatmosphere.modules.ocean;

import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.modules.atmosphere.RegionAtmosphereState;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
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
    private final Set<BiomeInstanceKey> oceanCells;
    private final Map<BiomeInstanceKey, Float> influenceWeights;
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
               Set<BiomeInstanceKey> oceanCells,
               Map<BiomeInstanceKey, Float> influenceWeights,
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

    public Set<BiomeInstanceKey> getOceanCells() {
        return oceanCells;
    }

    public Map<BiomeInstanceKey, Float> getInfluenceWeights() {
        return influenceWeights;
    }

    public void addOceanInfluence(OceanInfluence influence) {
        basinInfluences.add(influence);
    }

    public void addAtmosphereInfluence(AtmosVolumeInfluence influence) {
        atmosphereInfluences.add(influence);
    }

    public void tick(OceanUpdateContext context, Set<BiomeInstanceKey> activeKeys) {
        for (OceanInfluence influence : basinInfluences) {
            influence.applyTo(this, context);
        }
        if (atmosphereInfluences.isEmpty() || activeKeys.isEmpty()) {
            return;
        }
        for (Map.Entry<BiomeInstanceKey, Float> entry : influenceWeights.entrySet()) {
            BiomeInstanceKey key = entry.getKey();
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

    public boolean intersects(Set<BiomeInstanceKey> activeKeys) {
        if (activeKeys.isEmpty()) {
            return false;
        }
        for (BiomeInstanceKey key : activeKeys) {
            if (influenceWeights.containsKey(key)) {
                return true;
            }
        }
        return false;
    }
}
