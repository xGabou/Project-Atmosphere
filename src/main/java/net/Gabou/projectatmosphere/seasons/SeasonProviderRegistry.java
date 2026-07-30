package net.Gabou.projectatmosphere.seasons;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Registry for a single active season provider. External compat modules can
 * call {@link #setProvider(SeasonProvider)} to supply their implementation.
 */
public final class SeasonProviderRegistry {
    private static final Logger LOGGER = LogManager.getLogger("ProjectAtmosphere/Seasons");
    private static final AtomicReference<SeasonProvider> ACTIVE = new AtomicReference<>(new NeutralSeasonProvider());

    private SeasonProviderRegistry() {
    }

    public static void setProvider(SeasonProvider provider) {
        if (provider == null) {
            return;
        }
        SeasonProvider previous = ACTIVE.getAndSet(provider);
        ResourceLocation prevId = previous == null ? null : ResourceLocation.parse(previous.id());
        LOGGER.info("Season provider set to '{}' (previous: {}).", provider.id(), prevId);
    }

    public static SeasonProvider getProvider() {
        return Objects.requireNonNullElseGet(ACTIVE.get(), NeutralSeasonProvider::new);
    }

    public static SeasonSnapshot snapshot(Level level) {
        try {
            return getProvider().snapshot(level);
        } catch (Exception e) {
            LOGGER.warn("Season provider '{}' failed; falling back to neutral.", getProvider().id(), e);
            return SeasonSnapshot.neutral();
        }
    }

    public static SeasonSnapshot snapshot(Level level, BlockPos pos) {
        try {
            return getProvider().snapshot(level, pos);
        } catch (Exception e) {
            LOGGER.warn("Season provider '{}' failed for position {}; falling back to neutral.", getProvider().id(), pos, e);
            return SeasonSnapshot.neutral();
        }
    }

    private static final class NeutralSeasonProvider implements SeasonProvider {
        private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("projectatmosphere", "neutral");

        @Override
        public String id() {
            return ID.toString();
        }

        @Override
        public SeasonSnapshot snapshot(Level level) {
            return SeasonSnapshot.neutral();
        }
    }
}
