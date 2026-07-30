package net.Gabou.projectatmosphere.clouds.field;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * Smoothly hydrates or dehydrates identifiable cloudlets as a field moves
 * between LOD bands.
 */
public final class CloudFieldHydrationController {
    private final Config config;

    public CloudFieldHydrationController(Config config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public static CloudFieldHydrationController defaultController() {
        return new CloudFieldHydrationController(Config.DEFAULT);
    }

    public Config config() {
        return config;
    }

    public CloudFieldRuntimeState update(
            CloudField field,
            CloudFieldRuntimeState previous,
            CloudLodBand targetBand,
            long worldTime,
            float deltaTicks,
            Vec3 previousCenter
    ) {
        Objects.requireNonNull(field, "field");
        CloudLodBand band = targetBand == null ? CloudLodBand.HAZE : targetBand;
        CloudFieldRuntimeState prior = previous == null
                ? CloudFieldRuntimeState.initial(field, band, worldTime)
                : previous;

        float progress = approach(
                prior.hydrationProgress(),
                hydrationTarget(band),
                hydrationRate(band) * Math.max(0.0F, finite(deltaTicks, 0.0F))
        );
        CloudFieldHydrationState state = stateFor(prior.hydrationProgress(), progress, band);
        int activeCloudlets = activeCloudletCount(field.cloudletCount(), band, progress);

        return new CloudFieldRuntimeState(
                field.fieldId(),
                band,
                prior.currentLodBand(),
                state,
                progress,
                worldTime,
                activeCloudlets,
                previousCenter == null ? field.center() : previousCenter
        );
    }

    private float hydrationTarget(CloudLodBand band) {
        return band == CloudLodBand.DYNAMIC || band == CloudLodBand.TRANSITION ? 1.0F : 0.0F;
    }

    private float hydrationRate(CloudLodBand band) {
        return switch (band) {
            case DYNAMIC -> config.dynamicHydratePerTick();
            case TRANSITION -> config.transitionHydratePerTick();
            case FAR_PROCEDURAL -> config.farDehydratePerTick();
            case HAZE -> config.hazeDehydratePerTick();
        };
    }

    private CloudFieldHydrationState stateFor(float previousProgress, float progress, CloudLodBand band) {
        if (progress <= 0.001F) {
            return CloudFieldHydrationState.NOT_HYDRATED;
        }
        if (progress >= 0.999F && (band == CloudLodBand.DYNAMIC || band == CloudLodBand.TRANSITION)) {
            return CloudFieldHydrationState.HYDRATED;
        }
        if (progress >= previousProgress) {
            return CloudFieldHydrationState.HYDRATING;
        }
        return CloudFieldHydrationState.DEHYDRATING;
    }

    private int activeCloudletCount(int targetCloudletCount, CloudLodBand band, float hydrationProgress) {
        int target = Math.max(0, targetCloudletCount);
        if (target == 0 || !band.hasIdentifiableCloudlets() || hydrationProgress <= 0.001F) {
            return 0;
        }
        int lodTarget = Math.max(1, Math.round(target * band.cloudletFraction()));
        int hydratedTarget = Math.max(1, Math.round(lodTarget * hydrationProgress));
        return Math.min(target, Math.min(lodTarget, hydratedTarget));
    }

    private static float approach(float current, float target, float maxDelta) {
        float delta = target - current;
        if (Math.abs(delta) <= maxDelta) {
            return clamp01(target);
        }
        return clamp01(current + Math.copySign(maxDelta, delta));
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, finite(value, 0.0F)));
    }

    private static float finite(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    public record Config(
            float dynamicHydratePerTick,
            float transitionHydratePerTick,
            float farDehydratePerTick,
            float hazeDehydratePerTick
    ) {
        public static final Config DEFAULT = new Config(0.025F, 0.010F, 0.012F, 0.035F);

        public Config {
            dynamicHydratePerTick = positive(dynamicHydratePerTick, 0.025F);
            transitionHydratePerTick = positive(transitionHydratePerTick, 0.010F);
            farDehydratePerTick = positive(farDehydratePerTick, 0.012F);
            hazeDehydratePerTick = positive(hazeDehydratePerTick, 0.035F);
        }

        private static float positive(float value, float fallback) {
            float finiteValue = Float.isFinite(value) ? value : fallback;
            return Math.max(0.0F, finiteValue);
        }
    }
}
