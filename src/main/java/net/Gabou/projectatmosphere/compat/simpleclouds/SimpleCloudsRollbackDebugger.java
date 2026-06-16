package net.Gabou.projectatmosphere.compat.simpleclouds;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.backend.CloudBackendBridgeSnapshot;
import net.Gabou.projectatmosphere.clouds.backend.CloudBackendMigrationManager;
import net.Gabou.projectatmosphere.clouds.backend.CloudBackendStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec2;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SimpleCloudsRollbackDebugger {
    private static final Map<String, PositionSample> PREVIOUS_POSITIONS = new ConcurrentHashMap<>();
    private static volatile String trackedKey;
    private static volatile long lastTouchGameTime = Long.MIN_VALUE;
    private static volatile String lastTouchReason = "none";
    private static volatile long lastWriteGameTime = Long.MIN_VALUE;
    private static volatile String lastWriteReason = "none";

    private SimpleCloudsRollbackDebugger() {
    }

    public static void markMovementTouched(String trackingKey, ServerLevel level, String reason) {
        lastTouchGameTime = level == null ? Long.MIN_VALUE : level.getGameTime();
        lastTouchReason = safe(reason) + " key=" + safe(trackingKey);
    }

    public static void markSimpleCloudWrite(String action, String trackingKey, ServerLevel level) {
        lastWriteGameTime = level == null ? Long.MIN_VALUE : level.getGameTime();
        lastWriteReason = safe(action) + " key=" + safe(trackingKey);
        ProjectAtmosphere.LOGGER.warn(
                "[Atmosphere] Simple Clouds write path used: action={} key={} gameTime={}",
                action,
                trackingKey,
                level == null ? -1L : level.getGameTime()
        );
    }

    public static void logSevereCloudSample(
            ServerLevel level,
            CloudRegion region,
            CloudBackendBridgeSnapshot snapshot,
            SimpleCloudsTrackingIdentity.Entry identity
    ) {
        if (level == null || region == null || snapshot == null || identity == null || !isSevere(identity.typeId())) {
            return;
        }
        if (trackedKey == null) {
            trackedKey = identity.trackingKey();
        }
        if (!trackedKey.equals(identity.trackingKey())) {
            return;
        }

        long gameTime = level.getGameTime();
        PositionSample previous = PREVIOUS_POSITIONS.put(identity.trackingKey(), new PositionSample(region.getWorldX(), region.getWorldZ(), gameTime));
        double delta = previous == null ? 0.0D : distance(previous.x(), previous.z(), region.getWorldX(), region.getWorldZ());
        long deltaTicks = previous == null ? 0L : Math.max(0L, gameTime - previous.gameTime());
        double speed = deltaTicks <= 0L ? 0.0D : delta / (deltaTicks / 20.0D);
        Vec2 movement = region.getMovementDirection();
        CloudBackendStatus status = CloudBackendMigrationManager.status(level);
        boolean touchedThisTick = lastTouchGameTime == gameTime;
        boolean wroteThisTick = lastWriteGameTime == gameTime;

        ProjectAtmosphere.LOGGER.info(
                "[Atmosphere] Simple Clouds rollback audit key={} type={} serverPos=({}, {}) clientRenderPos=unavailable bridgePos=({}, {}) previousPos={} deltaBlocks={} speedBlocksPerSecond={} movementDir={} maxSpeed={} paTouchedThisTick={} touchReason={} paSpawnRemoveMirrorThisTick={} writeReason={} backend={} migrationStatus={}",
                identity.trackingKey(),
                identity.typeId(),
                region.getWorldX(),
                region.getWorldZ(),
                snapshot.x(),
                snapshot.z(),
                previous == null ? "none" : "(" + previous.x() + ", " + previous.z() + ")",
                delta,
                speed,
                movement == null ? "unavailable" : "(" + movement.x + ", " + movement.y + ")",
                region.getMaxSpeed(),
                touchedThisTick,
                touchedThisTick ? lastTouchReason : "none",
                wroteThisTick,
                wroteThisTick ? lastWriteReason : "none",
                status.currentBackend(),
                status.migrationStatus()
        );
    }

    public static void clear() {
        PREVIOUS_POSITIONS.clear();
        trackedKey = null;
        lastTouchGameTime = Long.MIN_VALUE;
        lastTouchReason = "none";
        lastWriteGameTime = Long.MIN_VALUE;
        lastWriteReason = "none";
    }

    private static boolean isSevere(String typeId) {
        String lower = safe(typeId);
        return lower.contains("severe") || lower.contains("cumulonimbus") || lower.contains("nimbus");
    }

    private static double distance(double ax, double az, double bx, double bz) {
        double dx = ax - bx;
        double dz = az - bz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private record PositionSample(double x, double z, long gameTime) {
    }
}
