package net.Gabou.projectatmosphere.modules.hurricane;

import net.Gabou.projectatmosphere.modules.weather.StormLifecyclePhase;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public record HurricaneSnapshot(
        UUID id,
        Vec3 position,
        float radius,
        float eyewallRadius,
        float windSpeed,
        float windAngle,
        float windGust,
        float normalizedIntensity,
        HurricaneCategory category,
        StormLifecyclePhase phase
) {
    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.id);
        buf.writeDouble(this.position.x);
        buf.writeDouble(this.position.y);
        buf.writeDouble(this.position.z);
        buf.writeFloat(this.radius);
        buf.writeFloat(this.eyewallRadius);
        buf.writeFloat(this.windSpeed);
        buf.writeFloat(this.windAngle);
        buf.writeFloat(this.windGust);
        buf.writeFloat(this.normalizedIntensity);
        buf.writeEnum(this.category);
        buf.writeEnum(this.phase);
    }

    public static HurricaneSnapshot read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        Vec3 position = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        float radius = buf.readFloat();
        float eyewallRadius = buf.readFloat();
        float windSpeed = buf.readFloat();
        float windAngle = buf.readFloat();
        float windGust = buf.readFloat();
        float normalizedIntensity = buf.readFloat();
        HurricaneCategory category = buf.readEnum(HurricaneCategory.class);
        StormLifecyclePhase phase = buf.readEnum(StormLifecyclePhase.class);
        return new HurricaneSnapshot(id, position, radius, eyewallRadius, windSpeed, windAngle, windGust, normalizedIntensity, category, phase);
    }
}
