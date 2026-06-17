package net.Gabou.projectatmosphere.modules.tornado;

import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import dev.nonamecrackers2.simpleclouds.common.world.SpawnRegion;
import net.Gabou.projectatmosphere.config.AtmoCommonConfig;
import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.network.NetworkHandler;
import net.Gabou.projectatmosphere.network.SpawnTornadoPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TornadoManager {
    private static final List<TornadoInstance> ACTIVE_TORNADOES = new ArrayList<>();

    private static float shaderTime = 0.0f;

    public static void spawn(Vec3 pos, float radius, WindVector wind, Level level) {
        SpawnRegion temporaryRegion = new SpawnRegion((int)pos.x,(int) pos.z,(int) radius);
        for (CloudRegion cloud : CloudManager.get(level).getClouds()) {
            if (cloud.intersects(temporaryRegion)) {
                if (!AtmoCommonConfig.ENABLE_TORNADOES.get()) return;
                ACTIVE_TORNADOES.add(new TornadoInstance(pos, radius, wind, cloud));
                break;
            }
        }


    }
    @OnlyIn(Dist.CLIENT)
    public static void spawnClient(Vec3 pos, float radius, WindVector wind) {
        if (!AtmoCommonConfig.ENABLE_TORNADOES.get()) return;
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        spawn(pos, radius, wind, level);
    }


//    public static void spawn(Vec3 pos, float radius) {
//        spawn(pos, radius, WindVector.fromBase(0, 0));
//    }

    public static void spawnServer(ServerLevel level, Vec3 pos, float radius, WindVector wind) {
        spawn(pos, radius, wind, level);
        SpawnTornadoPacket packet = new SpawnTornadoPacket(pos, radius, wind.baseSpeed(), wind.angleRadians(), wind.gustSpeed());
        for (ServerPlayer player : level.players()) {
            player.connection.send(packet);
        }
    }

    public static List<TornadoInstance> getActiveTornadoes() {
        return ACTIVE_TORNADOES;
    }
    public static void removeTornado(TornadoInstance tornado) {
        ACTIVE_TORNADOES.remove(tornado);
    }
    public static void clearTornadoes() {
        ACTIVE_TORNADOES.clear();
    }

    public static void removeClientTornado(UUID id) {
        // This legacy TornadoInstance has no stable UUID field; clearing prevents stale client visuals after removal packets.
        ACTIVE_TORNADOES.clear();
    }

    @OnlyIn(Dist.CLIENT)
    public static void applyClientSnapshots(List<TornadoSnapshot> snapshots) {
        ACTIVE_TORNADOES.clear();
        if (!AtmoCommonConfig.ENABLE_TORNADOES.get() || snapshots == null) {
            return;
        }
        for (TornadoSnapshot snapshot : snapshots) {
            ACTIVE_TORNADOES.add(new TornadoInstance(
                    snapshot.position(),
                    snapshot.radius(),
                    new WindVector(snapshot.windSpeed(), snapshot.windAngle(), snapshot.windGust()),
                    null
            ));
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static float getShaderTime() {
        return shaderTime;
    }
    public static void tick(Level level) {
        ACTIVE_TORNADOES.removeIf(tornado -> tornado.getLifetimeSeconds() > 600);
        for (TornadoInstance tornado : ACTIVE_TORNADOES) {
            float speed = tornado.wind.baseSpeed() * 0.2f;
            tornado.position = tornado.position.add(
                    Math.cos(tornado.wind.angleRadians()) * speed,
                    0,
                    Math.sin(tornado.wind.angleRadians()) * speed);
            tornado.tick(level);
        }
        if (level.isClientSide) {
            shaderTime += 0.05f;
        }
    }
}
