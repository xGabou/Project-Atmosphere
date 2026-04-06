package net.Gabou.projectatmosphere.modules.hurricane;

import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.network.NetworkHandler;
import net.Gabou.projectatmosphere.network.SyncHurricaneStatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class HurricaneManager {
    private static final int SYNC_INTERVAL_TICKS = 10;
    private static final List<HurricaneInstance> ACTIVE_HURRICANES = new ArrayList<>();
    private static boolean dirty = true;

    public static void spawnServer(ServerLevel level, Vec3 pos, float radius, WindVector wind, HurricaneCategory category) {
        ACTIVE_HURRICANES.add(new HurricaneInstance(pos, radius, wind, category));
        dirty = true;
        syncToDimension(level);
    }

    public static void tick(ServerLevel level) {
        if (ACTIVE_HURRICANES.removeIf(h -> h.getLifetimeSeconds() > 1200.0F)) {
            dirty = true;
        }
        for (HurricaneInstance hurricane : ACTIVE_HURRICANES) {
            float speed = hurricane.wind.baseSpeed() * 0.01F;
            hurricane.position = hurricane.position.add(
                    Math.cos(hurricane.wind.angleRadians()) * speed,
                    0,
                    Math.sin(hurricane.wind.angleRadians()) * speed);
            hurricane.tick(level);
        }
        if (dirty || level.getGameTime() % SYNC_INTERVAL_TICKS == 0L) {
            syncToDimension(level);
            dirty = false;
        }
    }

    public static List<HurricaneInstance> getActiveHurricanes() {
        return Collections.unmodifiableList(ACTIVE_HURRICANES);
    }

    public static void clearHurricanes() {
        ACTIVE_HURRICANES.clear();
        dirty = true;
    }

    public static void removeHurricane(HurricaneInstance hurricane) {
        if (ACTIVE_HURRICANES.remove(hurricane)) {
            dirty = true;
        }
    }

    public static void syncToPlayer(ServerPlayer player) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), createSyncPacket());
    }

    private static void syncToDimension(ServerLevel level) {
        NetworkHandler.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), createSyncPacket());
    }

    private static SyncHurricaneStatePacket createSyncPacket() {
        return new SyncHurricaneStatePacket(ACTIVE_HURRICANES.stream()
                .map(HurricaneInstance::createRenderSnapshot)
                .collect(Collectors.toList()));
    }
}
