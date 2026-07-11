package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.cell.network.CloudCellAnalyticsPacket;
import net.Gabou.projectatmosphere.clouds.cell.network.CloudCellDeltaPacket;
import net.Gabou.projectatmosphere.clouds.cell.network.SyncCloudCellsPacket;
import net.Gabou.projectatmosphere.clouds.field.network.SyncCloudFieldsPacket;
import net.Gabou.projectatmosphere.clouds.field.network.CloudFieldDeltaPacket;
import net.Gabou.projectatmosphere.clouds.network.SyncCloudRegionsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "10";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void init() {
        // -----------------------------------------------------------------
        // Client-bound packets
        // -----------------------------------------------------------------
        CHANNEL.messageBuilder(SyncWindPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncWindPacket::decode)
                .encoder(SyncWindPacket::encode)
                .consumerMainThread(SyncWindPacket::handle)
                .add();
        CHANNEL.messageBuilder(BiomeDayTemperaturePacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(BiomeDayTemperaturePacket::decode)
                .encoder(BiomeDayTemperaturePacket::encode)
                .consumerMainThread(BiomeDayTemperaturePacket::handle)
                .add();
        CHANNEL.messageBuilder(ForecastLoadingStatusPacket.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(ForecastLoadingStatusPacket::decode)
                .encoder(ForecastLoadingStatusPacket::encode)
                .consumerMainThread(ForecastLoadingStatusPacket::handle)
                .add();
        CHANNEL.messageBuilder(SyncAtmosphereStatusPacket.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncAtmosphereStatusPacket::decode)
                .encoder(SyncAtmosphereStatusPacket::encode)
                .consumerMainThread(SyncAtmosphereStatusPacket::handle)
                .add();
        CHANNEL.messageBuilder(FogDebugOverridePacket.class, 4, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(FogDebugOverridePacket::decode)
                .encoder(FogDebugOverridePacket::encode)
                .consumerMainThread(FogDebugOverridePacket::handle)
                .add();
        CHANNEL.messageBuilder(InstrumentReadoutPacket.class, 5, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(InstrumentReadoutPacket::decode)
                .encoder(InstrumentReadoutPacket::encode)
                .consumerMainThread(InstrumentReadoutPacket::handle)
                .add();
        CHANNEL.messageBuilder(SpawnTornadoPacket.class, 6, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SpawnTornadoPacket::decode)
                .encoder(SpawnTornadoPacket::encode)
                .consumerMainThread(SpawnTornadoPacket::handle)
                .add();
        CHANNEL.messageBuilder(RemoveTornadoPacket.class, 7, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(RemoveTornadoPacket::decode)
                .encoder(RemoveTornadoPacket::encode)
                .consumerMainThread(RemoveTornadoPacket::handle)
                .add();
        CHANNEL.messageBuilder(SyncTornadoesPacket.class, 8, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncTornadoesPacket::decode)
                .encoder(SyncTornadoesPacket::encode)
                .consumerMainThread(SyncTornadoesPacket::handle)
                .add();
        CHANNEL.messageBuilder(SyncHurricaneStatePacket.class, 9, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncHurricaneStatePacket::decode)
                .encoder(SyncHurricaneStatePacket::encode)
                .consumerMainThread(SyncHurricaneStatePacket::handle)
                .add();
        // -----------------------------------------------------------------
        // Clouds
        // -----------------------------------------------------------------
        CHANNEL.messageBuilder(SyncCloudRegionsPacket.class, 10, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncCloudRegionsPacket::decode)
                .encoder(SyncCloudRegionsPacket::encode)
                .consumerMainThread(SyncCloudRegionsPacket::handle)
                .add();
        CHANNEL.messageBuilder(SyncCloudFieldsPacket.class, 11, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncCloudFieldsPacket::decode)
                .encoder(SyncCloudFieldsPacket::encode)
                .consumerMainThread(SyncCloudFieldsPacket::handle)
                .add();
        CHANNEL.messageBuilder(CloudFieldDeltaPacket.class, 12, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(CloudFieldDeltaPacket::decode)
                .encoder(CloudFieldDeltaPacket::encode)
                .consumerMainThread(CloudFieldDeltaPacket::handle)
                .add();
        CHANNEL.messageBuilder(SyncCloudCellsPacket.class, 13, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncCloudCellsPacket::decode)
                .encoder(SyncCloudCellsPacket::encode)
                .consumerMainThread(SyncCloudCellsPacket::handle)
                .add();
        CHANNEL.messageBuilder(CloudCellDeltaPacket.class, 14, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(CloudCellDeltaPacket::decode)
                .encoder(CloudCellDeltaPacket::encode)
                .consumerMainThread(CloudCellDeltaPacket::handle)
                .add();
        CHANNEL.messageBuilder(CloudCellAnalyticsPacket.class, 15, NetworkDirection.PLAY_TO_SERVER)
                .decoder(CloudCellAnalyticsPacket::decode)
                .encoder(CloudCellAnalyticsPacket::encode)
                .consumerMainThread(CloudCellAnalyticsPacket::handle)
                .add();
    }
}


