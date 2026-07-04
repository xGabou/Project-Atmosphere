package net.Gabou.projectatmosphere.clouds.api;

import net.minecraft.world.level.Level;

/**
 * Public gameplay API: analytic cloud density at any world position, with no
 * GPU roundtrip. Backed by the CPU cell evaluation on both sides (server cell
 * simulation, client interpolated cell cache). Intended consumers: plane
 * turbulence, icing, visibility AI, audio muffling.
 */
public final class CloudDensityQuery {
    /** Density provider for one side. */
    @FunctionalInterface
    public interface Provider {
        float densityAt(Level level, double x, double y, double z);
    }

    private static volatile Provider clientProvider;
    private static volatile Provider serverProvider;

    private CloudDensityQuery() {
    }

    public static void setClientProvider(Provider provider) {
        clientProvider = provider;
    }

    public static void setServerProvider(Provider provider) {
        serverProvider = provider;
    }

    /**
     * Returns the analytic cloud density (0..1) at a world position.
     *
     * @param level any level; the matching side's provider is used
     * @return 0 when no provider is registered or no cloud is present
     */
    public static float densityAt(Level level, double x, double y, double z) {
        if (level == null) {
            return 0.0F;
        }
        Provider provider = level.isClientSide() ? clientProvider : serverProvider;
        if (provider == null) {
            return 0.0F;
        }
        try {
            return Math.max(0.0F, Math.min(1.0F, provider.densityAt(level, x, y, z)));
        } catch (Exception exception) {
            return 0.0F;
        }
    }
}
