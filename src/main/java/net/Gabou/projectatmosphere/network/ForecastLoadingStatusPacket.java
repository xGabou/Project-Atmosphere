package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.client.ClientSyncLock;
import net.Gabou.projectatmosphere.client.loading.ClientForecastLoadingWorkQueue;
import net.Gabou.projectatmosphere.client.loading.ForecastLoadingStage;
import net.Gabou.projectatmosphere.client.loading.ForecastLoadingState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ForecastLoadingStatusPacket(
        ForecastLoadingStage stage,
        String message,
        String subtext,
        float progress,
        boolean ready,
        boolean reset,
        String source
) implements CustomPacketPayload {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, "forecast_loading_status");

    public static final Type<ForecastLoadingStatusPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, ForecastLoadingStatusPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeEnum(pkt.stage);
                        buf.writeBoolean(pkt.message != null);
                        if (pkt.message != null) {
                            buf.writeUtf(pkt.message, 256);
                        }
                        buf.writeBoolean(pkt.subtext != null);
                        if (pkt.subtext != null) {
                            buf.writeUtf(pkt.subtext, 256);
                        }
                        buf.writeFloat(pkt.progress);
                        buf.writeBoolean(pkt.ready);
                        buf.writeBoolean(pkt.reset);
                        buf.writeUtf(pkt.source == null ? "unknown" : pkt.source, 128);
                    },
                    buf -> new ForecastLoadingStatusPacket(
                            buf.readEnum(ForecastLoadingStage.class),
                            buf.readBoolean() ? buf.readUtf(256) : null,
                            buf.readBoolean() ? buf.readUtf(256) : null,
                            buf.readFloat(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readUtf(128)
                    )
            );

    public static ForecastLoadingStatusPacket status(ForecastLoadingStage stage, String message, String subtext, Float progress, String source) {
        return new ForecastLoadingStatusPacket(stage, message, subtext, progress == null ? -1.0F : progress, false, false, source);
    }

    public static ForecastLoadingStatusPacket ready(String source) {
        return new ForecastLoadingStatusPacket(ForecastLoadingStage.READY, null, null, 1.0F, true, false, source);
    }

    public static ForecastLoadingStatusPacket reset(String source) {
        return new ForecastLoadingStatusPacket(ForecastLoadingStage.WAITING_FOR_SERVER, null, null, -1.0F, false, true, source);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ForecastLoadingStatusPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> applyClient(pkt));
    }

    private static void applyClient(ForecastLoadingStatusPacket pkt) {
        if (pkt.reset) {
            ClientForecastLoadingWorkQueue.reset();
            ClientSyncLock.clear();
            ForecastLoadingState.reset(pkt.source);
            return;
        }

        if (pkt.ready) {
            ClientForecastLoadingWorkQueue.onServerReady(pkt.source);
            return;
        }

        ClientSyncLock.setReadyForLocalPlayer(false);
        ForecastLoadingState.update(
                pkt.stage,
                pkt.message,
                pkt.subtext,
                pkt.progress < 0.0F ? null : pkt.progress,
                pkt.source
        );
    }
}
