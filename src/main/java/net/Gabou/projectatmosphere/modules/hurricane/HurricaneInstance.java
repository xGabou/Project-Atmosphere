package net.Gabou.projectatmosphere.modules.hurricane;

import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;

/**
 * Represents a server-side hurricane event.
 */
public class HurricaneInstance {

    public Vec3 position;
    public final long spawnTime;
    public final float radius;
    public final WindVector wind;
    public final HurricaneCategory category;

    private long lastAmbientWindCheck = 0L;
    private final long ambientWindIntervalMs = 2000L;

    public HurricaneInstance(Vec3 position, float radius, WindVector wind, HurricaneCategory category) {
        this.position = position;
        this.radius = radius;
        this.wind = wind;
        this.category = category;
        this.spawnTime = System.currentTimeMillis();
    }

    public float getLifetimeSeconds() {
        return (System.currentTimeMillis() - spawnTime) / 1000f;
    }

    public void tick(Level level) {
        if (level.isClientSide) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastAmbientWindCheck >= ambientWindIntervalMs) {
            lastAmbientWindCheck = now;
            applyAmbientWind((ServerLevel) level);
        }
    }

    private void applyAmbientWind(ServerLevel level) {
        double influence = radius;
        AABB box = new AABB(
                position.x - influence, position.y - 5,
                position.z - influence, position.x + influence,
                position.y + 50, position.z + influence
        );

        double windSpeed = wind.gustSpeed() * 0.02f;
        double vx = Math.cos(wind.angleRadians()) * windSpeed;
        double vz = Math.sin(wind.angleRadians()) * windSpeed;

        for (Entity entity : level.getEntities(null, box)) {
            entity.push(vx, 0, vz);
        }
    }
}
