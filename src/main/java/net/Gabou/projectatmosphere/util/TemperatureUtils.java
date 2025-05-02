package net.Gabou.projectatmosphere.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
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
        if (timeOfDay >= 13000 && timeOfDay <= 23000) {
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

    public static float[] generateWeekForecast(Level world, BlockPos chunkPos, ResourceLocation biome) {
        float[] week = new float[7];
        Random rand = new Random(chunkPos.asLong() ^ biome.hashCode() ^ Objects.requireNonNull(world.getServer()).getWorldData().worldGenOptions().seed());

        float baseTemp = SeasonHooks.getBiomeTemperature(world, world.getBiome(chunkPos), chunkPos);
        float avg = getRealTemperature(biome, baseTemp, chunkPos, world);

        for (int i = 0; i < 7; i++) {
            float fluctuation = (rand.nextFloat() * 12f) - 6f;
            week[i] = avg + fluctuation;
        }

        return week;
    }
}
