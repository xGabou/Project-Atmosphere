package net.Gabou.projectatmosphere.modules.hurricane;

import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Represents a server-side hurricane event.
 */
public class HurricaneInstance {
    public static final ResourceLocation HURRICANE_CLOUD_TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("simpleclouds", "itty_bitty");

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

    public float getAnchorY() {
        return (float) position.y;
    }

    public float getVisualCoreRadius() {
        return Math.max(16.0F, radius * 0.28F);
    }

    public float getVisualStormExtentRadius() {
        return radius;
    }

    public float getVisualEyeRadius() {
        return Math.max(8.0F, radius * 0.12F);
    }

    public float getVisualEdgeFade() {
        return 0.2F;
    }

    public int getBandCount() {
        return Math.max(3, category.ordinal() + 3);
    }

    public float getBandWidth() {
        return Math.max(8.0F, radius * 0.08F);
    }

    public float getSpiralTightness() {
        return 0.85F;
    }

    public float getRotationPhase() {
        return getLifetimeSeconds() * 0.2F;
    }

    public float getRotationSpeed() {
        return Math.max(0.01F, wind.gustSpeed() * 0.001F);
    }

    public float getTransitionStart() {
        return 0.0F;
    }

    public float getTransitionEnd() {
        return 1.0F;
    }

    public HurricaneRenderSnapshot createRenderSnapshot() {
        float ageTicks = getLifetimeSeconds() * 20.0F;
        float categoryScale = (category.ordinal() + 1) / 5.0F;
        return new HurricaneRenderSnapshot(
                UUID.nameUUIDFromBytes(("legacy-hurricane:" + spawnTime).getBytes(StandardCharsets.UTF_8)),
                position.x,
                position.z,
                (float) position.y,
                Math.max(16.0F, radius * 0.28F),
                radius,
                Math.max(8.0F, radius * 0.12F),
                0.2F,
                Math.max(3, category.ordinal() + 3),
                Math.max(8.0F, radius * 0.08F),
                0.85F,
                ageTicks * 0.01F,
                Math.max(0.01F, wind.gustSpeed() * 0.001F),
                0.0F,
                1.0F,
                categoryScale,
                HURRICANE_CLOUD_TYPE_ID,
                (int) ageTicks
        );
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
