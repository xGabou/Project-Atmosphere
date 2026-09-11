package net.Gabou.projectatmosphere.api.climate;

import java.util.Optional;
import java.util.function.BiFunction;
import net.Gabou.projectatmosphere.manager.AtmosphereManager;
import net.Gabou.projectatmosphere.manager.ForecastOrchestrator;
import net.Gabou.projectatmosphere.modules.atmosphere.AtmosphericStateRegistry;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Optional companion boundary. PA currently owns one Overworld atmosphere per server process.
 * An identity lease refuses a second simultaneous owner; no coordinate-only cross-world lookup.
 * Context callbacks MUST return existing detached data and MUST NOT load chunks or generate terrain.
 */
public final class RuntimeClimateBridge {
    public static final int API_VERSION = 1;
    private static volatile Session current;
    private RuntimeClimateBridge() { }

    public static synchronized Session open(ServerLevel level,
            BiFunction<Integer, Integer, Optional<GeographicClimate>> context) {
        if (level.dimension() != Level.OVERWORLD || !level.getServer().isSameThread()) {
            throw new IllegalStateException("Runtime climate requires the Overworld server thread");
        }
        if (current != null) throw new IllegalStateException("PA runtime climate owner already bound");
        current = new Session(level, java.util.Objects.requireNonNull(context));
        return current;
    }

    /** Existing geographic expectation, before PA adds its seasonal and weather variation. */
    public static double initialTemperature(ServerLevel level, int x, int z, double fallback) {
        Session session = current;
        if (session == null || session.level != level) return fallback;
        var callback = session.context;
        if (callback == null) return fallback;
        var value = callback.apply(x, z);
        if (value.isEmpty()) return fallback;
        session.baselineInitializations.incrementAndGet();
        return value.get().baselineTemperatureCelsius();
    }

    public static final class Session implements AutoCloseable {
        private volatile ServerLevel level;
        private BiFunction<Integer, Integer, Optional<GeographicClimate>> context;
        private final java.util.concurrent.atomic.AtomicLong baselineInitializations = new java.util.concurrent.atomic.AtomicLong();
        private Session(ServerLevel level, BiFunction<Integer, Integer, Optional<GeographicClimate>> context) {
            this.level = level; this.context = context;
        }
        public int regionSize() { requireOpen(); return RegionInstanceKey.DEFAULT_REGION_SIZE; }
        /** Number of forecast initialization requests supplied with retained baseline temperature. */
        public long baselineInitializationCount() { requireOpen(); return baselineInitializations.get(); }
        public Optional<GeographicClimate> baseline(int x, int z) { requireOpen(); return context.apply(x, z); }
        public Optional<RegionalClimateObservation> sample(int x, int z) {
            ServerLevel owner = requireOpen();
            if (!AtmosphereManager.isInitialGenerationDone || ForecastOrchestrator.isRegenerating()) return Optional.empty();
            RegionInstanceKey key = new RegionInstanceKey(Math.floorDiv(x, regionSize()), Math.floorDiv(z, regionSize()));
            if (!AtmosphericStateRegistry.getActiveStates().contains(key)) return Optional.empty();
            var state = AtmosphericStateRegistry.getState(key);
            if (state == null || state.getWind() == null) return Optional.empty();
            var wind = state.getWind();
            return Optional.of(new RegionalClimateObservation(key.regionX(), key.regionZ(), key.regionSize(),
                    owner.getGameTime(), state.getTemperature(), state.getRainIntensity(), state.getHumidity(),
                    -wind.baseSpeed() * Math.sin(wind.angleRadians()), wind.baseSpeed() * Math.cos(wind.angleRadians()),
                    state.getPressure()));
        }
        private ServerLevel requireOpen() {
            ServerLevel owner = level;
            if (owner == null || current != this) throw new IllegalStateException("PA climate session closed");
            if (!owner.getServer().isSameThread()) throw new IllegalStateException("PA observations require server thread");
            return owner;
        }
        @Override public void close() {
            synchronized (RuntimeClimateBridge.class) {
                if (current == this) current = null;
                level = null; context = null;
            }
        }
    }
}
