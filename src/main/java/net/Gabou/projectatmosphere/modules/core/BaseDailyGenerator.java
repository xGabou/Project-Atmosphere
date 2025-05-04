package net.Gabou.projectatmosphere.modules.core;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Set;

/**
 * Base for your “daily generator” classes (DailyProfileGenerator, DailyPressureGenerator, …).
 * @param <P>  the type of one day‐profile (e.g. float[], double[])
 */
public abstract class BaseDailyGenerator<P> {

    /**
     * Go through all cached forecasts → build today & tomorrow curves if missing.
     * Exactly matches your existing scheduleGenerationForTodayAndTomorrow pattern.
     */
    public void scheduleGenerationForTodayAndTomorrow(Level world) {
        long now = world.getDayTime();
        for (String key : getAllBiomeKeys()) {
            ResourceLocation biome = new ResourceLocation(key);

            boolean hasToday    = hasDayProfile(biome);
            boolean hasTomorrow = hasTomorrowProfile(biome);
            if (hasToday && hasTomorrow) continue;

            P today   = hasToday   ? null : generateDayProfile(biome, world, now);
            P tomorrow = hasTomorrow ? null : generateDayProfile(biome, world, now + 24000L);

            if (!hasToday)    putDayProfile(biome, today);
            if (!hasTomorrow) putTomorrowProfile(biome, tomorrow);
        }
    }

    /** Build one 240‐step curve for a given biome & worldTick. */
    protected abstract P generateDayProfile(
            ResourceLocation biome, Level world, long worldTick);

    // hooks to your ProfileManager:
    protected abstract boolean hasDayProfile(ResourceLocation biome);
    protected abstract boolean hasTomorrowProfile(ResourceLocation biome);
    protected abstract void putDayProfile(ResourceLocation biome, P profile);
    protected abstract void putTomorrowProfile(ResourceLocation biome, P profile);

    protected abstract Set<String> getAllBiomeKeys();
}
