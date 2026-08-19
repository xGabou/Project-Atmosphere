package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.clouds.cell.network.CloudCellAnalyticsPacket;
import net.Gabou.projectatmosphere.clouds.cell.network.CloudCellDeltaPacket;
import net.Gabou.projectatmosphere.clouds.cell.network.SyncCloudCellsPacket;
import net.Gabou.projectatmosphere.clouds.field.network.SyncCloudFieldsPacket;
import net.Gabou.projectatmosphere.clouds.field.network.CloudFieldDeltaPacket;
import net.Gabou.projectatmosphere.clouds.network.SyncCloudRegionsPacket;
import net.Gabou.projectatmosphere.platform.network.AtmosphereNetwork;
import net.Gabou.projectatmosphere.platform.network.ForgeNetworkTransport;
import net.Gabou.projectatmosphere.platform.network.ForgePacketContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    // CloudField snapshot/delta payload v5 transports versioned PUFF layout
    // metadata. A protocol-11 peer would otherwise be accepted and decode the
    // two new fields as the following floats, corrupting the rest of packet.
    private static final String PROTOCOL_VERSION = "12";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void init() {
        AtmosphereNetwork.install(new ForgeNetworkTransport(CHANNEL));
        // -----------------------------------------------------------------
        // Client-bound packets
        // -----------------------------------------------------------------
        CHANNEL.messageBuilder(SyncWindPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncWindPacket::decode)
                .encoder(SyncWindPacket::encode)
                .consumerMainThread((packet, context) -> ForgePacketContext.dispatch(context, packet::handle))
                .add();
        CHANNEL.messageBuilder(BiomeDayTemperaturePacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(BiomeDayTemperaturePacket::decode)
                .encoder(BiomeDayTemperaturePacket::encode)
                .consumerMainThread((packet, context) -> ForgePacketContext.dispatch(
                        context,
                        adapted -> BiomeDayTemperaturePacket.handle(packet, adapted)))
                .add();
        CHANNEL.messageBuilder(ForecastLoadingStatusPacket.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(ForecastLoadingStatusPacket::decode)
                .encoder(ForecastLoadingStatusPacket::encode)
                .consumerMainThread((packet, context) -> ForgePacketContext.dispatch(
                        context,
                        adapted -> ForecastLoadingStatusPacket.handle(packet, adapted)))
                .add();
        CHANNEL.messageBuilder(SyncAtmosphereStatusPacket.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncAtmosphereStatusPacket::decode)
                .encoder(SyncAtmosphereStatusPacket::encode)
                .consumerMainThread((packet, context) -> ForgePacketContext.dispatch(
                        context,
                        adapted -> SyncAtmosphereStatusPacket.handle(packet, adapted)))
                .add();
        CHANNEL.messageBuilder(FogDebugOverridePacket.class, 4, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(FogDebugOverridePacket::decode)
                .encoder(FogDebugOverridePacket::encode)
                .consumerMainThread((packet, context) -> ForgePacketContext.dispatch(
                        context,
                        adapted -> FogDebugOverridePacket.handle(packet, adapted)))
                .add();
        CHANNEL.messageBuilder(InstrumentReadoutPacket.class, 5, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(InstrumentReadoutPacket::decode)
                .encoder(InstrumentReadoutPacket::encode)
                .consumerMainThread((packet, context) -> ForgePacketContext.dispatch(
                        context,
                        adapted -> InstrumentReadoutPacket.handle(packet, adapted)))
                .add();
        CHANNEL.messageBuilder(SpawnTornadoPacket.class, 6, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SpawnTornadoPacket::decode)
                .encoder(SpawnTornadoPacket::encode)
                .consumerMainThread((packet, context) -> ForgePacketContext.dispatch(context, packet::handle))
                .add();
        CHANNEL.messageBuilder(RemoveTornadoPacket.class, 7, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(RemoveTornadoPacket::decode)
                .encoder(RemoveTornadoPacket::encode)
                .consumerMainThread((packet, context) -> ForgePacketContext.dispatch(context, packet::handle))
                .add();
        CHANNEL.messageBuilder(SyncTornadoesPacket.class, 8, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncTornadoesPacket::decode)
                .encoder(SyncTornadoesPacket::encode)
                .consumerMainThread((packet, context) -> ForgePacketContext.dispatch(context, packet::handle))
                .add();
        CHANNEL.messageBuilder(SyncHurricaneStatePacket.class, 9, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncHurricaneStatePacket::decode)
                .encoder(SyncHurricaneStatePacket::encode)
                .consumerMainThread((packet, context) -> ForgePacketContext.dispatch(context, packet::handle))
                .add();
        // -----------------------------------------------------------------
        // Clouds
        // -----------------------------------------------------------------
        CHANNEL.messageBuilder(SyncCloudRegionsPacket.class, 10, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncCloudRegionsPacket::decode)
                .encoder(SyncCloudRegionsPacket::encode)
                .consumerMainThread((packet, context) -> ForgePacketContext.dispatch(
                        context,
                        adapted -> SyncCloudRegionsPacket.handle(packet, adapted)))
                .add();
        CHANNEL.messageBuilder(SyncCloudFieldsPacket.class, 11, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncCloudFieldsPacket::decode)
                .encoder(SyncCloudFieldsPacket::encode)
                .consumerMainThread((packet, context) -> ForgePacketContext.dispatch(
                        context,
                        adapted -> SyncCloudFieldsPacket.handle(packet, adapted)))
                .add();
        CHANNEL.messageBuilder(CloudFieldDeltaPacket.class, 12, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(CloudFieldDeltaPacket::decode)
                .encoder(CloudFieldDeltaPacket::encode)
                .consumerMainThread((packet, context) -> ForgePacketContext.dispatch(
                        context,
                        adapted -> CloudFieldDeltaPacket.handle(packet, adapted)))
                .add();
        CHANNEL.messageBuilder(SyncCloudCellsPacket.class, 13, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncCloudCellsPacket::decode)
                .encoder(SyncCloudCellsPacket::encode)
                .consumerMainThread((packet, context) -> ForgePacketContext.dispatch(
                        context,
                        adapted -> SyncCloudCellsPacket.handle(packet, adapted)))
                .add();
        CHANNEL.messageBuilder(CloudCellDeltaPacket.class, 14, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(CloudCellDeltaPacket::decode)
                .encoder(CloudCellDeltaPacket::encode)
                .consumerMainThread((packet, context) -> ForgePacketContext.dispatch(
                        context,
                        adapted -> CloudCellDeltaPacket.handle(packet, adapted)))
                .add();
        CHANNEL.messageBuilder(CloudCellAnalyticsPacket.class, 15, NetworkDirection.PLAY_TO_SERVER)
                .decoder(CloudCellAnalyticsPacket::decode)
                .encoder(CloudCellAnalyticsPacket::encode)
                .consumerMainThread((packet, context) -> ForgePacketContext.dispatch(
                        context,
                        adapted -> CloudCellAnalyticsPacket.handle(packet, adapted)))
                .add();
    }
}


