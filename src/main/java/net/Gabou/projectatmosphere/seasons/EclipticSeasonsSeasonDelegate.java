package net.Gabou.projectatmosphere.seasons;

import com.teamtea.eclipticseasons.api.event.SolarTermChangeEvent;
import com.teamtea.eclipticseasons.common.core.SolarHolders;
import com.teamtea.eclipticseasons.common.core.solar.SolarDataManager;
import com.teamtea.eclipticseasons.api.constant.solar.SolarTerm;
import net.Gabou.projectatmosphere.event.EclipticTracker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;

public class EclipticSeasonsSeasonDelegate implements SeasonTimeDelegate {


    public EclipticSeasonsSeasonDelegate() {
        MinecraftForge.EVENT_BUS.addListener(EclipticTracker::onSolarTermChange);
    }

    private static final int TERMS_PER_SEASON = 6;
    private static final int TOTAL_TERMS = 24;

    @Override
    public SeasonSnapshot snapshot(Level level) {
        if (level == null) {
            return SeasonSnapshot.neutral();
        }

        SolarDataManager data = SolarHolders.getSaveData(level);
        if (data == null) {
            return SeasonSnapshot.neutral();
        }

        SolarTerm term = data.getSolarTerm();
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
                0.0f
        );
    }

    @Override
    public long seasonCycleTicks(Level level) {
        if (level == null) {
            return 24000L * 30 * 4;
        }

        SolarDataManager data = SolarHolders.getSaveData(level);
        if (data == null) {
            return 24000L * 30 * 4;
        }

        long daysPerSeason = (long) data.getSolarTermLastingDays() * TERMS_PER_SEASON;
        return daysPerSeason * 24000L;
    }

    @Override
    public long seasonDuration(Level level) {
        if (level == null) {
            return 24000L * 30;
        }

        SolarDataManager data = SolarHolders.getSaveData(level);
        if (data == null) {
            return 24000L * 30;
        }

        return (long) data.getSolarTermLastingDays() * TERMS_PER_SEASON * 24000L;
    }

    @Override
    public long dayDuration(Level level) {
        return 24000L;
    }
}
