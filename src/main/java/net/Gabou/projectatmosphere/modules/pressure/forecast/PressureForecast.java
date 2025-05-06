package net.Gabou.projectatmosphere.modules.pressure.forecast;

import net.Gabou.projectatmosphere.modules.pressure.util.PressureGenerator;
import net.Gabou.projectatmosphere.modules.pressure.util.PressureProfileManager;
import net.Gabou.projectatmosphere.modules.pressure.util.PressureStorageManager;
import net.Gabou.projectatmosphere.modules.pressure.util.DailyPressureGenerator;
import net.Gabou.projectatmosphere.modules.pressure.util.PressureCurveGenerator;
import net.Gabou.projectatmosphere.util.AtmosphereUtils;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

/**
 * Unified forecast system:
 *  - Full (weekly + diffusion + daily curves) via generateFullForecast()
 *  - Low‐detail (weekly only) via generateLowDetailForecast()
 *  - Inactive cleanup (every 6000 ticks) via cleanupInactiveBiomes()
 */



//TODO transfer maps to PressureProfileManager
public class PressureForecast {

    private static final Map<BiomeInstanceKey, float[][]> activeWeekly = new HashMap<>();
    private static final Map<BiomeInstanceKey, float[][]> inactiveWeekly = new HashMap<>();


    public static Map<BiomeInstanceKey, float[][]> getActiveWeekly() {
        return activeWeekly;
    }

    public static float[][] getWeeklyForecast(ResourceLocation biome, BlockPos samplePos) {
        BiomeInstanceKey key = new BiomeInstanceKey(biome, samplePos);
        if (activeWeekly.containsKey(key)) {
            return activeWeekly.get(key);
        } else if (inactiveWeekly.containsKey(key)) {
            return inactiveWeekly.get(key);
        }
        return null;
    }

    // diffusion parameters
    private static final int DIFFUSION_RADIUS = 200; // blocks
    private static final float DIFFUSION_RATE = 0.1f;

    /**
     * 1) Generate a raw weekly forecast for each biome in range
     * 2) Smooth (diffuse) each biome’s week against its neighbors
     * 3) Persist into ProfileManager & StorageManager
     * 4) Schedule daily curve generation
     */
    public static void generateFullForecast(ServerLevel world, BlockPos center, int radius) {
        Set<BiomeInstanceKey> biomeSamples = AtmosphereUtils.findBiomes(world, center, radius);
        activeWeekly.clear();

        // Step 1 — Generate raw weekly pressure for each biome instance
        for (var entry : biomeSamples) {
            float[][] week = PressureGenerator.generateWeekForecast(world,entry);
            PressureProfileManager.putWeeklyForecast(entry, week);
            PressureStorageManager.putForecast(entry, week);
            activeWeekly.put(entry, week);
        }

        // Step 2 — Apply smoothing
        diffuseAll();

        for (var entry : activeWeekly.entrySet()) {
            BiomeInstanceKey key = entry.getKey();
            float[][] week = entry.getValue();

            // Smooth each day using a 3-day weighted average
            for (int d = 0; d < 7; d++) {
                float[] prev = (d > 0) ? week[d - 1] : week[d];
                float[] curr = week[d];
                float[] next = (d < 6) ? week[d + 1] : week[d];

                for (int i = 0; i < 2; i++) {
                    curr[i] = (prev[i] + 2 * curr[i] + next[i]) / 4f;
                }
            }
            activeWeekly.put(key, week);
        }

        // Step 4 — Generate daily pressure curves
        DailyPressureGenerator.scheduleGenerationForTodayAndTomorrow(world);
    }

    /**
     * Compute base weekly values only (no daily curves or smoothing).
     */
    public static void generateLowDetailForecast(ServerLevel world, BlockPos center, int radius) {
        Set<BiomeInstanceKey> biomeSamples = AtmosphereUtils.findBiomes(world, center, radius);

        for (var entry : biomeSamples) {

            if (activeWeekly.containsKey(entry) || inactiveWeekly.containsKey(entry)) continue;

            float[][] week = PressureGenerator.generateWeekForecast(world, entry);
            inactiveWeekly.put(entry, week);

            PressureProfileManager.putWeeklyForecast(entry, week);
            PressureStorageManager.putForecast(entry, week);
        }
    }

    /**
     * Called every 6000 ticks (~4x per day). Deactivates unused biome samples.
     */
    public static void cleanupInactiveBiomes(ServerLevel world, int radius) {
        long now = world.getDayTime();
        int todayIdx = (int) ((now / 24000) % 7);
        int tomorrowIdx = (todayIdx + 1) % 7;

        Iterator<BiomeInstanceKey> it = activeWeekly.keySet().iterator();
        while (it.hasNext()) {
            BiomeInstanceKey key = it.next();
            BlockPos pos = key.samplePos();

            boolean nearby = world.players().stream()
                    .anyMatch(p -> p.blockPosition().distSqr(pos) <= radius * radius);

            if (!nearby) {
                float[][] week = activeWeekly.get(key);

                float[] today = PressureCurveGenerator.buildDailyCurve(week[todayIdx]);
                float[] tom = PressureCurveGenerator.buildDailyCurve(week[tomorrowIdx]);

                // Save final daily curves
                PressureProfileManager.putDayProfile(key, today);
                PressureProfileManager.putTomorrowProfile(key, tom);

                // Clean memory
                it.remove();
                inactiveWeekly.put(key, week);
                PressureProfileManager.removeWeeklyForecast(key);
                PressureProfileManager.removeDayProfile(key);
                PressureProfileManager.removeTomorrowProfile(key);
            }
        }
    }

    /** Smooth all entries in `activeWeekly` against neighbors */
    private static void diffuseAll() {
        var original = new HashMap<>(activeWeekly);
        long threshold = DIFFUSION_RADIUS * DIFFUSION_RADIUS;

        for (var entry : original.entrySet()) {
            BiomeInstanceKey key = entry.getKey();
            float[][] week = entry.getValue();
            BlockPos pos = key.samplePos();

            Map<BiomeInstanceKey, float[][]> neighbors = new HashMap<>();
            for (var other : original.entrySet()) {
                if (other.getKey().equals(key)) continue;
                if (other.getKey().samplePos().distSqr(pos) <= threshold) {
                    neighbors.put(other.getKey(), other.getValue());
                }
            }

            if (neighbors.isEmpty()) {
                activeWeekly.put(key, week);
                continue;
            }

            float[][] smooth = new float[7][2];
            for (int d = 0; d < 7; d++) {
                for (int i = 0; i < 2; i++) {
                    float val = week[d][i];
                    float sum = 0, count = 0;
                    for (float[][] n : neighbors.values()) {
                        sum += n[d][i];
                        count++;
                    }
                    float avg = sum / count;
                    smooth[d][i] = val + DIFFUSION_RATE * (avg - val);
                }
            }

            activeWeekly.put(key, smooth);
        }
    }


}
