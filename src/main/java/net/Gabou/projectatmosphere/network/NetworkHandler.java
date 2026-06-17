package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.clouds.network.SyncCloudRegionsPacket;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(NetworkHandler::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(PROTOCOL_VERSION);

        // Register tornado spawn packet
        registrar.playToClient(
                SpawnTornadoPacket.TYPE,
                SpawnTornadoPacket.STREAM_CODEC,
                SpawnTornadoPacket::handle
        );

        // Register wind sync packet
        registrar.playToClient(
                SyncWindPacket.TYPE,
                SyncWindPacket.STREAM_CODEC,
                SyncWindPacket::handle
        );
        registrar.playToClient(BiomeDayTemperaturePacket.TYPE,
                BiomeDayTemperaturePacket.STREAM_CODEC,
                BiomeDayTemperaturePacket::handle
        );
        registrar.playToClient(
                ForecastLoadingStatusPacket.TYPE,
                ForecastLoadingStatusPacket.STREAM_CODEC,
                ForecastLoadingStatusPacket::handle
        );

        registrar.playToClient(
                InstrumentReadoutPacket.TYPE,
                InstrumentReadoutPacket.STREAM_CODEC,
                InstrumentReadoutPacket::handle
        );

        registrar.playToClient(
                RainfallUpdatePacket.TYPE,
                RainfallUpdatePacket.STREAM_CODEC,
                RainfallUpdatePacket::handle
        );

        // NeoForge 1.21.1 payload registration replaces Forge SimpleChannel#registerMessage.
        registrar.playToClient(
                AuthChallengePacket.TYPE,
                AuthChallengePacket.STREAM_CODEC,
                AuthChallengePacket::handle
        );
        registrar.playToServer(
                AuthChallengeReplyPacket.TYPE,
                AuthChallengeReplyPacket.STREAM_CODEC,
                AuthChallengeReplyPacket::handle
        );
        registrar.playToClient(
                FogDebugOverridePacket.TYPE,
                FogDebugOverridePacket.STREAM_CODEC,
                FogDebugOverridePacket::handle
        );
        registrar.playToClient(
                RemoveTornadoPacket.TYPE,
                RemoveTornadoPacket.STREAM_CODEC,
                RemoveTornadoPacket::handle
        );
        registrar.playToClient(
                SyncAtmosphereStatusPacket.TYPE,
                SyncAtmosphereStatusPacket.STREAM_CODEC,
                SyncAtmosphereStatusPacket::handle
        );
        registrar.playToClient(
                SyncHurricaneStatePacket.TYPE,
                SyncHurricaneStatePacket.STREAM_CODEC,
                SyncHurricaneStatePacket::handle
        );
        registrar.playToClient(
                SyncTornadoesPacket.TYPE,
                SyncTornadoesPacket.STREAM_CODEC,
                SyncTornadoesPacket::handle
        );
        registrar.playToClient(
                SyncCloudRegionsPacket.TYPE,
                SyncCloudRegionsPacket.STREAM_CODEC,
                SyncCloudRegionsPacket::handle
        );
    }
}
