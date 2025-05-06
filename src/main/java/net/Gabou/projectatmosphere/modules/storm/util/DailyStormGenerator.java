// net/Gabou/projectatmosphere/storm/util/DailyStormGenerator.java
package net.Gabou.projectatmosphere.modules.storm.util;

import net.Gabou.projectatmosphere.modules.storm.spike.StormSpikeManager;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import static net.Gabou.projectatmosphere.ProjectAtmosphere.DEFAULT_RADIUS;

public class DailyStormGenerator {
    public static void scheduleGenerationForTodayAndTomorrow(Level world) {
        AsyncAtmosphereService.runStorm(() -> {
            long now = world.getDayTime();
            for (BiomeInstanceKey key : StormProfileManager.getAllBiomeKeys()) {
                boolean hasToday = StormProfileManager.hasDayProfile(key);
                boolean hasTomorrow = StormProfileManager.hasTomorrowProfile(key);
                if (hasToday && hasTomorrow) continue;

                float[] week = StormProfileManager.getWeeklyForecast(key);
                int idx = (int)((now/24000L)%7);
                if (!hasToday) {
                    float[] today = buildDailyCurve(week[idx]);
                    StormProfileManager.putDayProfile(key, today);
                }
                if (!hasTomorrow) {
                    float[] tom = buildDailyCurve(week[(idx+1)%7]);
                    StormProfileManager.putTomorrowProfile(key, tom);
                }
            }
        });
    }

    private static float[] buildDailyCurve(float spike) {
        float[] curve = new float[240];
        int center = 120; // noon
        float spread = 50f; // controls curve width

        for (int i = 0; i < 240; i++) {
            float x = i - center;
            float gauss = (float) Math.exp(-x * x / (2 * spread * spread)); // normalized Gaussian
            curve[i] = gauss * spike;
        }

        return curve;
    }

}
