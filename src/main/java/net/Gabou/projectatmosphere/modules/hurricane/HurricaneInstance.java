package net.Gabou.projectatmosphere.modules.hurricane;

import net.Gabou.projectatmosphere.modules.core.WindVector;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Represents a server-side hurricane event.
 */
public class HurricaneInstance {
    public static final ResourceLocation HURRICANE_CLOUD_TYPE_ID = ResourceLocation.fromNamespaceAndPath("simpleclouds", "hurricane");

    public final UUID id;
    public Vec3 position;
    public final float radius;
    public final WindVector wind;
    public final HurricaneCategory category;

    private int ageTicks;
    private long lastAmbientWindCheck;
    private final long ambientWindIntervalMs = 2000L;

    public HurricaneInstance(Vec3 position, float radius, WindVector wind, HurricaneCategory category) {
        this.id = UUID.randomUUID();
        this.position = position;
        this.radius = radius;
        this.wind = wind;
        this.category = category;
    }

    public float getLifetimeSeconds() {
        return (float)this.ageTicks / 20.0F;
    }

    public int getAgeTicks() {
        return this.ageTicks;
    }

    public float getVisualOuterRadius() {
        return Math.max(this.radius * 5.5F, 180.0F + this.category.ordinal() * 24.0F);
    }

    public float getVisualEyeRadius() {
        float outer = this.getVisualOuterRadius();
        float ratio = 0.26F + this.category.ordinal() * 0.02F;
        return outer * ratio;
    }

    public float getVisualEdgeFade() {
        return Math.max(this.getVisualOuterRadius() * 0.18F, 16.0F);
    }

    public int getBandCount() {
        return 3 + Math.min(2, this.category.ordinal() / 2);
    }

    public float getBandWidth() {
        return Math.max(this.getVisualOuterRadius() * 0.24F, 28.0F);
    }

    public float getSpiralTightness() {
        return 0.18F + this.category.ordinal() * 0.02F;
    }

    public float getRotationSpeed() {
        return 0.015F + this.category.ordinal() * 0.0035F;
    }

    public float getRotationPhase() {
        return this.ageTicks * this.getRotationSpeed();
    }

    public HurricaneRenderSnapshot createRenderSnapshot() {
        return new HurricaneRenderSnapshot(
                this.id,
                this.position.x,
                this.position.z,
                this.getVisualOuterRadius(),
                this.getVisualEyeRadius(),
                this.getVisualEdgeFade(),
                this.getBandCount(),
                this.getBandWidth(),
                this.getSpiralTightness(),
                this.getRotationPhase(),
                this.getRotationSpeed(),
                HURRICANE_CLOUD_TYPE_ID,
                this.ageTicks
        );
    }

    public void tick(Level level) {
        if (level.isClientSide) {
            return;
        }
        this.ageTicks++;
        long now = System.currentTimeMillis();
        if (now - this.lastAmbientWindCheck >= this.ambientWindIntervalMs) {
            this.lastAmbientWindCheck = now;
            this.applyAmbientWind((ServerLevel)level);
        }
    }

    private void applyAmbientWind(ServerLevel level) {
        double influence = this.radius;
        AABB box = new AABB(
                this.position.x - influence, this.position.y - 5,
                this.position.z - influence, this.position.x + influence,
                this.position.y + 50, this.position.z + influence
        );

        double windSpeed = this.wind.gustSpeed() * 0.02F;
        double vx = Math.cos(this.wind.angleRadians()) * windSpeed;
        double vz = Math.sin(this.wind.angleRadians()) * windSpeed;

        for (Entity entity : level.getEntities(null, box)) {
            entity.push(vx, 0, vz);
        }
    }
}
