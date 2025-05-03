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

public class TemperatureGenerator {

    private static final float IN_MIN = -0.5f;
    private static final float IN_MAX =  2.0f;
    private static final float DEN    = (IN_MAX - IN_MIN);
    private static final float SEA_LEVEL = 63f;
    private static final float LAPSE_RATE = -0.0065f;

    /** Returns the “current” temperature based on the precomputed day profile. */
    public static float getRealTemperature(Level world, ResourceLocation biome, BlockPos pos) {
        long tick = world.getDayTime() % 24000L;
        return TemperatureProfileManager.getCurrentTemperature(biome, tick);
    }

    /** Generates a 7×2 weekly forecast (min at 3 AM, max at 3 PM), with noise & season. */
    public static float[][] generateWeekForecast(Level world, BlockPos chunkPos, ResourceLocation biomeId) {
        float[][] week = new float[7][2];
        long seed = chunkPos.asLong() ^ biomeId.hashCode() ^
                Objects.requireNonNull(world.getServer()).getWorldData().worldGenOptions().seed();
        Random rand = new Random(seed);

        // Base temperature (sea level + altitude + season)
        float baseTemp = SeasonHooks.getBiomeTemperature(world, world.getBiome(chunkPos), chunkPos);
        float seaLevelC = toCelsiusSeaLevel(biomeId, baseTemp);
        float altAdjusted = seaLevelC + (chunkPos.getY() - SEA_LEVEL) * LAPSE_RATE;

        float seasonProgress = SeasonHelper.getSeasonState(world).getSeasonCycleTicks() /
                (float) SeasonHelper.getSeasonState(world).getSeasonDuration();
        float seasonalShift = (float)Math.sin(seasonProgress * 2 * Math.PI) * 10f;
        float seasonallyAdjustedAvg = altAdjusted + seasonalShift;

        boolean isTropical = isTropicalBiome(biomeId, world);

        // Choose daily random amplitude based on biome type
        float randomAmp = isTropical ? 3f : 6f;        // ±3 °C in tropics, ±6 °C elsewhere
        float fluctuationAmp = isTropical ? 1.0f : 2.5f; // spatial/day noise

        BiomeTempConfig.DailyRange clamp = BiomeTempConfig.getClamp(biomeId);

        for (int day = 0; day < 7; day++) {
            // 1) Spatial/day noise
            float mapNoise = getDailyFluctuation(world, chunkPos, fluctuationAmp);
            // 2) Random daily variation
            float randNoise = (rand.nextFloat() * 2f * randomAmp) - randomAmp;
            float dailyBase = seasonallyAdjustedAvg + mapNoise + randNoise;

            // 3) Compute min/max using the precomputed night modifier (which itself uses isTropical internally)
            float minTemp = dailyBase + getNighttimeTempModifier(21000f, biomeId, world);
            float maxTemp = dailyBase + getNighttimeTempModifier(9000f, biomeId, world);

            // 4) Biome-specific clamping
            if (clamp != null) {
                minTemp = Math.max(clamp.minMin(), Math.min(clamp.maxMin(), minTemp));
                maxTemp = Math.max(clamp.minMax(), Math.min(clamp.maxMax(), maxTemp));
            }

            week[day][0] = minTemp;
            week[day][1] = maxTemp;
        }

        return week;
    }

    /*────────────────────────────────────────────────────────────────────────*/

    /** Maps Serene baseTemp (–0.5→2.0) into biome’s sea-level °C range. */
    private static float toCelsiusSeaLevel(ResourceLocation biome, float baseTemp) {
        baseTemp = Math.max(IN_MIN, Math.min(IN_MAX, baseTemp));
        BiomeTempConfig.Range range = BiomeTempConfig.getRange(biome);
        float norm = (baseTemp - IN_MIN) / DEN;
        return range.minC() + norm * (range.maxC() - range.minC());
    }

    /** Applies day/night drop: night=18:00–06:00, cold drop or minor in tropics. */
    private static float getNighttimeTempModifier(float timeOfDay, ResourceLocation biome, Level world) {
        boolean isTropical = isTropicalBiome(biome, world);
        // Night = [12000..23999] & [0..5999]
        if (timeOfDay >= 12000f || timeOfDay < 6000f) {
            return isTropical ? -1.0f : -3.5f;
        }
        return 0f;
    }

    /** True if biome belongs to Sereneseasons tropical tag. */
    public static boolean isTropicalBiome(ResourceLocation biomeId, Level level) {
        Registry<Biome> reg = level.registryAccess().registryOrThrow(Registries.BIOME);
        Biome b = reg.get(biomeId);
        if (b == null) return false;
        return reg.getResourceKey(b)
                .flatMap(reg::getHolder)
                .map(holder -> holder.is(ModTags.Biomes.TROPICAL_BIOMES))
                .orElse(false);
    }

    /** Spatial daily fluctuation based on chunk and day: ±maxFluctuation. */
    private static float getDailyFluctuation(Level world, BlockPos pos, float maxFluctuation) {
        long day = world.getDayTime() / 24000L;
        int hash = Objects.hash(pos.getX() >> 4, pos.getZ() >> 4, day);
        return ((hash % 200) - 100) / 100f * maxFluctuation;
    }
}
