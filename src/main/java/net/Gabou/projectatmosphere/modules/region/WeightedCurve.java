package net.Gabou.projectatmosphere.modules.region;

import java.util.ArrayList;
import java.util.List;

/**
 * Weighted accumulator for simple 2-column weekly curves (min/max per day).
 */
public final class WeightedCurve {
    private static final int DAYS = 7;
    private static final int COLS = 2;
    private final List<Entry> entries = new ArrayList<>();

    public static WeightedCurve empty() {
        return new WeightedCurve();
    }

    public void add(float weight, float[][] curve) {
        if (curve == null || weight <= 0f) {
            return;
        }
        entries.add(new Entry(weight, curve));
    }

    public float[][] normalize() {
        if (entries.isEmpty()) {
            return flat(0f);
        }
        float[][] result = new float[DAYS][COLS];
        float totalWeight = 0f;
        for (Entry entry : entries) {
            totalWeight += entry.weight;
            float[][] curve = entry.curve;
            for (int d = 0; d < DAYS && d < curve.length; d++) {
                float[] row = curve[d];
                if (row == null) {
                    continue;
                }
                for (int c = 0; c < COLS && c < row.length; c++) {
                    result[d][c] += row[c] * entry.weight;
                }
            }
        }
        if (totalWeight <= 0f) {
            return flat(0f);
        }
        for (int d = 0; d < DAYS; d++) {
            for (int c = 0; c < COLS; c++) {
                result[d][c] /= totalWeight;
            }
        }
        return result;
    }

    private static float[][] flat(float value) {
        float[][] arr = new float[DAYS][COLS];
        for (int d = 0; d < DAYS; d++) {
            arr[d][0] = value;
            arr[d][1] = value;
        }
        return arr;
    }

    private record Entry(float weight, float[][] curve) {
    }
}
