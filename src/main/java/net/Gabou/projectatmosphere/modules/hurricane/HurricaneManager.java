package net.Gabou.projectatmosphere.modules.hurricane;

import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.network.NetworkHandler;
import net.Gabou.projectatmosphere.network.SyncHurricanesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class HurricaneManager {
    private static final List<HurricaneInstance> SERVER_HURRICANES = new ArrayList<>();
    private static final List<HurricaneInstance> CLIENT_HURRICANES = new ArrayList<>();
    private static final int SYNC_INTERVAL_TICKS = 10;

    public static void spawnServer(ServerLevel level, Vec3 pos, float radius, WindVector wind, HurricaneCategory category) {
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
}
