package net.Gabou.projectatmosphere.modules.tornado;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import net.Gabou.projectatmosphere.modules.core.WindVector;

public class TornadoInstance {
    public Vec3 position;
    public final long spawnTime;
    public final float radius;
    public final WindVector wind;

    public TornadoInstance(Vec3 position, float radius, WindVector wind) {
        this.position = position;
        this.radius = radius;
        this.wind = wind;
        this.spawnTime = System.currentTimeMillis();
    }

    public float getLifetimeSeconds() {
        return (System.currentTimeMillis() - spawnTime) / 1000f;
    }
}
