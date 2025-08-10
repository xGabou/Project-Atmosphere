package net.Gabou.projectatmosphere.modules.temperature.compat;

public class SereneTempToCelcius {
    private final static float maxTemp = 56f;   
    private final static float minTemp = -20f;  

    public static float SereneTempToCelcius(float sereneTemp) {
        
        return (float) mapToTemperature(sereneTemp, -0.5f, 2.0f, minTemp, maxTemp);
    }

    public static double mapToTemperature(float input, float inputMin, float inputMax, float outputMin, float outputMax) {
        
        input = Math.max(inputMin, Math.min(inputMax, input));

        
        return outputMin + (input - inputMin) * (outputMax - outputMin) / (inputMax - inputMin);
    }
}
