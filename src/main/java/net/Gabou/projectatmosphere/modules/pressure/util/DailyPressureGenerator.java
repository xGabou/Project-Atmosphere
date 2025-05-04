// src/main/java/net/Gabou/projectatmosphere/modules/pressure/util/DailyPressureGenerator.java
package net.Gabou.projectatmosphere.modules.pressure.util;

import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.Gabou.projectatmosphere.modules.pressure.util.PressureProfileManager;

public class DailyPressureGenerator {
    public static void scheduleGenerationForTodayAndTomorrow(Level world) {
        AsyncAtmosphereService.runPression(() -> {
            long now = world.getDayTime();
            for (String key : PressureProfileManager.getAllBiomeKeys()) {
                ResourceLocation biome = new ResourceLocation(key);
                boolean hasToday = PressureProfileManager.getDayProfile(biome) != null;
                boolean hasTom  = PressureProfileManager.getTomorrowProfile(biome) != null;
                if (hasToday && hasTom) continue;

                double[][] week = PressureProfileManager.getWeeklyForecast(biome);
                if (week == null) continue;

                if (!hasToday) {
                    double[] todayCurve = buildDailyCurve(week[(int)((now/24000)%7)]);
                    PressureProfileManager.putDayProfile(biome, todayCurve);
                }
                if (!hasTom) {
                    double[] tomCurve = buildDailyCurve(week[(int)(((now+24000)/24000)%7)]);
                    PressureProfileManager.putTomorrowProfile(biome, tomCurve);
                }
            }
        });
    }

    private static double[] buildDailyCurve(double[] dailyRange) {
        double minP = dailyRange[0];
        double maxP = dailyRange[1];
        double[] curve = new double[240];
        for (int i = 0; i < 240; i++) {
            double θ = (i / 239.0) * Math.PI; // 3 AM→0, 3 PM→π
            curve[i] = minP + (maxP - minP) * (1 - Math.cos(θ)) * 0.5;
        }
        return curve;
    }
}
