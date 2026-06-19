package net.Gabou.projectatmosphere.api.common.cloud.region;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable-ish transport description of a tornado attached to a cloud region.
 * This is a serialized data contract for clients and controllers, not a simulation owner.
 */
public class TornadoDescriptor {
    public static final String KEY_UUID = "uuid";
    public static final String KEY_CONTROLLER = "controller";
    public static final String KEY_OFFSET_X = "offset_x";
    public static final String KEY_OFFSET_Z = "offset_z";
    public static final String KEY_VELOCITY_X = "velocity_x";
    public static final String KEY_VELOCITY_Z = "velocity_z";
    public static final String KEY_RADIUS = "radius";
    public static final String KEY_HEIGHT = "height";
    public static final String KEY_BOTTOM = "bottom";

    private final UUID id;
    private final ResourceLocation controllerId;
    private float offsetX;
    private float offsetZ;
    private float velocityX;
    private float velocityZ;
    private float radius;
    private float height;
    private float bottomY;

    public TornadoDescriptor(UUID id,
                             ResourceLocation controllerId,
                             float offsetX,
                             float offsetZ,
                             float velocityX,
                             float velocityZ,
                             float radius,
                             float bottomY,
                             float height) {
        this.id = id;
        this.controllerId = controllerId;
        this.offsetX = offsetX;
        this.offsetZ = offsetZ;
        this.velocityX = velocityX;
        this.velocityZ = velocityZ;
        this.radius = radius;
        this.bottomY = bottomY;
        this.height = height;
    }

    public TornadoDescriptor(ResourceLocation controllerId,
                             float offsetX,
                             float offsetZ,
                             float velocityX,
                             float velocityZ,
                             float radius,
                             float bottomY,
                             float height) {
        this(UUID.randomUUID(), controllerId, offsetX, offsetZ, velocityX, velocityZ, radius, bottomY, height);
    }

    public UUID getId() {
        return this.id;
    }

    public ResourceLocation getControllerId() {
        return this.controllerId;
    }

    public float getOffsetX() {
        return this.offsetX;
    }

    public void setOffsetX(float offsetX) {
        this.offsetX = offsetX;
    }

    public float getOffsetZ() {
        return this.offsetZ;
    }

    public void setOffsetZ(float offsetZ) {
        this.offsetZ = offsetZ;
    }

    public float getVelocityX() {
        return this.velocityX;
    }

    public void setVelocityX(float velocityX) {
        this.velocityX = velocityX;
    }

    public float getVelocityZ() {
        return this.velocityZ;
    }

    public void setVelocityZ(float velocityZ) {
        this.velocityZ = velocityZ;
    }

    public float getRadius() {
        return this.radius;
    }

    public void setRadius(float radius) {
        this.radius = radius;
    }

    public float getBottomY() {
        return this.bottomY;
    }

    public void setBottomY(float bottomY) {
        this.bottomY = bottomY;
    }

    public float getHeight() {
        return this.height;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    /**
     * Descriptor movement is owned by TornadoInstance. Region ticks keep this
     * method for compatibility, but they must not integrate a second motion path.
     */
    public void tick(float regionDeltaX, float regionDeltaZ) {
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(KEY_UUID, this.id);
        if (this.controllerId != null) {
            tag.putString(KEY_CONTROLLER, this.controllerId.toString());
        }
        tag.putFloat(KEY_OFFSET_X, this.offsetX);
        tag.putFloat(KEY_OFFSET_Z, this.offsetZ);
        tag.putFloat(KEY_VELOCITY_X, this.velocityX);
        tag.putFloat(KEY_VELOCITY_Z, this.velocityZ);
        tag.putFloat(KEY_RADIUS, this.radius);
        tag.putFloat(KEY_HEIGHT, this.height);
        tag.putFloat(KEY_BOTTOM, this.bottomY);
        return tag;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.id);
        buf.writeNullable(this.controllerId, FriendlyByteBuf::writeResourceLocation);
        buf.writeFloat(this.offsetX);
        buf.writeFloat(this.offsetZ);
        buf.writeFloat(this.velocityX);
        buf.writeFloat(this.velocityZ);
        buf.writeFloat(this.radius);
        buf.writeFloat(this.height);
        buf.writeFloat(this.bottomY);
    }

    public static TornadoDescriptor read(FriendlyByteBuf buf) {
        UUID uuid = buf.readUUID();
        ResourceLocation controller = buf.readNullable(FriendlyByteBuf::readResourceLocation);
        float offsetX = buf.readFloat();
        float offsetZ = buf.readFloat();
        float velocityX = buf.readFloat();
        float velocityZ = buf.readFloat();
        float radius = buf.readFloat();
        float height = buf.readFloat();
        float bottom = buf.readFloat();
        return new TornadoDescriptor(uuid, controller, offsetX, offsetZ, velocityX, velocityZ, radius, bottom, height);
    }

    public static TornadoDescriptor fromTag(CompoundTag tag) {
        UUID uuid = tag.hasUUID(KEY_UUID) ? tag.getUUID(KEY_UUID) : UUID.randomUUID();
        ResourceLocation controller = tag.contains(KEY_CONTROLLER, Tag.TAG_STRING)
                ? new ResourceLocation(tag.getString(KEY_CONTROLLER)) : null;
        float offsetX = tag.getFloat(KEY_OFFSET_X);
        float offsetZ = tag.getFloat(KEY_OFFSET_Z);
        float velocityX = tag.getFloat(KEY_VELOCITY_X);
        float velocityZ = tag.getFloat(KEY_VELOCITY_Z);
        float radius = tag.getFloat(KEY_RADIUS);
        float height = tag.contains(KEY_HEIGHT) ? tag.getFloat(KEY_HEIGHT) : 64.0F;
        float bottom = tag.contains(KEY_BOTTOM) ? tag.getFloat(KEY_BOTTOM) : 0.0F;
        return new TornadoDescriptor(uuid, controller, offsetX, offsetZ, velocityX, velocityZ, radius, bottom, height);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TornadoDescriptor other)) {
            return false;
        }
        return Objects.equals(this.id, other.id);
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    @Override
    public String toString() {
        return "TornadoDescriptor{" +
                "id=" + this.id +
                ", controller=" + this.controllerId +
                ", offsetX=" + this.offsetX +
                ", offsetZ=" + this.offsetZ +
                ", radius=" + this.radius +
                ", height=" + this.height +
                '}';
    }
}
