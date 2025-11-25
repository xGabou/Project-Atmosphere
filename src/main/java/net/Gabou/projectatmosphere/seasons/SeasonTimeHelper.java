package net.Gabou.projectatmosphere.seasons;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Central season helper with a pluggable delegate (e.g., Serene Seasons, TFC).
 */
public final class SeasonTimeHelper {
    private static final Logger LOGGER = LogManager.getLogger("ProjectAtmosphere/SeasonTime");
    private static final AtomicReference<SeasonTimeDelegate> DELEGATE =
            new AtomicReference<>(new NeutralDelegate());

    private SeasonTimeHelper() {
    }

    public static void setDelegate(SeasonTimeDelegate delegate) {
        if (delegate == null) return;
        DELEGATE.set(delegate);
        LOGGER.info("Season time delegate set to '{}'.", delegate.getClass().getName());
    }

    public static SeasonSnapshot snapshot(Level level) {
        try {
            return DELEGATE.get().snapshot(level);
        } catch (Exception e) {
            LOGGER.warn("Season delegate failed; using neutral snapshot.", e);
            return SeasonSnapshot.neutral();
        }
    }

    public static SeasonStage stage(Level level) {
        return snapshot(level).stage();
    }

    public static long seasonCycleTicks(Level level) {
        try {
            return DELEGATE.get().seasonCycleTicks(level);
        } catch (Exception e) {
            return NeutralDelegate.SEASON_CYCLE;
        }
    }

    public static long seasonDuration(Level level) {
        try {
            return DELEGATE.get().seasonDuration(level);
        } catch (Exception e) {
            return NeutralDelegate.SEASON_DURATION;
        }
    }

    public static long dayDuration(Level level) {
        try {
            return DELEGATE.get().dayDuration(level);
        } catch (Exception e) {
            return NeutralDelegate.DAY_DURATION;
        }
    }

    private static final class NeutralDelegate implements SeasonTimeDelegate {
        static final long DAY_DURATION = 24000L;
        static final long SEASON_DURATION = 24000L;
        static final long SEASON_CYCLE = SEASON_DURATION * 4;
        private static final ResourceLocation ID = new ResourceLocation("projectatmosphere", "neutral");

        @Override
        public SeasonSnapshot snapshot(Level level) {
            return SeasonSnapshot.neutral();
        }

        @Override
        public long seasonCycleTicks(Level level) {
            return SEASON_CYCLE;
        }

        @Override
        public long seasonDuration(Level level) {
            return SEASON_DURATION;
        }

        @Override
        public long dayDuration(Level level) {
            return DAY_DURATION;
        }
    }
}
