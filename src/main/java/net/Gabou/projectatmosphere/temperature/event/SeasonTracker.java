package net.Gabou.projectatmosphere.temperature.event;

import net.Gabou.projectatmosphere.temperature.TemperatureManager;
import net.minecraft.world.level.Level;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;

public class SeasonTracker {
    private static Season.SubSeason lastSubSeason = null;

    public static void init(Level level) {
        lastSubSeason = SeasonHelper.getSeasonState(level).getSubSeason();
        System.out.println("Initial season: " + lastSubSeason);
    }

    public static void tick(Level level) {
        Season.SubSeason current = SeasonHelper.getSeasonState(level).getSubSeason();

        if (lastSubSeason != null && current != lastSubSeason) {
            System.out.println("Season changed from " + lastSubSeason + " to " + current);
            TemperatureManager.init(level,level.players().get(0).blockPosition(), 250);
        }

        lastSubSeason = current;
    }
}