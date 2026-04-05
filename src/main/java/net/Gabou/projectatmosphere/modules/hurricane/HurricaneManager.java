package net.Gabou.projectatmosphere.modules.hurricane;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.weather.StormShieldManager;
import net.Gabou.projectatmosphere.network.NetworkHandler;
import net.Gabou.projectatmosphere.network.SyncHurricanesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class HurricaneManager {
    private static final List<HurricaneInstance> SERVER_HURRICANES = new ArrayList<>();
    private static final List<HurricaneInstance> CLIENT_HURRICANES = new ArrayList<>();
    private static final int SYNC_INTERVAL_TICKS = 10;
    private static final int CLIENT_DEBUG_LOG_INTERVAL_TICKS = 40;
    private static int clientDebugTickCounter = 0;
    private static int lastLoggedClientHurricaneCount = Integer.MIN_VALUE;
    private static int lastLoggedSnapshotCount = Integer.MIN_VALUE;
    private static long lastLoggedSnapshotAgeBucket = Long.MIN_VALUE;

    public static void spawnServer(ServerLevel level, Vec3 pos, float radius, WindVector wind, HurricaneCategory category) {
        if (StormShieldManager.isProtected(level, pos)) {
            return;
        }
        SERVER_HURRICANES.add(new HurricaneInstance(pos, radius, wind, category));
        broadcastSnapshots();
    }

    public static void tick(ServerLevel level) {
        long gameTime = level.getGameTime();
        Iterator<HurricaneInstance> iterator = SERVER_HURRICANES.iterator();
        while (iterator.hasNext()) {
            HurricaneInstance hurricane = iterator.next();
            hurricane.tickServer(level, gameTime);
            if (hurricane.isDead()) {
                iterator.remove();
            }
        }
        if (gameTime % SYNC_INTERVAL_TICKS == 0L) {
            broadcastSnapshots();
        }
    }

    public static void tickClient() {
        for (HurricaneInstance hurricane : CLIENT_HURRICANES) {
            hurricane.tickClient();
        }
        maybeLogClientState(false);
    }

    public static List<HurricaneInstance> getActiveHurricanes() {
        return Collections.unmodifiableList(SERVER_HURRICANES);
    }

    public static List<HurricaneInstance> getClientHurricanes() {
        return Collections.unmodifiableList(CLIENT_HURRICANES);
    }

    public static void clearHurricanes() {
        SERVER_HURRICANES.clear();
        broadcastSnapshots();
    }

    public static void clearClientHurricanes() {
        CLIENT_HURRICANES.clear();
        maybeLogClientState(true);
    }

    public static void removeHurricane(HurricaneInstance hurricane) {
        SERVER_HURRICANES.remove(hurricane);
        broadcastSnapshots();
    }

    public static void applyClientSnapshots(List<HurricaneSnapshot> snapshots) {
        List<HurricaneInstance> next = new ArrayList<>(snapshots.size());
        for (HurricaneSnapshot snapshot : snapshots) {
            HurricaneInstance existing = findClient(snapshot.id());
            if (existing == null) {
                existing = new HurricaneInstance(
                        snapshot.id(),
                        snapshot.position(),
                        snapshot.radius(),
                        new WindVector(snapshot.windSpeed(), snapshot.windAngle(), snapshot.windGust()),
                        snapshot.category()
                );
            }
            existing.applySnapshot(snapshot);
            next.add(existing);
        }
        CLIENT_HURRICANES.clear();
        CLIENT_HURRICANES.addAll(next);
        maybeLogClientSnapshotReceive(snapshots);
        maybeLogClientState(true);
    }

    public static HurricaneInstance getPrimaryClientHurricane() {
        return CLIENT_HURRICANES.isEmpty() ? null : CLIENT_HURRICANES.get(0);
    }

    public static HurricaneInstance getPrimaryServerHurricane() {
        return SERVER_HURRICANES.isEmpty() ? null : SERVER_HURRICANES.get(0);
    }

    private static HurricaneInstance findClient(UUID id) {
        for (HurricaneInstance hurricane : CLIENT_HURRICANES) {
            if (hurricane.getId().equals(id)) {
                return hurricane;
            }
        }
        return null;
    }

    private static void broadcastSnapshots() {
        List<HurricaneSnapshot> snapshots = new ArrayList<>(SERVER_HURRICANES.size());
        for (HurricaneInstance hurricane : SERVER_HURRICANES) {
            snapshots.add(hurricane.snapshot());
        }
        NetworkHandler.CHANNEL.send(PacketDistributor.ALL.noArg(), new SyncHurricanesPacket(snapshots));
    }

    private static void maybeLogClientSnapshotReceive(List<HurricaneSnapshot> snapshots) {
        if (!ProjectAtmosphere.DEBUG_MODE) {
            return;
        }

        int snapshotCount = snapshots.size();
        long ageBucket = snapshotCount > 0 ? snapshots.get(0).ageTicks() / CLIENT_DEBUG_LOG_INTERVAL_TICKS : -1L;
        if (snapshotCount == lastLoggedSnapshotCount && ageBucket == lastLoggedSnapshotAgeBucket) {
            return;
        }

        lastLoggedSnapshotCount = snapshotCount;
        lastLoggedSnapshotAgeBucket = ageBucket;

        if (snapshots.isEmpty()) {
            ProjectAtmosphere.LOGGER.info("[HurricaneDebug] client snapshots=0");
            return;
        }

        HurricaneSnapshot first = snapshots.get(0);
        ProjectAtmosphere.LOGGER.info(
                "[HurricaneDebug] client snapshots={} firstId={} phase={} pos=({}, {}, {}) radius={} eyeRadius={} intensity={} ageTicks={}",
                snapshotCount,
                first.id(),
                first.phase(),
                format(first.position().x),
                format(first.position().y),
                format(first.position().z),
                format(first.radius()),
                format(first.eyewallRadius()),
                format(first.normalizedIntensity()),
                first.ageTicks()
        );
    }

    private static void maybeLogClientState(boolean force) {
        if (!ProjectAtmosphere.DEBUG_MODE) {
            return;
        }

        clientDebugTickCounter++;
        int hurricaneCount = CLIENT_HURRICANES.size();
        boolean countsChanged = hurricaneCount != lastLoggedClientHurricaneCount;
        if (!force && !countsChanged && clientDebugTickCounter % CLIENT_DEBUG_LOG_INTERVAL_TICKS != 0) {
            return;
        }

        lastLoggedClientHurricaneCount = hurricaneCount;

        if (CLIENT_HURRICANES.isEmpty()) {
            ProjectAtmosphere.LOGGER.info("[HurricaneDebug] client hurricanes=0 renderMode=custom_volume");
            return;
        }

        HurricaneInstance first = CLIENT_HURRICANES.get(0);
        HurricaneRenderDescriptor descriptor = first.getRenderDescriptor(1.0F);
        ProjectAtmosphere.LOGGER.info(
                "[HurricaneDebug] client hurricanes={} firstId={} phase={} pos=({}, {}, {}) radius={} visualCloudRadius={} intensity={} lifetimeSeconds={} renderMode=custom_volume eyeRadius={} torusMajorRadius={} torusMinorRadius={} bandStart={} bandEnd={} volumeHeight={}",
                hurricaneCount,
                first.getId(),
                first.getPhase(),
                format(first.position.x),
                format(first.position.y),
                format(first.position.z),
                format(first.radius),
                format(first.getVisualCloudRadius()),
                format(first.getNormalizedIntensity()),
                format(first.getLifetimeSeconds()),
                format(descriptor.eyeRadiusWorld()),
                format(descriptor.canopyRadiusWorld()),
                format(descriptor.eyewallThicknessWorld()),
                format(descriptor.bandStartRadiusWorld()),
                format(descriptor.bandEndRadiusWorld()),
                format(descriptor.volumeHeightWorld())
        );
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
