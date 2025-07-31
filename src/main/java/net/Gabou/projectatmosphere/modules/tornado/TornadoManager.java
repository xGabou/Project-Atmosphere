package net.Gabou.projectatmosphere.modules.tornado;

import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudSpawningConfig;
import net.Gabou.projectatmosphere.compat.SimpleCloudsCompat;
import net.Gabou.projectatmosphere.util.BiomeInstanceKey;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class TornadoManager {
    private static final List<TornadoInstance> ACTIVE_TORNADOES = new ArrayList<>();
    private static float shaderTime = 0.0f;

    public static void spawn(Vec3 pos, float radius) {
        ACTIVE_TORNADOES.add(new TornadoInstance(pos, radius));
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
        shaderTime += 0.05f;
    }

}
