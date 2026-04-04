package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.network.SyncWindPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "2";
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
        CHANNEL.messageBuilder(RemoveTornadoPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(RemoveTornadoPacket::decode)
                .encoder(RemoveTornadoPacket::encode)
                .consumerMainThread(RemoveTornadoPacket::handle)
                .add();
        CHANNEL.messageBuilder(SyncTornadoesPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncTornadoesPacket::decode)
                .encoder(SyncTornadoesPacket::encode)
                .consumerMainThread(SyncTornadoesPacket::handle)
                .add();
        CHANNEL.messageBuilder(SyncHurricanesPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncHurricanesPacket::decode)
                .encoder(SyncHurricanesPacket::encode)
                .consumerMainThread(SyncHurricanesPacket::handle)
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
        CHANNEL.messageBuilder(SyncAtmosphereStatusPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncAtmosphereStatusPacket::decode)
                .encoder(SyncAtmosphereStatusPacket::encode)
                .consumerMainThread(SyncAtmosphereStatusPacket::handle)
                .add();
        CHANNEL.messageBuilder(FogDebugOverridePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(FogDebugOverridePacket::decode)
                .encoder(FogDebugOverridePacket::encode)
                .consumerMainThread(FogDebugOverridePacket::handle)
                .add();
        CHANNEL.messageBuilder(InstrumentReadoutPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(InstrumentReadoutPacket::decode)
                .encoder(InstrumentReadoutPacket::encode)
                .consumerMainThread(InstrumentReadoutPacket::handle)
                .add();


    }
}


