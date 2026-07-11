package net.Gabou.projectatmosphere.clouds.service;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.ToIntBiFunction;

/**
 * Backend-neutral query bridge. Optional integrations install providers only
 * after their mod-presence check, keeping common/client state free of their
 * classes.
 */
public final class OptionalCloudQueries {
    private static volatile BiPredicate<Level, BlockPos> cloudAt = (level, pos) -> false;
    private static volatile BiPredicate<Level, BlockPos> rainingAt = Level::isRainingAt;
    private static volatile ToIntBiFunction<ServerLevel, BlockPos> severityAt = (level, pos) -> 1;

    private OptionalCloudQueries() {
    }

    public static void install(
            BiPredicate<Level, BlockPos> cloudProvider,
            BiPredicate<Level, BlockPos> rainProvider
    ) {
        cloudAt = Objects.requireNonNull(cloudProvider, "cloudProvider");
        rainingAt = Objects.requireNonNull(rainProvider, "rainProvider");
    }

    public static boolean isCloudAt(Level level, BlockPos pos) {
        return level != null && pos != null && cloudAt.test(level, pos);
    }

    public static boolean isRainingAt(Level level, BlockPos pos) {
        return level != null && pos != null && rainingAt.test(level, pos);
    }

    /** Installs the server-only severity probe supplied by an active optional backend. */
    public static void installSeverity(ToIntBiFunction<ServerLevel, BlockPos> severityProvider) {
        severityAt = Objects.requireNonNull(severityProvider, "severityProvider");
    }

    /** Returns the backend's local cloud severity, or the neutral minimum. */
    public static int sampleSeverity(ServerLevel level, BlockPos pos) {
        return level == null || pos == null ? 1 : severityAt.applyAsInt(level, pos);
    }

    public static void reset() {
        cloudAt = (level, pos) -> false;
        rainingAt = Level::isRainingAt;
        severityAt = (level, pos) -> 1;
    }
}
