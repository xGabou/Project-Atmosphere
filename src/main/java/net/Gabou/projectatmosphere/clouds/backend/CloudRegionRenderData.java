package net.Gabou.projectatmosphere.clouds.backend;

import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Donnée de rendu transportable pour une région de nuage PA.
 * Cette classe ne possède pas la simulation et ne fait aucun rendu.
 */
public final class CloudRegionRenderData {

    private final UUID regionId;
    private final String dimensionId;
    private final Vec3 center;

    private final float radius;
    private final float baseY;
    private final float topY;

    private final float density;
    private final float coverage;
    private final float edgeSoftness;

    private final boolean active;
    private final int debugColorOrTint;

    private final Vec3 previousCenter;
    private final Vec3 velocity;

    private final int ageTicks;
    private final int lifetimeTicks;

    private final float growth;
    private final float decay;

    public CloudRegionRenderData(
            UUID regionId,
            String dimensionId,
            Vec3 center,
            float radius,
            float baseY,
            float topY,
            float density,
            float coverage,
            float edgeSoftness,
            boolean active,
            int debugColorOrTint,
            Vec3 previousCenter,
            Vec3 velocity,
            int ageTicks,
            int lifetimeTicks,
            float growth,
            float decay
    ) {
        this.regionId = regionId;
        this.dimensionId = dimensionId;
        this.center = center;
        this.radius = radius;
        this.baseY = baseY;
        this.topY = topY;
        this.density = density;
        this.coverage = coverage;
        this.edgeSoftness = edgeSoftness;
        this.active = active;
        this.debugColorOrTint = debugColorOrTint;
        this.previousCenter = previousCenter;
        this.velocity = velocity;
        this.ageTicks = ageTicks;
        this.lifetimeTicks = lifetimeTicks;
        this.growth = growth;
        this.decay = decay;

    }

    public UUID getRegionId() {
        return regionId;
    }

    public String getDimensionId() {
        return dimensionId;
    }

    public Vec3 getCenter() {
        return center;
    }

    public float getRadius() {
        return radius;
    }

    public float getBaseY() {
        return baseY;
    }

    public float getTopY() {
        return topY;
    }

    public float getDensity() {
        return density;
    }

    public float getCoverage() {
        return coverage;
    }

    public float getEdgeSoftness() {
        return edgeSoftness;
    }

    public boolean isActive() {
        return active;
    }

    public int getDebugColorOrTint() {
        return debugColorOrTint;
    }

    /**
     * Écrit cette donnée transportable dans un buffer réseau.
     *
     * @param buffer buffer réseau cible
     */
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(regionId);
        buffer.writeUtf(dimensionId);

        buffer.writeDouble(center.x());
        buffer.writeDouble(center.y());
        buffer.writeDouble(center.z());

        buffer.writeFloat(radius);
        buffer.writeFloat(baseY);
        buffer.writeFloat(topY);

        buffer.writeFloat(density);
        buffer.writeFloat(coverage);
        buffer.writeFloat(edgeSoftness);

        buffer.writeBoolean(active);
        buffer.writeInt(debugColorOrTint);
        buffer.writeDouble(previousCenter.x());
        buffer.writeDouble(previousCenter.y());
        buffer.writeDouble(previousCenter.z());

        buffer.writeDouble(velocity.x());
        buffer.writeDouble(velocity.y());
        buffer.writeDouble(velocity.z());
        buffer.writeVarInt(ageTicks);
        buffer.writeVarInt(lifetimeTicks);
        buffer.writeFloat(growth);
        buffer.writeFloat(decay);
    }

    /**
     * Lit une donnée transportable depuis un buffer réseau.
     *
     * @param buffer buffer réseau source
     * @return donnée de région de nuage décodée
     */
    public static CloudRegionRenderData decode(FriendlyByteBuf buffer) {
        UUID regionId = buffer.readUUID();
        String dimensionId = buffer.readUtf();

        Vec3 center = new Vec3(
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble()
        );

        float radius = buffer.readFloat();
        float baseY = buffer.readFloat();
        float topY = buffer.readFloat();

        float density = buffer.readFloat();
        float coverage = buffer.readFloat();
        float edgeSoftness = buffer.readFloat();

        boolean active = buffer.readBoolean();
        int debugColorOrTint = buffer.readInt();

        Vec3 previousCenter = new Vec3(
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble()
        );

        Vec3 velocity = new Vec3(
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble()
        );

        int ageTicks = buffer.readVarInt();
        int lifetimeTicks = buffer.readVarInt();

        float growth = buffer.readFloat();
        float decay = buffer.readFloat();

        return new CloudRegionRenderData(
                regionId,
                dimensionId,
                center,
                radius,
                baseY,
                topY,
                density,
                coverage,
                edgeSoftness,
                active,
                debugColorOrTint,
                previousCenter,
                velocity,
                ageTicks,
                lifetimeTicks,
                growth,
                decay
        );
    }

    public Vec3 getPreviousCenter() {
        return previousCenter;
    }

    public Vec3 getVelocity() {
        return velocity;
    }

    public int getAgeTicks() {
        return ageTicks;
    }

    public int getLifetimeTicks() {
        return lifetimeTicks;
    }

    public float getGrowth() {
        return growth;
    }

    public float getDecay() {
        return decay;
    }
}