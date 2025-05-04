// net/Gabou/projectatmosphere/storm/util/DailyStormGenerator.java
package net.Gabou.projectatmosphere.modules.storm.util;

import net.Gabou.projectatmosphere.modules.storm.spike.StormSpikeManager;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public class DailyStormGenerator {
    public static void scheduleGenerationForTodayAndTomorrow(Level world) {
        AsyncAtmosphereService.runStorm(() -> {
            long now = world.getDayTime();
            for (String key : StormProfileManager.getAllBiomeKeys()) {
                ResourceLocation biome = new ResourceLocation(key);
                boolean hasToday = StormProfileManager.hasDayProfile(biome);
                boolean hasTomorrow = StormProfileManager.hasTomorrowProfile(biome);
                if (hasToday && hasTomorrow) continue;

                double[] week = StormSpikeManager.generateForecastAround(world, null, 0).get(biome);
                int idx = (int)((now/24000L)%7);
                if (!hasToday) {
                    double[] today = buildDailyCurve(week[idx]);
                    StormProfileManager.putDayProfile(biome, today);
                }
                if (!hasTomorrow) {
                    double[] tom = buildDailyCurve(week[(idx+1)%7]);
                    StormProfileManager.putTomorrowProfile(biome, tom);
                }
            }
        });
    }

    private static double[] buildDailyCurve(double spike) {
        // TODO: build 240-step curve peaking at spike
        return new double[240];
    }
}
