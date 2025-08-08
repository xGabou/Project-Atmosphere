package net.Gabou.projectatmosphere.modules.tornado;

import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.Gabou.projectatmosphere.network.NetworkHandler;
import net.Gabou.projectatmosphere.network.SpawnTornadoPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class TornadoManager {
    private static final List<TornadoInstance> ACTIVE_TORNADOES = new ArrayList<>();
    private static float shaderTime = 0.0f;

    public static void spawn(Vec3 pos, float radius, WindVector wind) {
        ACTIVE_TORNADOES.add(new TornadoInstance(pos, radius, wind));
    }

    public static void spawn(Vec3 pos, float radius) {
        spawn(pos, radius, WindVector.fromBase(0, 0));
    }

    public static void spawnServer(ServerLevel level, Vec3 pos, float radius, WindVector wind) {
        spawn(pos, radius, wind);
        NetworkHandler.CHANNEL.send(PacketDistributor.ALL.noArg(), new SpawnTornadoPacket(pos, radius, wind));
    }

    public static List<TornadoInstance> getActiveTornadoes() {
        return ACTIVE_TORNADOES;
    }

    public static float getShaderTime() {
        return shaderTime;
    }

    public static void tick(Level level) {
        ACTIVE_TORNADOES.removeIf(tornado -> tornado.getLifetimeSeconds() > 600);
        for (TornadoInstance tornado : ACTIVE_TORNADOES) {
            float speed = tornado.wind.baseSpeed() * 0.05f;
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
