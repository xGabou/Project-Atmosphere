package net.Gabou.projectatmosphere.modules.tornado;

import net.Gabou.projectatmosphere.modules.weather.StormLifecyclePhase;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public record TornadoSnapshot(
        UUID id,
        Vec3 position,
        float radius,
        float visualBottomY,
        float visualHeight,
        float windSpeed,
        float windAngle,
        float windGust,
        float normalizedIntensity,
        StormLifecyclePhase phase
) {
    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.id);
        buf.writeDouble(this.position.x);
        buf.writeDouble(this.position.y);
        buf.writeDouble(this.position.z);
        buf.writeFloat(this.radius);
        buf.writeFloat(this.visualBottomY);
        buf.writeFloat(this.visualHeight);
        buf.writeFloat(this.windSpeed);
        buf.writeFloat(this.windAngle);
        buf.writeFloat(this.windGust);
        buf.writeFloat(this.normalizedIntensity);
        buf.writeEnum(this.phase);
    }

    public static TornadoSnapshot read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        Vec3 position = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        float radius = buf.readFloat();
        float bottomY = buf.readFloat();
        float height = buf.readFloat();
        float windSpeed = buf.readFloat();
        float windAngle = buf.readFloat();
        float windGust = buf.readFloat();
        float normalizedIntensity = buf.readFloat();
        StormLifecyclePhase phase = buf.readEnum(StormLifecyclePhase.class);
        return new TornadoSnapshot(id, position, radius, bottomY, height, windSpeed, windAngle, windGust, normalizedIntensity, phase);
    }
}
