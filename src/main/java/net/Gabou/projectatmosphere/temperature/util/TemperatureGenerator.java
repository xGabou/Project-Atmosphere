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
import sereneseasons.season.SeasonTime;
import net.Gabou.projectatmosphere.temperature.config.BiomeTempConfig.Season;


import java.util.Objects;
import java.util.Random;

public class TemperatureGenerator {

    public static final float BOUND_TEMP = 65f;
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



        // look up the current Sereneseasons primary season
        int cycleTicks = SeasonHelper.getSeasonState(world).getSeasonCycleTicks();
        long seasonDuration = SeasonHelper.getSeasonState(world).getSeasonDuration();
        long dayDuration    = SeasonHelper.getSeasonState(world).getDayDuration();
        SeasonTime st = new SeasonTime(cycleTicks);
        Season currentSeason = Season.valueOf(st.getSeason().name());


        // Base temperature (sea level + altitude + season)
        float baseTemp = SeasonHooks.getBiomeTemperature(world, world.getBiome(chunkPos), chunkPos);
        float seaLevelC = toCelsiusSeaLevel(biomeId, baseTemp, currentSeason);
        float altitudeBase = seaLevelC + (chunkPos.getY() - SEA_LEVEL) * LAPSE_RATE;



        float randomAmp = isTropicalBiome(biomeId,world,4f,8f);        // ±3 °C in tropics, ±6 °C elsewhere
        float fluctuationAmp = isTropicalBiome(biomeId,world,2f,4f); // spatial/day noise

        BiomeTempConfig.DailyRange clamp = BiomeTempConfig.getClamp(biomeId, currentSeason);

        for (int day = 0; day < 7; day++) {


            float prog = (cycleTicks + day * dayDuration) / (float) seasonDuration;
            float seasonalShift = (float)Math.sin(prog * 2 * Math.PI) * -10f;
            float dailyMean = altitudeBase + seasonalShift;

            // 1) Spatial/day noise
            float mapNoise = getDailyFluctuation(world, chunkPos, fluctuationAmp);
            // 2) Random daily variation
            float randNoise = (rand.nextFloat() * 2f * randomAmp) - randomAmp;
            float dailyBase   = dailyMean + mapNoise + randNoise;

            // times in ticks: dawn(6000), noon(9000), dusk(12000), midnight(21000), etc.
            float[] sampleTicks = { 21000f, 6000f, 9000f, 12000f, 18000f };
            float dayMin = Float.POSITIVE_INFINITY;
            float dayMax = Float.NEGATIVE_INFINITY;
            for (float t : sampleTicks) {
                float modifier = getNighttimeTempModifier(t, biomeId, world);
                float temp = dailyBase + modifier;
                dayMin = Math.min(dayMin, temp);
                dayMax = Math.max(dayMax, temp);
            }
            // D) smooth‐clamp min toward avgNight & max toward avgDay
            if (clamp != null) {
                // ease the min toward avgNight
                float easedMin = easeTowardAverage(dayMin, clamp.avgNight());
                // if still outside [minMin..maxMax], apply the ultimate smoother
                dayMin = (easedMin < clamp.minMin() || easedMin > clamp.maxMax())
                        ? ultimateSmoother(easedMin, clamp.minMin(), clamp.maxMax())
                        : easedMin;

                // same for the max toward avgDay
                float easedMax = easeTowardAverage(dayMax, clamp.avgDay());
                dayMax = (easedMax < clamp.minMin() || easedMax > clamp.maxMax())
                        ? ultimateSmoother(easedMax, clamp.minMin(), clamp.maxMax())
                        : easedMax;
            }

            week[day][0] = dayMin;
            week[day][1] = dayMax;

        }

        return week;
    }
    /**
     * If `v` lies outside [boundLow..boundHigh], then:
     * 1) globally cap to ±65 °C,
     * 2) credit back a scaled portion of the overshoot,
     * 3) ease the value toward the nearest bound using average-based easing,
     * 4) snap within a 2 °C buffer if still out-of-bounds.
     */
    private static float ultimateSmoother(float v, float boundLow, float boundHigh) {
        float sign = Math.signum(v);

        // 1) Cap to ±65°C
        float capped = sign * Math.min(BOUND_TEMP, Math.abs(v));

        // 2) Compute credit based on overshoot and scale
        float credit = computeCredit(v) * sign;
        float postCap = capped + credit;

        // 3) Ease toward the average of the bounds
        float boundMid = (boundLow + boundHigh) / 2f;
        float eased = easeTowardAverage(postCap, boundMid);

        // 4) Final hard clamp into a 2°C safety buffer
        if (eased < boundLow)  return boundLow + 2f;
        if (eased > boundHigh) return boundHigh - 2f;

        return eased;
    }
    private static float computeCredit(float value) {
        float abs = Math.abs(value);
        float overshoot = abs - BOUND_TEMP;
        if (overshoot <= 0f) return 0f;

        // Example: a natural log-based asymptotic curve, scaled to fit
        float credit = (float) (Math.log(overshoot + 1) / Math.log(2.5)) + 0.5f;

        // Optional: clamp to a max if needed
        return Math.min(credit*4, 10f);
    }


    /**
     * Eases `v` toward `avg` in four bands:
     *  - |Δ| ≤ 3°C  → 10% pull
     *  - |Δ| ≤ 6°C  → 35% pull
     *  - |Δ| ≤ 10°C → 65% pull
     *  - |Δ| > 10°C → 100% pull
     *
     * Does NOT enforce any bounds—just returns an eased value:
     *   eased = avg + (v − avg) * (1 − factor)
     */
    private static float easeTowardAverage(float v, float avg) {
        float diff = v - avg;
        float absDiff = Math.abs(diff);

        // More tolerant: increase denominator to reduce strength
        float strength = (float)(1.0 - Math.exp(-absDiff / 6.0)); // was /4.0
        strength = Math.min(strength, 0.7f);  // still cap at 70% pull

        return avg + diff * (1.0f - strength);
    }



    /*────────────────────────────────────────────────────────────────────────*/

    /** Maps Serene baseTemp (–0.5→2.0) into biome’s sea-level °C range. */
    private static float toCelsiusSeaLevel(ResourceLocation biome, float baseTemp, Season season) {
        baseTemp = Math.max(IN_MIN, Math.min(IN_MAX, baseTemp));
        BiomeTempConfig.Range range = BiomeTempConfig.getRange(biome,season);
        float norm = (baseTemp - IN_MIN) / DEN;
        return range.minC() + norm * (range.maxC() - range.minC());
    }

    /** Applies day/night drop: night=18:00–06:00, cold drop or minor in tropics. */
    private static float getNighttimeTempModifier(float timeOfDay, ResourceLocation biome, Level world) {
        // Night = [12000..23999] & [0..5999]
        if (timeOfDay >= 12000f || timeOfDay < 6000f) {
            return isTropicalBiome(biome,world,-1.0f, -2.5f);
        }
        else{
            return isTropicalBiome(biome,world,1.0f, 2.5f);
        }
    }

    /** True if biome belongs to Sereneseasons tropical tag. */
    public static float isTropicalBiome(ResourceLocation biomeId, Level level,float tropicalAmp, float nonTropicalAmp) {
        Registry<Biome> reg = level.registryAccess().registryOrThrow(Registries.BIOME);
        Biome b = reg.get(biomeId);
        if (b == null) return nonTropicalAmp;
        Boolean bol= reg.getResourceKey(b)
                .flatMap(reg::getHolder)
                .map(holder -> holder.is(ModTags.Biomes.TROPICAL_BIOMES))
                .orElse(false);
        return bol ? tropicalAmp : nonTropicalAmp;
    }

    /** Spatial daily fluctuation based on chunk and day: ±maxFluctuation. */
    private static float getDailyFluctuation(Level world, BlockPos pos, float maxFluctuation) {
        long day = world.getDayTime() / 24000L;
        int hash = Objects.hash(pos.getX() >> 4, pos.getZ() >> 4, day);
        return ((hash % 200) - 100) / 100f * maxFluctuation;
    }
}
