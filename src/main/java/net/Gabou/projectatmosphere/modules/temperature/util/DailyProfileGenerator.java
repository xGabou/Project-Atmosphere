package net.Gabou.projectatmosphere.modules.temperature.util;

import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public class DailyProfileGenerator {

    //    public static void scheduleGenerationForAllBiomes(Level world) {
//        AsyncTemperatureService.runAsync(() -> {
//            long worldTick = world.getDayTime();
//            for (String key : ForecastStorageManager.getAllBiomeKeys()) {
//                ResourceLocation biome = new ResourceLocation(key);
//                generateDayProfile(biome, world, worldTick);
//            }
//        });
//    }
    public static void scheduleGenerationForTodayAndTomorrow(Level world) {
        AsyncAtmosphereService.runTemperature(() -> {
            long now = world.getDayTime();

            for (BiomeInstanceKey key : ForecastStorageManager.getAllBiomeKeys()) {
                ResourceLocation biome = key.biomeType();
                BlockPos pos = key.samplePos();

                boolean hasToday = TemperatureProfileManager.hasDayProfile(key);
                boolean hasTomorrow = TemperatureProfileManager.hasTomorrowProfile(key);

                if (hasToday && hasTomorrow)
                    continue; // Nothing to generate

                float[] today = hasToday ? null : generateDayProfile(key, world, now);
                float[] tomorrow = hasTomorrow ? null : generateDayProfile(key, world, now + 24000L);

                if (!hasToday) {
                    if (today == null)
                        throw new RuntimeException("Failed to generate today's profile for " + biome + " at tick "+now +" with coords : " + pos);
                    TemperatureProfileManager.putDayProfile(key, today);
                }

                if (!hasTomorrow) {
                    if (tomorrow == null)
                        throw new RuntimeException("Failed to generate tomorrow's profile for " + biome + " at tick " + now+" with coords : " + pos);
                    TemperatureProfileManager.putTomorrowProfile(key, tomorrow);
                }
            }
        });
    }


    private static float[] generateDayProfile(
            BiomeInstanceKey biome, Level world, long worldTick) {

        float[][] week = ForecastStorageManager.getForecast(biome);
        if (week == null) return null;

        int todayIndex = (int) ((worldTick / 24000L) % 7);
        float min = week[todayIndex][0], max = week[todayIndex][1];
        float[] dayProfile = new float[240];

        // Define “3 AM” as our zero point, “3 PM” (9000 ticks) as peak
        long dayStart = worldTick - (worldTick % 24000L);
        for (int i = 0; i < 240; i++) {
            long sampleTick = (dayStart + i * 100L) % 24000L;
            // shift so that 3 AM→0, 3 PM→π
            long shifted = (sampleTick + 3000L) % 24000L;
            float theta = (shifted / 12000f) * (float) Math.PI;
            float factor = (1 - (float) Math.cos(theta)) * 0.5f;
            dayProfile[i] = min + (max - min) * factor;
        }
        return dayProfile;
    }
}
