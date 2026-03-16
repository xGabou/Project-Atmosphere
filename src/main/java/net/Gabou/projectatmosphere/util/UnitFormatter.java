package net.Gabou.projectatmosphere.util;

import net.Gabou.projectatmosphere.config.AtmoCommonConfig;

import java.util.Locale;

public final class UnitFormatter {

    private UnitFormatter() {}

    public static boolean imperial() {
        return AtmoCommonConfig.DISPLAY_UNITS_IMPERIAL.get();
    }

    public static String formatTemperature(float celsius) {
        if (imperial()) {
            float f = celsius * 9f / 5f + 32f;
            return String.format(Locale.US, "%.1f°F", f);
        }
        return String.format(Locale.US, "%.1f°C", celsius);
    }

    public static String formatWindSpeed(float metersPerSecond) {
        if (imperial()) {
            float mph = metersPerSecond * 2.2369363f;
            return String.format(Locale.US, "%.1f mph", mph);
        }
        return String.format(Locale.US, "%.1f m/s", metersPerSecond);
    }

    public static String formatPressure(double hPa) {
        if (imperial()) {
            double inHg = hPa * 0.0295299830714d;
            return String.format(Locale.US, "%.2f inHg", inHg);
        }
        return String.format(Locale.US, "%.1f hPa", hPa);
    }

    public static String formatHumidity(float percent) {
        float clamped = HumidityGuard.clampPercent(
                percent,
                0f,
                "UnitFormatter.formatHumidity",
                null,
                null,
                null,
                null
        );
        return String.format(Locale.US, "%.1f%%", clamped);
    }
}
