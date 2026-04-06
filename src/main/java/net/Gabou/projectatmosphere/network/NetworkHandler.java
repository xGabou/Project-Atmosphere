package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int id = 0;

    public static void init() {
        CHANNEL.messageBuilder(SpawnTornadoPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SpawnTornadoPacket::decode)
                .encoder(SpawnTornadoPacket::encode)
                .consumerMainThread(SpawnTornadoPacket::handle)
                .add();
        CHANNEL.messageBuilder(SyncWindPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncWindPacket::decode)
                .encoder(SyncWindPacket::encode)
                .consumerMainThread(SyncWindPacket::handle)
                .add();
        CHANNEL.messageBuilder(BiomeDayTemperaturePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(BiomeDayTemperaturePacket::decode)
                .encoder(BiomeDayTemperaturePacket::encode)
                .consumerMainThread(BiomeDayTemperaturePacket::handle)
                .add();
        CHANNEL.messageBuilder(ForecastLoadingStatusPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(ForecastLoadingStatusPacket::decode)
                .encoder(ForecastLoadingStatusPacket::encode)
                .consumerMainThread(ForecastLoadingStatusPacket::handle)
                .add();
        CHANNEL.messageBuilder(RainfallUpdatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(RainfallUpdatePacket::decode)
                .encoder(RainfallUpdatePacket::encode)
                .consumerMainThread(RainfallUpdatePacket::handle)
                .add();
        CHANNEL.messageBuilder(InstrumentReadoutPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(InstrumentReadoutPacket::decode)
                .encoder(InstrumentReadoutPacket::encode)
                .consumerMainThread(InstrumentReadoutPacket::handle)
                .add();
        CHANNEL.messageBuilder(SyncHurricaneStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncHurricaneStatePacket::decode)
                .encoder(SyncHurricaneStatePacket::encode)
                .consumerMainThread(SyncHurricaneStatePacket::handle)
                .add();
    }
}
