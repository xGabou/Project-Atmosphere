package net.Gabou.projectatmosphere.clouds.client.render;

import net.Gabou.projectatmosphere.clouds.AtmosphereCloudPolicy;
import net.Gabou.projectatmosphere.clouds.backend.CloudBackendResolver;
import net.Gabou.projectatmosphere.clouds.backend.CloudVisualBackend;
import net.Gabou.projectatmosphere.clouds.client.render.field.CloudFieldVolumeRenderConfig;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.VolumetricCloudRenderHook;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.VolumetricCloudClientLifecycle;
import net.Gabou.projectatmosphere.client.render.shader.CloudFieldVolumeShaders;
import net.Gabou.projectatmosphere.client.render.shader.VolumetricCloudShaders;
import net.Gabou.projectatmosphere.clouds.service.AtmosphereCloudServices;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Single client-side contract deciding which renderer owns the base cloud
 * layer. Callers must not infer ownership from individual configuration flags.
 */
public final class ClientCloudRenderOwnership {
    private static volatile Predicate<ClientLevel> simpleCloudsDimensionProbe = level -> false;
    private static volatile Owner lastResolvedOwner;

    public enum Owner {
        VANILLA,
        PA_VOLUMETRIC,
        PA_FIELD_FALLBACK,
        SIMPLE_CLOUDS
    }

    private ClientCloudRenderOwnership() {
    }

    public static Owner resolve(@Nullable ClientLevel level) {
        // PA does not register a native replacement when Simple Clouds is
        // installed. Simple Clouds therefore remains the sole base-cloud owner.
        if (AtmosphereCloudServices.isSimpleCloudsLoaded()) {
            return recordOwner(level != null && simpleCloudsDimensionProbe.test(level)
                    ? Owner.SIMPLE_CLOUDS
                    : Owner.VANILLA);
        }
        if (level == null
                || AtmoCommonConfig.CLOUD_MODE.get() == AtmoCommonConfig.CloudMode.VANILLA
                || !AtmosphereCloudPolicy.canUsePaInDimension(level)
                || CloudBackendResolver.resolve(level) != CloudVisualBackend.PA_NATIVE) {
            return recordOwner(Owner.VANILLA);
        }
        if (VolumetricCloudRenderHook.isRuntimeConfigured()
                && VolumetricCloudShaders.isReady()) {
            return recordOwner(Owner.PA_VOLUMETRIC);
        }
        if (CloudFieldVolumeRenderConfig.isEnabled()
                && CloudFieldVolumeShaders.isReady()) {
            return recordOwner(Owner.PA_FIELD_FALLBACK);
        }
        return recordOwner(Owner.VANILLA);
    }

    public static void setSimpleCloudsDimensionProbe(Predicate<ClientLevel> probe) {
        simpleCloudsDimensionProbe = Objects.requireNonNull(probe, "probe");
    }

    /** Clears the transition sentinel at world/session boundaries. */
    public static void reset() {
        lastResolvedOwner = null;
    }

    public static boolean ownsBaseCloudRendering(@Nullable ClientLevel level) {
        Owner owner = resolve(level);
        return owner == Owner.PA_VOLUMETRIC || owner == Owner.PA_FIELD_FALLBACK;
    }

    public static boolean ownsOpaqueCloudPass(@Nullable ClientLevel level) {
        return ownsBaseCloudRendering(level);
    }

    public static boolean ownsTransparentCloudPass(@Nullable ClientLevel level) {
        return ownsBaseCloudRendering(level);
    }

    public static boolean ownsVolumetricPass(@Nullable ClientLevel level) {
        return resolve(level) == Owner.PA_VOLUMETRIC;
    }

    public static boolean ownsFieldFallbackPass(@Nullable ClientLevel level) {
        return resolve(level) == Owner.PA_FIELD_FALLBACK;
    }

    private static Owner recordOwner(Owner owner) {
        Owner previous = lastResolvedOwner;
        lastResolvedOwner = owner;
        if (previous != null && previous != owner) {
            VolumetricCloudClientLifecycle.onBackendChanged();
        }
        return owner;
    }
}
