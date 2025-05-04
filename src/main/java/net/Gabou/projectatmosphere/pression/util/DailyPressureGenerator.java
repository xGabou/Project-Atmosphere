package net.Gabou.projectatmosphere.pression.util;

import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public class DailyPressureGenerator {
    public static void scheduleGenerationForTodayAndTomorrow(Level world) {
        AsyncAtmosphereService.runAsync(AsyncAtmosphereService.Branch.PRESSURE,() -> {
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
        // now you have both endpoints and can e.g. do a cosine interpolation:
        double[] curve = new double[240];
        for (int i = 0; i < 240; i++) {
            // simple sine wave: low at 3 AM, high at 3 PM, amplitude ±5 hPa
            double θ = (i / 239.0) * Math.PI;
            curve[i] = minP + (maxP - minP) * (1 - Math.cos(θ)) * 0.5;
        }
        return curve;
    }
}

