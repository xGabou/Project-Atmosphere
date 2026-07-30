package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.clouds.cell.network.CloudCellAnalyticsPacket;
import net.Gabou.projectatmosphere.clouds.cell.network.CloudCellDeltaPacket;
import net.Gabou.projectatmosphere.clouds.cell.network.SyncCloudCellsPacket;
import net.Gabou.projectatmosphere.clouds.field.network.CloudFieldDeltaPacket;
import net.Gabou.projectatmosphere.clouds.field.network.SyncCloudFieldsPacket;
import net.Gabou.projectatmosphere.clouds.network.SyncCloudRegionsPacket;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class NetworkHandler {
    private static final String PROTOCOL_VERSION = "12";

    private NetworkHandler() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(NetworkHandler::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(PROTOCOL_VERSION);

        registrar.playToClient(SyncWindPacket.TYPE, SyncWindPacket.STREAM_CODEC, SyncWindPacket::handle);
        registrar.playToClient(BiomeDayTemperaturePacket.TYPE, BiomeDayTemperaturePacket.STREAM_CODEC, BiomeDayTemperaturePacket::handle);
        registrar.playToClient(ForecastLoadingStatusPacket.TYPE, ForecastLoadingStatusPacket.STREAM_CODEC, ForecastLoadingStatusPacket::handle);
        registrar.playToClient(SyncAtmosphereStatusPacket.TYPE, SyncAtmosphereStatusPacket.STREAM_CODEC, SyncAtmosphereStatusPacket::handle);
        registrar.playToClient(FogDebugOverridePacket.TYPE, FogDebugOverridePacket.STREAM_CODEC, FogDebugOverridePacket::handle);
        registrar.playToClient(InstrumentReadoutPacket.TYPE, InstrumentReadoutPacket.STREAM_CODEC, InstrumentReadoutPacket::handle);
        registrar.playToClient(SpawnTornadoPacket.TYPE, SpawnTornadoPacket.STREAM_CODEC, SpawnTornadoPacket::handle);
        registrar.playToClient(RemoveTornadoPacket.TYPE, RemoveTornadoPacket.STREAM_CODEC, RemoveTornadoPacket::handle);
        registrar.playToClient(SyncTornadoesPacket.TYPE, SyncTornadoesPacket.STREAM_CODEC, SyncTornadoesPacket::handle);
        registrar.playToClient(SyncHurricaneStatePacket.TYPE, SyncHurricaneStatePacket.STREAM_CODEC, SyncHurricaneStatePacket::handle);
        registrar.playToClient(SyncCloudRegionsPacket.TYPE, SyncCloudRegionsPacket.STREAM_CODEC, SyncCloudRegionsPacket::handle);
        registrar.playToClient(SyncCloudFieldsPacket.TYPE, SyncCloudFieldsPacket.STREAM_CODEC, SyncCloudFieldsPacket::handle);
        registrar.playToClient(CloudFieldDeltaPacket.TYPE, CloudFieldDeltaPacket.STREAM_CODEC, CloudFieldDeltaPacket::handle);
        registrar.playToClient(SyncCloudCellsPacket.TYPE, SyncCloudCellsPacket.STREAM_CODEC, SyncCloudCellsPacket::handle);
        registrar.playToClient(CloudCellDeltaPacket.TYPE, CloudCellDeltaPacket.STREAM_CODEC, CloudCellDeltaPacket::handle);
        registrar.playToServer(CloudCellAnalyticsPacket.TYPE, CloudCellAnalyticsPacket.STREAM_CODEC, CloudCellAnalyticsPacket::handle);
    }
}
