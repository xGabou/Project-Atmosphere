package net.Gabou.projectatmosphere.modules.region;

import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.world.phys.Vec3;

/**
 * Region-level forecast container with eight weighted sections derived from biomes.
 */
public final class ForecastRegion {
    private final RegionInstanceKey id;
    private final List<BiomeInstanceKey> sourceBiomes;
    private final Section[] sections;
    private final RegionCurves curves;
    private final BiomeFallbackSnapshot fallbackSnapshot;

    public ForecastRegion(RegionInstanceKey id,
                          List<BiomeInstanceKey> sourceBiomes,
                          Section[] sections,
                          RegionCurves curves,
                          BiomeFallbackSnapshot fallbackSnapshot) {
        this.id = id;
        this.sourceBiomes = List.copyOf(sourceBiomes);
        this.sections = sections.clone();
        this.curves = curves;
        this.fallbackSnapshot = fallbackSnapshot;
    }

    public RegionInstanceKey id() {
        return id;
    }

    public RegionCurves curves() {
        return curves;
    }

    public BiomeFallbackSnapshot fallbackSnapshot() {
        return fallbackSnapshot;
    }

    public List<BiomeInstanceKey> sourceBiomes() {
        return sourceBiomes;
    }

    public Section[] sections() {
        return sections.clone();
    }

    public float sampleTemperature(Vec3 inRegionPos, long gameTime) {
        return curves.sampleTemperature(inRegionPos, gameTime, sections);
    }

    public float sampleHumidity(Vec3 inRegionPos, long gameTime) {
        return curves.sampleHumidity(inRegionPos, gameTime, sections);
    }

    public float samplePressure(long gameTime) {
        return curves.samplePressure(gameTime, sections);
    }

    public net.Gabou.projectatmosphere.modules.core.WindVector sampleWind(long gameTime) {
        return curves.sampleWind(gameTime, sections);
    }

    public float sampleStorm(long gameTime) {
        return curves.sampleStorm(gameTime, sections);
    }

    /**
     * Drop references to per-biome snapshots after aggregation to keep runtime region-only.
     */
    public void clearBiomeForecasts() {
        for (Section s : sections) {
            s.clearSnapshot();
        }
    }

    public static final class Section {
        private final float factor;
        private BiomeForecastSnapshot snapshot; // cleared after aggregation

        public Section(float factor, @Nullable BiomeForecastSnapshot snapshot) {
            this.factor = factor;
            this.snapshot = snapshot;
        }

        public float factor() {
            return factor;
        }

        public @Nullable BiomeForecastSnapshot snapshot() {
            return snapshot;
        }

        public void clearSnapshot() {
            this.snapshot = null;
        }
    }

    @Override
    public String toString() {
        return "ForecastRegion{" +
                "id=" + id +
                ", sourceBiomes=" + sourceBiomes.size() +
                ", sections=" + Arrays.toString(sections) +
                '}';
    }
}
