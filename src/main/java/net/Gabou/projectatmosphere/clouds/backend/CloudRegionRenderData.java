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
            int debugColorOrTint
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
                debugColorOrTint
        );
    }
}