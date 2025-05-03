package net.Gabou.projectatmosphere.temperature.event;

import net.Gabou.projectatmosphere.temperature.TemperatureManager;
import net.minecraft.world.level.Level;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;

public class SeasonTracker {
    private static Season.SubSeason lastSubSeason = null;

    public static void tick(Level level) {
        Season.SubSeason current = SeasonHelper.getSeasonState(level).getSubSeason();

        if (lastSubSeason != null && current != lastSubSeason) {
            System.out.println("Season changed from " + lastSubSeason + " to " + current);
            TemperatureManager.regenerateWeeklyForecast(level);
        }

        lastSubSeason = current;
    }
}
