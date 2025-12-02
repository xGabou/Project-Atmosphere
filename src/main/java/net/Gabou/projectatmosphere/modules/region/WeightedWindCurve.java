package net.Gabou.projectatmosphere.modules.region;

import java.util.ArrayList;
import java.util.List;
import net.Gabou.projectatmosphere.modules.core.WindVector;

/**
 * Weighted accumulator for weekly wind vectors.
 */
public final class WeightedWindCurve {
    private static final int DAYS = 7;
    private final List<Entry> entries = new ArrayList<>();

    public static WeightedWindCurve empty() {
        return new WeightedWindCurve();
    }

    public void add(float weight, WindVector[] curve) {
        if (curve == null || weight <= 0f) {
            return;
        }
        entries.add(new Entry(weight, curve));
    }

    public WindVector[] normalize() {
        WindVector[] result = new WindVector[DAYS];
        if (entries.isEmpty()) {
            for (int i = 0; i < DAYS; i++) {
                result[i] = WindVector.fromBase(0f, 0f);
            }
            return result;
        }
        for (int day = 0; day < DAYS; day++) {
            float sumX = 0f;
            float sumZ = 0f;
            float sumGust = 0f;
            float weightSum = 0f;
            for (Entry entry : entries) {
                WindVector[] curve = entry.curve();
                if (curve.length <= day || curve[day] == null) {
                    continue;
                }
                WindVector wind = curve[day];
                float angle = wind.angleRadians();
                float speed = wind.baseSpeed();
                sumX += speed * (float) Math.cos(angle) * entry.weight();
                sumZ += speed * (float) Math.sin(angle) * entry.weight();
                sumGust += wind.gustSpeed() * entry.weight();
                weightSum += entry.weight();
            }
            if (weightSum <= 0f) {
                result[day] = WindVector.fromBase(0f, 0f);
            } else {
                float avgX = sumX / weightSum;
                float avgZ = sumZ / weightSum;
                float avgSpeed = (float) Math.sqrt(avgX * avgX + avgZ * avgZ);
                float avgAngle = (float) Math.atan2(avgZ, avgX);
                float avgGust = sumGust / weightSum;
                result[day] = new WindVector(avgSpeed, avgAngle, avgGust);
            }
        }
        return result;
    }

    private record Entry(float weight, WindVector[] curve) {
    }
}
