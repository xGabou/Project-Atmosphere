package net.Gabou.projectatmosphere.seasons;

import com.teamtea.eclipticseasons.common.core.SolarHolders;
import com.teamtea.eclipticseasons.common.core.solar.SolarDataManager;
import net.Gabou.projectatmosphere.event.EclipticTracker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;

public class EclipticSeasonsSeasonDelegate implements SeasonTimeDelegate {
    private static final String PROVIDER_ID = "eclipticseasons";
    private static final long DEFAULT_DAYS_PER_TERM = 5L;

    public EclipticSeasonsSeasonDelegate() {
        MinecraftForge.EVENT_BUS.addListener(EclipticTracker::onSolarTermChange);
    }

    private static final int TERMS_PER_SEASON = 6;

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public SeasonSnapshot snapshot(Level level) {
        if (level == null) {
            return SeasonSnapshot.neutral();
        }

        SolarDataManager data = SolarHolders.getSaveData(level);
        if (data == null) {
            return SeasonSnapshot.neutral();
        }

        int termIndex = data.getSolarTermIndex(); // 0..23
        int daysPerTerm = data.getSolarTermLastingDays();

        // --- 4 saisons simples
        int seasonIndex = termIndex / TERMS_PER_SEASON;

        SeasonStage stage = switch (seasonIndex) {
            case 0 -> SeasonStage.SPRING;
            case 1 -> SeasonStage.SUMMER;
            case 2 -> SeasonStage.AUTUMN;
            case 3 -> SeasonStage.WINTER;
            default -> SeasonStage.SPRING;
        };

        // --- Progression dans la saison (0..1)
        int dayInSeason = data.getSolarTermDaysInPeriod()
                + (termIndex % TERMS_PER_SEASON) * daysPerTerm;

        int totalSeasonDays = TERMS_PER_SEASON * daysPerTerm;

        float progress = (float) dayInSeason / (float) totalSeasonDays;

        return new SeasonSnapshot(
                new ResourceLocation("eclipticseasons", "season"),
                stage,
                progress,
                SeasonClimateProfile.temperatureOffsetC(stage, progress)
        );
    }

    @Override
    public long seasonCycleTicks(Level level) {
        if (level == null) {
            return 0L;
        }

        SolarDataManager data = SolarHolders.getSaveData(level);
        if (data == null) {
            return 0L;
        }

        long dayInCycle = (long) data.getSolarTermIndex() * data.getSolarTermLastingDays()
                + Math.max(0, data.getSolarTermDaysInPeriod());
        long dayTime = Math.floorMod(level.getDayTime(), 24000L);
        return dayInCycle * 24000L + dayTime;
    }

    @Override
    public long seasonDuration(Level level) {
        if (level == null) {
            return DEFAULT_DAYS_PER_TERM * TERMS_PER_SEASON * 24000L;
        }

        SolarDataManager data = SolarHolders.getSaveData(level);
        if (data == null) {
            return DEFAULT_DAYS_PER_TERM * TERMS_PER_SEASON * 24000L;
        }

        return (long) data.getSolarTermLastingDays() * TERMS_PER_SEASON * 24000L;
    }

    @Override
    public long dayDuration(Level level) {
        return 24000L;
    }
}
