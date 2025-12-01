package net.Gabou.projectatmosphere.modules.temperature.util;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.util.AsyncAtmosphereService;
import net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.Gabou.projectatmosphere.modules.temperature.config.BiomeTempConfig.Season;
import net.Gabou.projectatmosphere.seasons.SeasonStage;
import net.Gabou.projectatmosphere.seasons.SeasonTimeHelper;

import java.util.Objects;
import java.util.Random;

public class TemperatureGenerator {

    public static final float BOUND_TEMP = 65f;
    private static final float IN_MIN = -0.5f;
    private static final float IN_MAX =  2.0f;
    private static final float DEN    = (IN_MAX - IN_MIN);
    private static final float LAPSE_RATE = -0.0065f;
    static final float SEA_LEVEL = AsyncAtmosphereService.callOnMainThread(ProjectAtmosphere::getSeaLevel);

    /**
     * Generates a 7×2 weekly forecast (min at 3 AM, max at 3 PM),
     * incorporating random noise and seasonal effects.
     *
     * @param level    the level to sample
     * @param chunkPos the chunk position used as a seed
     * @param biomeId  the biome identifier
     * @return a 7×2 array where each day contains [min, max] temperatures
     */
    public static float[][] generateWeekForecast(ServerLevel level, BlockPos chunkPos, ResourceLocation biomeId) {
        // Step 1: Grab world-dependent values safely
        ForecastBaseData base = AsyncAtmosphereService.callOnMainThread(() -> {
            int cycleTicks = (int) SeasonTimeHelper.seasonCycleTicks(level);
            long seasonDuration = SeasonTimeHelper.seasonDuration(level);
            long dayDuration = SeasonTimeHelper.dayDuration(level);
            SeasonStage stage = SeasonTimeHelper.stage(level);
            Season currentSeason = mapSeasonStage(stage);

            float baseTemp = level.getBiome(chunkPos).value().getBaseTemperature();
            BiomeTempConfig.DailyRange clamp = BiomeTempConfig.getClamp(biomeId, currentSeason);

            return new ForecastBaseData(cycleTicks, seasonDuration, dayDuration, currentSeason, baseTemp, clamp);
        });

        // Step 2: Math-heavy async work
        float[][] week = new float[7][2];
        long seed = chunkPos.asLong() ^ biomeId.hashCode() ^ ProjectAtmosphere.seed;
        Random rand = new Random(seed);

        float seaLevelC = toCelsiusSeaLevel(biomeId, base.baseTemp, base.currentSeason);
        float altitudeBase = seaLevelC + (chunkPos.getY() - SEA_LEVEL) * LAPSE_RATE;

        float randomAmp = isTropicalBiome(biomeId, level, 4f, 8f);
        float fluctuationAmp = isTropicalBiome(biomeId, level, 2f, 4f);

        for (int day = 0; day < 7; day++) {
            float prog = (base.cycleTicks + day * base.dayDuration) / (float) base.seasonDuration;
            float seasonalShift = (float) Math.sin(prog * 2 * Math.PI) * -10f;
            float dailyMean = altitudeBase + seasonalShift;

            float mapNoise = getDailyFluctuation(level, chunkPos, fluctuationAmp);
            float randNoise = (rand.nextFloat() * 2f * randomAmp) - randomAmp;
            float dailyBase = dailyMean + mapNoise + randNoise;

            float[] sampleTicks = {21000f, 6000f, 9000f, 12000f, 18000f};
            float dayMin = Float.POSITIVE_INFINITY;
            float dayMax = Float.NEGATIVE_INFINITY;

            for (float t : sampleTicks) {
                float modifier = getNighttimeTempModifier(t, biomeId, level);
                float temp = dailyBase + modifier;
                dayMin = Math.min(dayMin, temp);
                dayMax = Math.max(dayMax, temp);
            }

            if (base.clamp != null) {
                float easedMin = easeTowardAverage(dayMin, base.clamp.avgNight());
                dayMin = (easedMin < base.clamp.minMin() || easedMin > base.clamp.maxMax())
                        ? ultimateSmoother(easedMin, base.clamp.minMin(), base.clamp.maxMax())
                        : easedMin;

                float easedMax = easeTowardAverage(dayMax, base.clamp.avgDay());
                dayMax = (easedMax < base.clamp.minMin() || easedMax > base.clamp.maxMax())
                        ? ultimateSmoother(easedMax, base.clamp.minMin(), base.clamp.maxMax())
                        : easedMax;
            }

            week[day][0] = dayMin;
            week[day][1] = dayMax;
        }

        return week;
    }

    // Small record to carry main-thread values
    private record ForecastBaseData(
            int cycleTicks,
            long seasonDuration,
            long dayDuration,
            Season currentSeason,
            float baseTemp,
            BiomeTempConfig.DailyRange clamp
    ) {}

    /**
     * If {@code v} lies outside {@code [boundLow..boundHigh]}, then:
     * <ol>
     *   <li>globally cap to ±65 °C,</li>
     *   <li>credit back a scaled portion of the overshoot,</li>
     *   <li>ease the value toward the nearest bound using average-based easing,</li>
     *   <li>snap within a 2 °C buffer if still out-of-bounds.</li>
     * </ol>
     *
     * @param v        the value to smooth
     * @param boundLow the lower temperature bound
     * @param boundHigh the upper temperature bound
     * @return the smoothed value within or near the bounds
     */
    private static float ultimateSmoother(float v, float boundLow, float boundHigh) {
        float sign = Math.signum(v);

        
        float capped = sign * Math.min(BOUND_TEMP, Math.abs(v));

        
        float credit = computeCredit(v) * sign;
        float postCap = capped + credit;

        
        float boundMid = (boundLow + boundHigh) / 2f;
        float eased = easeTowardAverage(postCap, boundMid);

        
        if (eased < boundLow)  return boundLow + 2f;
        if (eased > boundHigh) return boundHigh - 2f;

        return eased;
    }
    /**
     * Computes the credit applied back after capping a value that exceeds the
     * temperature bounds.
     *
     * @param value the original temperature value
     * @return the credit to add after the cap
     */
    private static float computeCredit(float value) {
        float abs = Math.abs(value);
        float overshoot = abs - BOUND_TEMP;
        if (overshoot <= 0f) return 0f;


        float credit = (float) (Math.log(overshoot + 1) / Math.log(2.5)) + 0.5f;


        return Math.min(credit * 4, 10f);
    }


    /**
     * Eases {@code v} toward {@code avg} in four bands:
     * <ul>
     *   <li>|Δ| ≤ 3°C  → 10% pull</li>
     *   <li>|Δ| ≤ 6°C  → 35% pull</li>
     *   <li>|Δ| ≤ 10°C → 65% pull</li>
     *   <li>|Δ| > 10°C → 100% pull</li>
     * </ul>
     * Does not enforce any bounds—it simply returns an eased value:
     * {@code eased = avg + (v - avg) * (1 - factor)}.
     *
     * @param v   the value to ease
     * @param avg the target average
     * @return the value eased toward the average
     */
    private static float easeTowardAverage(float v, float avg) {
        float diff = v - avg;
        float absDiff = Math.abs(diff);

        
        float strength = (float)(1.0 - Math.exp(-absDiff / 8.0)); 
        strength = Math.min(strength, 0.7f);  

        return avg + diff * (1.0f - strength);
    }



    

    /**
     * Maps the Serene Seasons base temperature (–0.5→2.0) into the biome's
     * sea-level Celsius range.
     *
     * @param biome    the biome identifier
     * @param baseTemp the Serene Seasons base temperature
     * @param season   the current season
     * @return the mapped sea-level temperature in degrees Celsius
     */
    private static float toCelsiusSeaLevel(ResourceLocation biome, float baseTemp, Season season) {
        baseTemp = Math.max(IN_MIN, Math.min(IN_MAX, baseTemp));
        BiomeTempConfig.Range range = BiomeTempConfig.getRange(biome,season);
        float norm = (baseTemp - IN_MIN) / DEN;
        return range.minC() + norm * (range.maxC() - range.minC());
    }

    /**
     * Applies a day or night temperature modifier based on the time of day and
     * biome.
     *
     * @param timeOfDay the time of day in ticks
     * @param biome     the biome identifier
     * @param world     the level being sampled
     * @return the modifier to apply to the base temperature
     */
    private static float getNighttimeTempModifier(float timeOfDay, ResourceLocation biome, Level world) {
        
        if (timeOfDay >= 12000f || timeOfDay < 6000f) {
            return isTropicalBiome(biome,world,-2.0f, -4f);
        }
        else{
            return isTropicalBiome(biome,world,2.0f, 4f);
        }
    }

    /**
     * Chooses a temperature amplitude based on whether the biome belongs to the
     * Sereneseasons tropical tag.
     *
     * @param biomeId        the biome identifier
     * @param level          the level containing the biome
     * @param tropicalAmp    amplitude to use for tropical biomes
     * @param nonTropicalAmp amplitude to use for non-tropical biomes
     * @return the selected amplitude based on biome type
     */
    public static float isTropicalBiome(ResourceLocation biomeId, Level level, float tropicalAmp, float nonTropicalAmp) {
        Registry<Biome> reg = level.registryAccess().registryOrThrow(Registries.BIOME);
        Biome b = reg.get(biomeId);
        if (b == null) return nonTropicalAmp;
        float baseTemp = b.getBaseTemperature();
        return baseTemp >= 1.2f ? tropicalAmp : nonTropicalAmp;
    }

    /**
     * Computes a spatial daily fluctuation value based on chunk position and day.
     *
     * @param world          the level used for the day time
     * @param pos            the block position serving as seed
     * @param maxFluctuation the maximum fluctuation amplitude
     * @return the fluctuation value in degrees Celsius
     */
    private static float getDailyFluctuation(Level world, BlockPos pos, float maxFluctuation) {
        long day = world.getDayTime() / 24000L;
        int hash = Objects.hash(pos.getX() >> 4, pos.getZ() >> 4, day);
        return ((hash % 200) - 100) / 100f * maxFluctuation;
    }

    private static Season mapSeasonStage(SeasonStage stage) {
        return switch (stage) {
            case WINTER -> Season.WINTER;
            case AUTUMN -> Season.AUTUMN;
            case SPRING -> Season.SPRING;
            default -> Season.SUMMER;
        };
    }
}
