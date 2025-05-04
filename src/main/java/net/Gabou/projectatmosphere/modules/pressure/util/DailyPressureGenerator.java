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
                boolean hasToday = PressureProfileManager.getTodayProfile(biome) != null;
                boolean hasTom  = PressureProfileManager.getTomorrowProfile(biome) != null;
                if (hasToday && hasTom) continue;

                float[][] week = PressureProfileManager.getWeeklyForecast(biome);
                if (week == null) continue;

                if (!hasToday) {
                    float[] todayCurve = buildDailyCurve(week[(int)((now/24000)%7)]);
                    PressureProfileManager.putDayProfile(biome, todayCurve);
                }
                if (!hasTom) {
                    float[] tomCurve = buildDailyCurve(week[(int)(((now+24000)/24000)%7)]);
                    PressureProfileManager.putTomorrowProfile(biome, tomCurve);
                }
            }
        });
    }

    private static float[] buildDailyCurve(float[] dailyRange) {
        float minP = dailyRange[0];
        float maxP = dailyRange[1];
        float[] curve = new float[240];
        for (int i = 0; i < 240; i++) {
            float θ = (float) ((i / 239.0f) * Math.PI); // 3 AM→0, 3 PM→π
            curve[i] = (float) (minP + (maxP - minP) * (1 - Math.cos(θ)) * 0.5);
        }
        return curve;
    }
}
