package net.Gabou.projectatmosphere.compat.projectlandscape;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.ModList;

/** Optional bridge to Project Landscape's forecast-only API. No Landscape classes are linked here. */
public final class ProjectLandscapeForecastCompat {
    private static final String MOD_ID = "projectlandscape";
    private static final int API_VERSION = 1;
    private static final String API_CLASS = "com.gabou.projectlandscape.api.atmosphere.LandscapeAtmosphereApi";
    private static final AtomicBoolean UNAVAILABLE_LOGGED = new AtomicBoolean();

    private ProjectLandscapeForecastCompat() {
    }

    public static Optional<Sampler> open(ServerLevel level) {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return Optional.empty();
        }
        try {
            Class<?> api = Class.forName(API_CLASS);
            if (api.getField("API_VERSION").getInt(null) != API_VERSION) {
                unavailable("unsupported Project Landscape forecast API version", null);
                return Optional.empty();
            }
            Method supports = api.getMethod("supports", ServerLevel.class);
            if (!Boolean.TRUE.equals(supports.invoke(null, level))) {
                return Optional.empty();
            }
            Method sample = api.getMethod("sampleForAtmosphere", ServerLevel.class, int.class, int.class);
            ProjectAtmosphere.LOGGER.info("[Atmosphere] Using Project Landscape forecast API v{} for {}.", API_VERSION,
                    level.dimension().location());
            return Optional.of(new Sampler(level, sample));
        } catch (ReflectiveOperationException | LinkageError failure) {
            unavailable("Project Landscape forecast API is unavailable; using normal biome sampling", failure);
            return Optional.empty();
        }
    }

    private static void unavailable(String message, Throwable failure) {
        if (UNAVAILABLE_LOGGED.compareAndSet(false, true)) {
            if (failure == null) {
                ProjectAtmosphere.LOGGER.warn("[Atmosphere] {}.", message);
            } else {
                ProjectAtmosphere.LOGGER.warn("[Atmosphere] {}: {}", message, failure.toString());
            }
        }
    }

    public static final class Sampler {
        private final ServerLevel level;
        private final Method sample;
        private Method biomeId;
        private boolean failed;

        private Sampler(ServerLevel level, Method sample) {
            this.level = level;
            this.sample = sample;
        }

        public Optional<ResourceLocation> biomeId(int x, int z) {
            if (this.failed) {
                return Optional.empty();
            }
            try {
                Object result = this.sample.invoke(null, this.level, x, z);
                if (!(result instanceof Optional<?> optional) || optional.isEmpty()) {
                    return Optional.empty();
                }
                Object forecastSample = optional.get();
                if (this.biomeId == null) {
                    this.biomeId = forecastSample.getClass().getMethod("biomeId");
                }
                Object biome = this.biomeId.invoke(forecastSample);
                return biome instanceof ResourceLocation location ? Optional.of(location) : Optional.empty();
            } catch (ReflectiveOperationException | LinkageError failure) {
                this.failed = true;
                unavailable("Project Landscape forecast sample failed; using normal biome sampling", failure);
                return Optional.empty();
            }
        }
    }
}
