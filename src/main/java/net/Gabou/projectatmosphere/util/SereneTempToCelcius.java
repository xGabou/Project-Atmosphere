package net.Gabou.projectatmosphere.util;

public class SereneTempToCelcius {
    private final static float maxTemp = 56f;   // Desert
    private final static float minTemp = -20f;  // Iceberg

    public static float SereneTempToCelcius(float sereneTemp) {
        // Fixing the mapping to ensure correct output temperature
        return (float) mapToTemperature(sereneTemp, -0.5f, 2.0f, minTemp, maxTemp);
    }

    public static double mapToTemperature(float input, float inputMin, float inputMax, float outputMin, float outputMax) {
        // Clamp input to stay within range (for safety)
        input = Math.max(inputMin, Math.min(inputMax, input));

        // Linearly interpolate the input to the output range
        return outputMin + (input - inputMin) * (outputMax - outputMin) / (inputMax - inputMin);
    }
}