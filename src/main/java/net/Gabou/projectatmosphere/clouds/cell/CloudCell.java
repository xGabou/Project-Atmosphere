package net.Gabou.projectatmosphere.clouds.cell;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.UUID;

/**
 * One immutable cloud cell: the primary tracked entity of the dynamic cloud
 * system. Cells carry identity plus continuous shape/behavior properties. The
 * GPU amplifies cells into detailed volumes; the CPU owns identity, lifecycle,
 * merging, splitting, and classification.
 */
public record CloudCell(
        UUID id,
        long seed,
        String dimensionId,
        double x,
        double z,
        float baseY,
        float topY,
        float radiusMajor,
        float radiusMinor,
        float orientationRadians,
        float density,
        float edgeSoftness,
        float energy,
        float rotation,
        float funnelStrength,
        float funnelGroundY,
        Vec3 wind,
        CloudCellLifecyclePhase phase,
        CloudCellClassification classification,
        long ageTicks,
        long worldTime
) {
    public CloudCell {
        id = Objects.requireNonNull(id, "id");
        dimensionId = dimensionId == null || dimensionId.isBlank() ? "minecraft:overworld" : dimensionId;
        x = finite(x, 0.0D);
        z = finite(z, 0.0D);
        baseY = finite(baseY, 128.0F);
        topY = Math.max(baseY + 1.0F, finite(topY, baseY + 24.0F));
        radiusMajor = Math.max(1.0F, finite(radiusMajor, 60.0F));
        radiusMinor = Math.max(1.0F, Math.min(radiusMajor, finite(radiusMinor, radiusMajor)));
        orientationRadians = finite(orientationRadians, 0.0F);
        density = clamp01(density);
        edgeSoftness = clamp01(edgeSoftness);
        energy = clamp01(energy);
        rotation = clamp01(rotation);
        funnelStrength = clamp01(funnelStrength);
        funnelGroundY = finite(funnelGroundY, 64.0F);
        wind = wind == null ? Vec3.ZERO : wind;
        phase = phase == null ? CloudCellLifecyclePhase.MATURE : phase;
        classification = classification == null ? CloudCellClassification.UNCLASSIFIED : classification;
        ageTicks = Math.max(0L, ageTicks);
    }

    public float verticalExtent() {
        return topY - baseY;
    }

    /** Vertical development ratio: tall convective cells score above ~1.0. */
    public float verticalExtentRatio() {
        return verticalExtent() / Math.max(radiusMajor, 1.0F);
    }

    public float footprintArea() {
        return (float) (Math.PI * radiusMajor * radiusMinor);
    }

    public boolean isVisuallyRelevant() {
        return density > 0.004F && radiusMajor > 2.0F;
    }

    public double distanceSqrTo(double worldX, double worldZ) {
        double dx = x - worldX;
        double dz = z - worldZ;
        return dx * dx + dz * dz;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(id);
        buffer.writeLong(seed);
        buffer.writeUtf(dimensionId);
        buffer.writeDouble(x);
        buffer.writeDouble(z);
        buffer.writeFloat(baseY);
        buffer.writeFloat(topY);
        buffer.writeFloat(radiusMajor);
        buffer.writeFloat(radiusMinor);
        buffer.writeFloat(orientationRadians);
        buffer.writeFloat(density);
        buffer.writeFloat(edgeSoftness);
        buffer.writeFloat(energy);
        buffer.writeFloat(rotation);
        buffer.writeFloat(funnelStrength);
        buffer.writeFloat(funnelGroundY);
        buffer.writeFloat((float) wind.x());
        buffer.writeFloat((float) wind.y());
        buffer.writeFloat((float) wind.z());
        buffer.writeVarInt(phase.ordinal());
        buffer.writeVarInt(classification.ordinal());
        buffer.writeVarLong(ageTicks);
        buffer.writeVarLong(worldTime);
    }

    public static CloudCell decode(FriendlyByteBuf buffer) {
        UUID id = buffer.readUUID();
        long seed = buffer.readLong();
        String dimensionId = buffer.readUtf();
        double x = buffer.readDouble();
        double z = buffer.readDouble();
        float baseY = buffer.readFloat();
        float topY = buffer.readFloat();
        float radiusMajor = buffer.readFloat();
        float radiusMinor = buffer.readFloat();
        float orientation = buffer.readFloat();
        float density = buffer.readFloat();
        float edgeSoftness = buffer.readFloat();
        float energy = buffer.readFloat();
        float rotation = buffer.readFloat();
        float funnelStrength = buffer.readFloat();
        float funnelGroundY = buffer.readFloat();
        Vec3 wind = new Vec3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
        CloudCellLifecyclePhase phase = CloudCellLifecyclePhase.byOrdinal(buffer.readVarInt());
        CloudCellClassification classification = CloudCellClassification.byOrdinal(buffer.readVarInt());
        long ageTicks = buffer.readVarLong();
        long worldTime = buffer.readVarLong();
        return new CloudCell(
                id, seed, dimensionId, x, z, baseY, topY,
                radiusMajor, radiusMinor, orientation,
                density, edgeSoftness, energy, rotation,
                funnelStrength, funnelGroundY,
                wind, phase, classification, ageTicks, worldTime
        );
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, finite(value, 0.0F)));
    }

    private static float finite(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }
}
