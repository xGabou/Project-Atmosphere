package net.Gabou.projectatmosphere.modules.region;

final class RegionForecastCorruptionValidator {
    private static final float CORRUPTION_TEMP_MIN = -90f;
    private static final float CORRUPTION_TEMP_MAX = 70f;
    private static final float CORRUPTION_HUMIDITY_MIN = 0f;
    private static final float CORRUPTION_HUMIDITY_MAX = 100f;
    private static final float CORRUPTION_PRESSURE_MIN = 880f;
    private static final float CORRUPTION_PRESSURE_MAX = 1085f;

    private RegionForecastCorruptionValidator() {
    }

    static CorruptionReport detect(ForecastRegion region) {
        if (region == null) {
            return new CorruptionReport(false, "");
        }
        boolean tempCorrupt = usesOnlyExtremes(region.getTemperature(), CORRUPTION_TEMP_MIN, CORRUPTION_TEMP_MAX);
        boolean humidityCorrupt = usesOnlyExtremes(region.getHumidity(), CORRUPTION_HUMIDITY_MIN, CORRUPTION_HUMIDITY_MAX);
        boolean pressureCorrupt = usesOnlyExtremes(region.getPressure(), CORRUPTION_PRESSURE_MIN, CORRUPTION_PRESSURE_MAX);

        if (!tempCorrupt && !humidityCorrupt && !pressureCorrupt) {
            return new CorruptionReport(false, "");
        }
        StringBuilder message = new StringBuilder();
        if (tempCorrupt) {
            message.append("temperature");
        }
        if (humidityCorrupt) {
            if (message.length() > 0) {
                message.append(", ");
            }
            message.append("humidity");
        }
        if (pressureCorrupt) {
            if (message.length() > 0) {
                message.append(", ");
            }
            message.append("pressure");
        }
        return new CorruptionReport(true, message.toString());
    }

    private static boolean usesOnlyExtremes(float[][] curve, float minValue, float maxValue) {
        if (curve == null || curve.length == 0) {
            return false;
        }
        boolean sawValue = false;
        for (float[] row : curve) {
            if (row == null) {
                continue;
            }
            for (float value : row) {
                if (!Float.isFinite(value)) {
                    continue;
                }
                sawValue = true;
                if (value != minValue && value != maxValue) {
                    return false;
                }
            }
        }
        return sawValue;
    }

    record CorruptionReport(boolean corrupted, String message) {
    }
}
