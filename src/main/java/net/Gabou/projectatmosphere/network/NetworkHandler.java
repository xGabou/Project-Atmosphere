package net.Gabou.projectatmosphere.network;

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
    }
}
