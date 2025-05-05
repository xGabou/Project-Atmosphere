// src/main/java/net/Gabou/projectatmosphere/modules/pressure/util/PressureCurveGenerator.java
package net.Gabou.projectatmosphere.modules.pressure.util;

/**
 * Builds smooth, 240-step daily curves from a [min,max] range.
 */
public class PressureCurveGenerator {
    public static float[] buildDailyCurve(float[] dailyRange) {
        float min = dailyRange[0], max = dailyRange[1];
        float[] curve = new float[240];
        for (int i = 0; i < 240; i++) {
            float θ = (i / 239.0f) * (float) Math.PI;  // 3 AM→0, 3 PM→π
            curve[i] = min + (max - min) * (1 - (float)Math.cos(θ)) * 0.5f;
        }
        return curve;
    }
}
