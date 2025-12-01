package net.Gabou.projectatmosphere.modules.wind;

import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.modules.core.BiomeForecast;
import net.Gabou.projectatmosphere.modules.core.ForecastType;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.Gabou.projectatmosphere.util.AtmosphericPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

public class WindGenerator {

    /**
     * Cache from a sample key to its neighbor set.
     * Filled lazily using the spatial index below.
     */
    private static final Map<BiomeInstanceKey, Set<BiomeInstanceKey>> neighborCache = new ConcurrentHashMap<>();

    /**
     * Max neighbor distance (in blocks) and precalculated squared value.
     */
    private static final int MIN_DISTANCE = 200;
    private static final int MIN_DISTANCE_SQR = MIN_DISTANCE * MIN_DISTANCE;

    /**
     * Cell size for grid-based spatial bucketing.
     * We choose it equal to MIN_DISTANCE so any neighbor within MIN_DISTANCE
     * must be in the same cell or one of the 8 adjacent cells.
     */
    private static final int CELL_SIZE = MIN_DISTANCE;

    /**
     * Read-only spatial index: maps cell key -> array of samples in that cell.
     * Built once per region via buildNeighborIndex(...) then treated as immutable.
     */
    private static volatile Map<Long, BiomeInstanceKey[]> spatialIndex = Collections.emptyMap();

    /**
     * Optional: keep a reference to the current region's samples for fallback.
     */
    private static volatile Set<BiomeInstanceKey> allSamples = Collections.emptySet();

    private static final float SPEED_SCALING = 1.0f;

    /**
     * Build a spatial index for the given set of samples.
     * Call this once per forecast region, after biomeSamples/FORECAST_MAP are fully prepared
     * and before generating wind.
     */
    public static void buildNeighborIndex(Set<BiomeInstanceKey> samples) {
        if (samples == null || samples.isEmpty()) {
            spatialIndex = Collections.emptyMap();
            allSamples = Collections.emptySet();
            neighborCache.clear();
            return;
        }

        allSamples = samples;
        neighborCache.clear();

        // 1) Bucket samples by cell
        Map<Long, List<BiomeInstanceKey>> buckets = new HashMap<>(samples.size() * 2);

        for (BiomeInstanceKey key : samples) {
            BlockPos pos = key.samplePos();
            int cellX = Math.floorDiv(pos.getX(), CELL_SIZE);
            int cellZ = Math.floorDiv(pos.getZ(), CELL_SIZE);
            long cellKey = toCellKey(cellX, cellZ);

            buckets.computeIfAbsent(cellKey, k -> new ArrayList<>()).add(key);
        }

        // 2) Convert lists to arrays for cheaper iteration
        Map<Long, BiomeInstanceKey[]> index = new HashMap<>(buckets.size() * 2);
        for (Map.Entry<Long, List<BiomeInstanceKey>> entry : buckets.entrySet()) {
            List<BiomeInstanceKey> list = entry.getValue();
            index.put(entry.getKey(), list.toArray(new BiomeInstanceKey[0]));
        }

        // 3) Publish the index (volatile write => safe for readers)
        spatialIndex = index;
    }

    /**
     * Encode (cellX, cellZ) into a long key.
     */
    private static long toCellKey(int cellX, int cellZ) {
        return (((long) cellX) << 32) ^ (cellZ & 0xffffffffL);
    }

    /**
     * Find neighbors of `self` within MIN_DISTANCE, using the spatial index.
     * Signature kept the same; `all` is only used as a fallback if the index isn't built.
     */
    private static Set<BiomeInstanceKey> getNeighbors(BiomeInstanceKey self, Set<BiomeInstanceKey> all) {
        // Fast path: cached neighbors
        Set<BiomeInstanceKey> cached = neighborCache.get(self);
        if (cached != null) {
            return cached;
        }

        BlockPos center = self.samplePos();
        int centerX = center.getX();
        int centerZ = center.getZ();

        int cellX = Math.floorDiv(centerX, CELL_SIZE);
        int cellZ = Math.floorDiv(centerZ, CELL_SIZE);

        Set<BiomeInstanceKey> result = new HashSet<>();
        Map<Long, BiomeInstanceKey[]> index = spatialIndex;

        if (index != null && !index.isEmpty()) {
            // Look in this cell and 8 neighbors (3x3 block)
            for (int dz = -1; dz <= 1; dz++) {
                int cz = cellZ + dz;
                for (int dx = -1; dx <= 1; dx++) {
                    int cx = cellX + dx;
                    BiomeInstanceKey[] bucket = index.get(toCellKey(cx, cz));
                    if (bucket == null) continue;

                    for (BiomeInstanceKey other : bucket) {
                        if (other == self) continue; // identity check as in original code

                        BlockPos o = other.samplePos();
                        int dxPos = o.getX() - centerX;
                        int dzPos = o.getZ() - centerZ;
                        int distSq = dxPos * dxPos + dzPos * dzPos;

                        if (distSq <= MIN_DISTANCE_SQR) {
                            result.add(other);
                        }
                    }
                }
            }
        } else if (all != null && !all.isEmpty()) {
            // Fallback: old O(N) behavior if index hasn't been built (shouldn't happen in normal use)
            for (BiomeInstanceKey other : all) {
                if (other == self) continue;
                BlockPos o = other.samplePos();
                int dxPos = o.getX() - centerX;
                int dzPos = o.getZ() - centerZ;
                int distSq = dxPos * dxPos + dzPos * dzPos;
                if (distSq <= MIN_DISTANCE_SQR) {
                    result.add(other);
                }
            }
        }

        // Store in cache; if another thread beat us, use its value
        Set<BiomeInstanceKey> existing = neighborCache.putIfAbsent(self, result);
        return existing != null ? existing : result;
    }

    public static WindVector[] generateWindWeek(BiomeInstanceKey selfKey) {
        BlockPos center = selfKey.samplePos();
        int centerX = center.getX();
        int centerZ = center.getZ();

        ResourceLocation biome = selfKey.biomeType();
        WindVector[] result = new WindVector[7];

        BiomeForecast biomeForecast = ForecastGenerator.getClosestValidForecast(selfKey, ForecastType.PRESSURE);
        if (biomeForecast == null) {
            // Fallback: random gentle wind if no forecast is available
            for (int d = 0; d < 7; d++) {
                float randomAngle = (float) (Math.random() * Math.PI * 2);
                result[d] = WindVector.fromBase(1.2f, randomAngle);
            }
            return result;
        }

        float[][] selfPressure = biomeForecast.getPressure();
        float[][] selfTemp = biomeForecast.getTemperature();
        float[][] selfHumidity = biomeForecast.getHumidity();

        // Using allSamples as the same set passed into buildNeighborIndex.
        // ForecastGenerator.getBiomeSamples() is kept to preserve behavior.
        Set<BiomeInstanceKey> neighbors = getNeighbors(selfKey, ForecastGenerator.getBiomeSamples());

        float altitude = center.getY();
        float biomeFactor = getBiomeWindModifier(biome);
        double[] airDensity = AtmosphericPhysics.computeAirDensity(selfTemp, selfHumidity);

        for (int d = 0; d < 7; d++) {
            float Pself = (selfPressure[d][0] + selfPressure[d][1]) * 0.5f;
            float Pavg = 0f;
            int count = 0;

            // Accumulate wind components directly instead of allocating Vec2 per neighbor.
            float sumVx = 0f;
            float sumVz = 0f;

            for (BiomeInstanceKey key : neighbors) {
                BiomeForecast forecast = ForecastGenerator.getClosestValidForecast(key, ForecastType.PRESSURE);
                if (forecast == null) continue;
                float[][] p = forecast.getPressure();
                if (p == null) continue;

                float Pn = (p[d][0] + p[d][1]) * 0.5f;
                Pavg += Pn;
                count++;

                BlockPos neighborPos = key.samplePos();
                float dx = neighborPos.getX() - centerX;
                float dz = neighborPos.getZ() - centerZ;
                float distSq = dx * dx + dz * dz;
                if (distSq < 1e-4f) continue; // equivalent to dist < 1e-2

                float dist = Mth.sqrt(distSq);
                float dP = Pself - Pn;

                float vx = dP * dx / dist;
                float vz = dP * dz / dist;

                sumVx += vx;
                sumVz += vz;
            }

            if (count == 0) {
                float randomAngle = (float) (Math.random() * Math.PI * 2);
                result[d] = WindVector.fromBase(1.2f, randomAngle);
                continue;
            }

            Pavg /= count;
            float dP = Math.abs(Pavg - Pself);

            float densityFactor = (float) (airDensity[d] / 1.225f);
            float normalizedAltitude = Math.min(altitude / 256f, 1f);
            float altitudeFactor = 0.5f + 0.5f * normalizedAltitude;

            float speed = (float) Math.sqrt(2 * dP / densityFactor) * biomeFactor * altitudeFactor * SPEED_SCALING;
            speed = Mth.clamp(speed, 1.2f, 60f);

            int hash = selfKey.hashCode();
            float baseSpeed = speed;
            float gustFactor = Mth.sin((d + hash % 50) * 0.6f) * 0.5f + 1.6f;

            float gustSpeed = baseSpeed * gustFactor;
            gustSpeed = Mth.clamp(gustSpeed, 1.5f, 75f);

            float lenSq = sumVx * sumVx + sumVz * sumVz;
            float angle;
            if (lenSq > 1e-6f) {
                angle = (float) Math.atan2(sumVz, sumVx);
            } else {
                // If the pressure differences cancel out almost perfectly, pick a default direction.
                angle = 0f;
            }

            result[d] = new WindVector(baseSpeed, angle, gustSpeed);
        }

        return result;
    }

    private static float getBiomeWindModifier(ResourceLocation biome) {
        String path = biome.getPath();
        if (path.contains("forest") || path.contains("taiga") || path.contains("jungle")) return 0.7f;
        if (path.contains("plains") || path.contains("savanna")) return 1.1f;
        if (path.contains("ocean") || path.contains("beach")) return 1.25f;
        if (path.contains("mountain") || path.contains("peak") || path.contains("windswept")) return 1.4f;
        return 1.0f;
    }
}
