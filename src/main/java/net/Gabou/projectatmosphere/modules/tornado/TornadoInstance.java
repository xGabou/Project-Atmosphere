package net.Gabou.projectatmosphere.modules.tornado;

import net.minecraft.world.phys.Vec3;

import net.Gabou.projectatmosphere.modules.core.WindVector;

public class TornadoInstance {
    public Vec3 position;
    public final long spawnTime;
    public final float radius;
    public final WindVector wind;
    private float angularSpeed = 0.15f; // ~0.15 radians per tick ≈ 8.6 deg/tick


    public TornadoInstance(Vec3 position, float radius, WindVector wind) {
        this.position = position;
        this.radius = radius;
        this.wind = wind;
        this.spawnTime = System.currentTimeMillis();

    }
    public TornadoInstance(Vec3 position, float radius, WindVector wind,float angularSpeed) {
        this.position = position;
        this.radius = radius;
        this.wind = wind;
        this.spawnTime = System.currentTimeMillis();
        this.angularSpeed = angularSpeed;
    }

    public float getLifetimeSeconds() {
        return (System.currentTimeMillis() - spawnTime) / 1000f;
    }

    public float getTwist() {
        long elapsedMs = System.currentTimeMillis() - spawnTime;
        float elapsedTicks = elapsedMs / 50.0f; // Convert ms → ticks
        return elapsedTicks * angularSpeed;
    }
}
