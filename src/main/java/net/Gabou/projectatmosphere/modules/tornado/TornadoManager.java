package net.Gabou.projectatmosphere.modules.tornado;

import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class TornadoManager {
    private static final List<TornadoInstance> ACTIVE_TORNADOES = new ArrayList<>();
    private static float shaderTime = 0.0f;

    public static void spawn(Vec3 pos, float radius, WindVector wind) {
        ACTIVE_TORNADOES.add(new TornadoInstance(pos, radius, wind));
    }

    public static void spawn(Vec3 pos, float radius) {
        spawn(pos, radius, new WindVector(0, 0));
    }

    public static List<TornadoInstance> getActiveTornadoes() {
        return ACTIVE_TORNADOES;
    }

    public static float getShaderTime() {
        return shaderTime;
    }

    public static void tick() {
        // Remove tornados after 20 seconds
        ACTIVE_TORNADOES.removeIf(tornado -> tornado.getLifetimeSeconds() > 20);
        for (TornadoInstance tornado : ACTIVE_TORNADOES) {
            float speed = tornado.wind.speed() * 0.05f;
            tornado.position = tornado.position.add(
                    Math.cos(tornado.wind.angleRadians()) * speed,
                    0,
                    Math.sin(tornado.wind.angleRadians()) * speed);
        }
        shaderTime += 0.05f;
    }

}
