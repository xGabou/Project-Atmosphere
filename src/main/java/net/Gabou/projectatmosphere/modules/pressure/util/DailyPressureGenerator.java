// src/main/java/net/Gabou/projectatmosphere/modules/pressure/util/DailyPressureGenerator.java
package net.Gabou.projectatmosphere.modules.pressure.util;

import net.Gabou.projectatmosphere.modules.pressure.forecast.PressureForecast;
import net.Gabou.projectatmosphere.modules.pressure.util.PressureProfileManager;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Builds 240-step daily curves from weekly [min,max] ranges.
 */
public class DailyPressureGenerator {
    public static void scheduleGenerationForTodayAndTomorrow(Level world) {
            long now = world.getDayTime();
            for (var key : PressureProfileManager.getAllBiomeKeys()) {
                boolean hasToday = PressureProfileManager.getTodayProfile(key) != null;
                boolean hasTom   = PressureProfileManager.getTomorrowProfile(key) != null;
                if (hasToday && hasTom) continue;

                float[][] week = PressureProfileManager.getWeeklyForecast(key);
                if (week == null) continue;

                if (!hasToday) {
                    float[] todayCurve = PressureCurveGenerator.buildDailyCurve(
                            week[(int)((now / 24000) % 7)]
                    );
                    PressureProfileManager.putDayProfile(key, todayCurve);
                }
                if (!hasTom) {
                    float[] tomCurve = PressureCurveGenerator.buildDailyCurve(
                            week[(int)(((now + 24000) / 24000) % 7)]
                    );
                    PressureProfileManager.putTomorrowProfile(key, tomCurve);
                }
            }
    }
}
