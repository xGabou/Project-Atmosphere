package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.modules.hurricane.HurricaneRenderSnapshot;
import net.Gabou.projectatmosphere.modules.tornado.TornadoSnapshot;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Dependency-neutral client sink for severe-weather packets.
 * Optional backends install their consumers only after their mod-presence check.
 */
public final class SevereWeatherClientPacketHandlers {
    private static final Consumer<TornadoSpawn> NO_SPAWN = ignored -> { };
    private static final Consumer<UUID> NO_REMOVE = ignored -> { };
    private static final Consumer<List<TornadoSnapshot>> NO_TORNADO_SYNC = ignored -> { };
    private static final Consumer<List<HurricaneRenderSnapshot>> NO_HURRICANE_SYNC = ignored -> { };

    private static volatile Consumer<TornadoSpawn> tornadoSpawn = NO_SPAWN;
    private static volatile Consumer<UUID> tornadoRemove = NO_REMOVE;
    private static volatile Consumer<List<TornadoSnapshot>> tornadoSync = NO_TORNADO_SYNC;
    private static volatile Consumer<List<HurricaneRenderSnapshot>> hurricaneSync = NO_HURRICANE_SYNC;

    private SevereWeatherClientPacketHandlers() {
    }

    public static void install(
            Consumer<TornadoSpawn> spawnHandler,
            Consumer<UUID> removeHandler,
            Consumer<List<TornadoSnapshot>> tornadoSyncHandler,
            Consumer<List<HurricaneRenderSnapshot>> hurricaneSyncHandler
    ) {
        tornadoSpawn = Objects.requireNonNull(spawnHandler, "spawnHandler");
        tornadoRemove = Objects.requireNonNull(removeHandler, "removeHandler");
        tornadoSync = Objects.requireNonNull(tornadoSyncHandler, "tornadoSyncHandler");
        hurricaneSync = Objects.requireNonNull(hurricaneSyncHandler, "hurricaneSyncHandler");
    }

    public static void spawnTornado(TornadoSpawn spawn) {
        tornadoSpawn.accept(spawn);
    }

    public static void removeTornado(UUID id) {
        tornadoRemove.accept(id);
    }

    public static void syncTornadoes(List<TornadoSnapshot> snapshots) {
        tornadoSync.accept(List.copyOf(snapshots));
    }

    public static void syncHurricanes(List<HurricaneRenderSnapshot> snapshots) {
        hurricaneSync.accept(List.copyOf(snapshots));
    }

    public static void reset() {
        tornadoSpawn = NO_SPAWN;
        tornadoRemove = NO_REMOVE;
        tornadoSync = NO_TORNADO_SYNC;
        hurricaneSync = NO_HURRICANE_SYNC;
    }

    public record TornadoSpawn(
            UUID id,
            Vec3 position,
            float radius,
            WindVector wind,
            float bottomY,
            float height
    ) {
        public TornadoSpawn {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(wind, "wind");
        }
    }
}
