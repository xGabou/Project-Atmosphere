package net.Gabou.projectatmosphere.clouds.field.network;

import net.Gabou.projectatmosphere.clouds.field.CloudFieldHydrationState;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldSourceKind;
import net.Gabou.projectatmosphere.clouds.field.CloudFieldSnapshot;
import net.Gabou.projectatmosphere.clouds.field.CloudLodBand;
import net.Gabou.projectatmosphere.clouds.type.CloudMorphologyFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server-to-client CloudField snapshot sync. This sends field summaries only;
 * cloudlets remain deterministic client/GPU-side data derived from field seed
 * and cloudlet id.
 */
public final class SyncCloudFieldsPacket {
    private static final int VERSION_MARKER = -1;
    private static final int VERSION_SOURCE_KIND = 2;
    private static final int VERSION_MORPHOLOGY = 3;

    private final List<CloudFieldSnapshot> fields;

    public SyncCloudFieldsPacket(Collection<CloudFieldSnapshot> fields) {
        this.fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public SyncCloudFieldsPacket(FriendlyByteBuf buffer) {
        int first = buffer.readVarInt();
        int version = 1;
        int count = first;
        if (first == VERSION_MARKER) {
            version = buffer.readVarInt();
            count = buffer.readVarInt();
        }
        List<CloudFieldSnapshot> decoded = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            decoded.add(decodeSnapshot(buffer, version));
        }
        this.fields = List.copyOf(decoded);
    }

    public List<CloudFieldSnapshot> fields() {
        return fields;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(VERSION_MARKER);
        buffer.writeVarInt(VERSION_MORPHOLOGY);
        buffer.writeVarInt(fields.size());
        for (CloudFieldSnapshot snapshot : fields) {
            encodeSnapshot(buffer, snapshot);
        }
    }

    public static SyncCloudFieldsPacket decode(FriendlyByteBuf buffer) {
        return new SyncCloudFieldsPacket(buffer);
    }

    public static void handle(SyncCloudFieldsPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                CloudFieldPacketDispatcher.handleClientSnapshots(packet.fields)
        ));
        context.setPacketHandled(true);
    }

    static void encodeSnapshot(FriendlyByteBuf buffer, CloudFieldSnapshot snapshot) {
        buffer.writeUUID(snapshot.fieldId());
        buffer.writeLong(snapshot.seed());
        buffer.writeUtf(snapshot.dimensionId());
        writeVec(buffer, snapshot.center());
        writeVec(buffer, snapshot.previousCenter());
        buffer.writeFloat(snapshot.radius());
        buffer.writeFloat(snapshot.baseY());
        buffer.writeFloat(snapshot.topY());
        buffer.writeFloat(snapshot.density());
        buffer.writeFloat(snapshot.coverage());
        buffer.writeFloat(snapshot.growth());
        buffer.writeFloat(snapshot.decay());
        buffer.writeFloat(snapshot.humidityInfluence());
        writeVec(buffer, snapshot.windVector());
        buffer.writeFloat(snapshot.verticalDevelopment());
        buffer.writeFloat(snapshot.stormPotential());
        buffer.writeUtf(snapshot.cloudTypeId());
        buffer.writeEnum(snapshot.morphologyFamily());
        buffer.writeFloat(snapshot.anvilStrength());
        buffer.writeFloat(snapshot.precipitationIntensity());
        buffer.writeEnum(snapshot.sourceKind());
        buffer.writeEnum(snapshot.lodBand());
        buffer.writeEnum(snapshot.previousLodBand());
        buffer.writeEnum(snapshot.hydrationState());
        buffer.writeFloat(snapshot.hydrationProgress());
        buffer.writeVarInt(snapshot.targetCloudletCount());
        buffer.writeVarInt(snapshot.activeCloudletCount());
        buffer.writeLong(snapshot.fieldAgeTicks());
        buffer.writeLong(snapshot.lifetimeTicks());
        buffer.writeLong(snapshot.worldTime());
    }

    static CloudFieldSnapshot decodeSnapshot(FriendlyByteBuf buffer, int version) {
        UUID fieldId = buffer.readUUID();
        long seed = buffer.readLong();
        String dimensionId = buffer.readUtf();
        Vec3 center = readVec(buffer);
        Vec3 previousCenter = readVec(buffer);
        float radius = buffer.readFloat();
        float baseY = buffer.readFloat();
        float topY = buffer.readFloat();
        float density = buffer.readFloat();
        float coverage = buffer.readFloat();
        float growth = buffer.readFloat();
        float decay = buffer.readFloat();
        float humidityInfluence = buffer.readFloat();
        Vec3 wind = readVec(buffer);
        float verticalDevelopment = buffer.readFloat();
        float stormPotential = buffer.readFloat();
        String cloudTypeId = version >= VERSION_MORPHOLOGY
                ? buffer.readUtf()
                : CloudTypeRegistry.DEFAULT_CLOUD_TYPE_ID;
        CloudMorphologyFamily morphologyFamily = version >= VERSION_MORPHOLOGY
                ? buffer.readEnum(CloudMorphologyFamily.class)
                : CloudTypeRegistry.getOrDefault(cloudTypeId).getMorphologyFamily();
        float anvilStrength = version >= VERSION_MORPHOLOGY ? buffer.readFloat() : 0.0F;
        float precipitationIntensity = version >= VERSION_MORPHOLOGY ? buffer.readFloat() : 0.0F;
        CloudFieldSourceKind sourceKind = version >= VERSION_SOURCE_KIND
                ? buffer.readEnum(CloudFieldSourceKind.class)
                : CloudFieldSourceKind.UNKNOWN;
        CloudLodBand lodBand = buffer.readEnum(CloudLodBand.class);
        CloudLodBand previousLodBand = buffer.readEnum(CloudLodBand.class);
        CloudFieldHydrationState hydrationState = buffer.readEnum(CloudFieldHydrationState.class);
        float hydrationProgress = buffer.readFloat();
        int targetCloudletCount = buffer.readVarInt();
        int activeCloudletCount = buffer.readVarInt();
        long fieldAgeTicks = buffer.readLong();
        long lifetimeTicks = buffer.readLong();
        long worldTime = buffer.readLong();

        return new CloudFieldSnapshot(
                fieldId,
                seed,
                dimensionId,
                center,
                previousCenter,
                radius,
                baseY,
                topY,
                density,
                coverage,
                growth,
                decay,
                humidityInfluence,
                wind,
                verticalDevelopment,
                stormPotential,
                cloudTypeId,
                morphologyFamily,
                anvilStrength,
                precipitationIntensity,
                sourceKind,
                lodBand,
                previousLodBand,
                hydrationState,
                hydrationProgress,
                targetCloudletCount,
                activeCloudletCount,
                fieldAgeTicks,
                lifetimeTicks,
                worldTime,
                0.0F,
                Vec3.ZERO
        );
    }

    private static void writeVec(FriendlyByteBuf buffer, Vec3 value) {
        Vec3 vec = value == null ? Vec3.ZERO : value;
        buffer.writeDouble(vec.x());
        buffer.writeDouble(vec.y());
        buffer.writeDouble(vec.z());
    }

    private static Vec3 readVec(FriendlyByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }
}
