package net.Gabou.projectatmosphere.temperature.util;

import net.Gabou.projectatmosphere.temperature.config.BiomeTempConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.init.ModTags;
import sereneseasons.season.SeasonHooks;

import java.util.Objects;
import java.util.Random;

public class TemperatureUtils {

    private static final float IN_MIN = -0.5f;
    private static final float IN_MAX =  2.0f;
    private static final float DEN    = (IN_MAX - IN_MIN);
    private static final float SEA_LEVEL = 63f;
    private static final float LAPSE_RATE = -0.0065f;

    public static float getRealTemperature(ResourceLocation biomeKey, float baseTemp, BlockPos pos, Level world) {
        float seaLevelC = toCelsiusSeaLevel(biomeKey, baseTemp);
        float temp = seaLevelC + (pos.getY() - SEA_LEVEL) * LAPSE_RATE;
        float timeOfDay = world.getDayTime() % 24000L;
        float nightFactor = getNighttimeTempModifier(timeOfDay, biomeKey, world);
        float noise = getDailyFluctuation(world, pos, 6.0f);
        return temp + nightFactor + noise;
    }

    private static float toCelsiusSeaLevel(ResourceLocation biome, float baseTemp) {
        if (baseTemp < IN_MIN) baseTemp = IN_MIN;
        else if (baseTemp > IN_MAX) baseTemp = IN_MAX;

        BiomeTempConfig.Range range = BiomeTempConfig.getRange(biome);
        float norm = (baseTemp - IN_MIN) / DEN;
        return range.minC() + norm * (range.maxC() - range.minC());
    }

    private static float getNighttimeTempModifier(float timeOfDay, ResourceLocation biome, Level world) {
        boolean isTropical = isTropicalBiome(biome, world);

        // Minecraft night runs from 18:00 (12000) to 6:00 (0), i.e., 12000–23999 and 0–5999
        if (timeOfDay >= 12000 || timeOfDay < 6000) {
            return isTropical ? -1.0f : -3.5f;
        }

        return 0f;
    }


    public static boolean isTropicalBiome(ResourceLocation biomeId, Level level) {
        Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
        Biome biome = biomeRegistry.get(biomeId);
        if (biome == null) return false;
        return biomeRegistry.getResourceKey(biome)
                .flatMap(biomeRegistry::getHolder)
                .map(holder -> holder.is(ModTags.Biomes.TROPICAL_BIOMES))
                .orElse(false);
    }

    private static float getDailyFluctuation(Level world, BlockPos pos, float maxFluctuation) {
        long day = world.getDayTime() / 24000L;
        int hash = Objects.hash(pos.getX() >> 4, pos.getZ() >> 4, day);
        return (hash % 200 - 100) / 100f * maxFluctuation;
    }

    public static float[][] generateWeekForecast(Level world, BlockPos chunkPos, ResourceLocation biomeId) {
        float[][] week = new float[7][2]; // [day][0] = min (3am), [1] = max (3pm)

        Random rand = new Random(chunkPos.asLong() ^ biomeId.hashCode() ^ Objects.requireNonNull(world.getServer()).getWorldData().worldGenOptions().seed());

        float baseTemp = SeasonHooks.getBiomeTemperature(world, world.getBiome(chunkPos), chunkPos);
        float avg = toCelsiusSeaLevel(biomeId, baseTemp);

        // --- Season macro-trend ---
        float seasonProgress = SeasonHelper.getSeasonState(world).getSeasonCycleTicks() / (float) SeasonHelper.getSeasonState(world).getSeasonDuration(); // [0, 1]
        float seasonalAmplitude = 10f; // ±10°C seasonal swing
        float seasonalBias = (float) Math.sin(seasonProgress * Math.PI * 2); // one full wave per year
        float seasonalShift = seasonalBias * seasonalAmplitude;
        float seasonallyAdjustedAvg = avg + seasonalShift;

        boolean isTropical = isTropicalBiome(biomeId, world);
        BiomeTempConfig.DailyRange clamp = BiomeTempConfig.getClamp(biomeId);

        for (int i = 0; i < 7; i++) {
            // Daily fluctuation ±6°C around the seasonal average
            float dailyNoise = (rand.nextFloat() * 12f) - 6f;
            float dailyBase = seasonallyAdjustedAvg + dailyNoise;

            // Day/night delta (tropics swing less)
            float delta = isTropical ? 3f : 7f;
            float minTemp = dailyBase - delta / 2f; // 3am
            float maxTemp = dailyBase + delta / 2f; // 3pm

            if (clamp != null) {
                minTemp = Math.max(clamp.minMin(), Math.min(clamp.maxMin(), minTemp));
                maxTemp = Math.max(clamp.minMax(), Math.min(clamp.maxMax(), maxTemp));
            }

            week[i][0] = minTemp;
            week[i][1] = maxTemp;
        }

        return week;
    }

}
