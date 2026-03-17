package net.Gabou.projectatmosphere.network;

import net.Gabou.projectatmosphere.client.ClientSyncLock;
import net.Gabou.projectatmosphere.client.loading.ClientForecastLoadingWorkQueue;
import net.Gabou.projectatmosphere.client.loading.ForecastLoadingStage;
import net.Gabou.projectatmosphere.client.loading.ForecastLoadingState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ForecastLoadingStatusPacket {
    private final ForecastLoadingStage stage;
    private final String message;
    private final String subtext;
    private final float progress;
    private final boolean ready;
    private final boolean reset;
    private final String source;

    public ForecastLoadingStatusPacket(
            ForecastLoadingStage stage,
            String message,
            String subtext,
            float progress,
            boolean ready,
            boolean reset,
            String source
    ) {
        this.stage = stage;
        this.message = message;
        this.subtext = subtext;
        this.progress = progress;
        this.ready = ready;
        this.reset = reset;
        this.source = source;
    }

    public ForecastLoadingStatusPacket(FriendlyByteBuf buf) {
        this.stage = buf.readEnum(ForecastLoadingStage.class);
        this.message = buf.readBoolean() ? buf.readUtf(256) : null;
        this.subtext = buf.readBoolean() ? buf.readUtf(256) : null;
        this.progress = buf.readFloat();
        this.ready = buf.readBoolean();
        this.reset = buf.readBoolean();
        this.source = buf.readUtf(128);
    }

    public static ForecastLoadingStatusPacket status(ForecastLoadingStage stage, String message, String subtext, Float progress, String source) {
        return new ForecastLoadingStatusPacket(stage, message, subtext, progress == null ? -1.0F : progress, false, false, source);
    }

    public static ForecastLoadingStatusPacket ready(String source) {
        return new ForecastLoadingStatusPacket(ForecastLoadingStage.READY, null, null, 1.0F, true, false, source);
    }

    public static ForecastLoadingStatusPacket reset(String source) {
        return new ForecastLoadingStatusPacket(ForecastLoadingStage.WAITING_FOR_SERVER, null, null, -1.0F, false, true, source);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(stage);
        buf.writeBoolean(message != null);
        if (message != null) {
            buf.writeUtf(message, 256);
        }
        buf.writeBoolean(subtext != null);
        if (subtext != null) {
            buf.writeUtf(subtext, 256);
        }
        buf.writeFloat(progress);
        buf.writeBoolean(ready);
        buf.writeBoolean(reset);
        buf.writeUtf(source == null ? "unknown" : source, 128);
    }

    public static ForecastLoadingStatusPacket decode(FriendlyByteBuf buf) {
        return new ForecastLoadingStatusPacket(buf);
    }

    public static void handle(ForecastLoadingStatusPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> applyClient(msg)));
        context.setPacketHandled(true);
    }

    private static void applyClient(ForecastLoadingStatusPacket msg) {
        if (msg.reset) {
            ClientForecastLoadingWorkQueue.reset();
            ClientSyncLock.clear();
            ForecastLoadingState.reset(msg.source);
            return;
        }

        if (msg.ready) {
            ClientForecastLoadingWorkQueue.onServerReady(msg.source);
            return;
        }

        ClientSyncLock.setReadyForLocalPlayer(false);
        ForecastLoadingState.update(
                msg.stage,
                msg.message,
                msg.subtext,
                msg.progress < 0.0F ? null : msg.progress,
                msg.source
        );
    }
}
