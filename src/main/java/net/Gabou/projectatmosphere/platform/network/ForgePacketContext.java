package net.Gabou.projectatmosphere.platform.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Forge 1.20.1 adapter for inbound packet execution. */
public final class ForgePacketContext implements PacketContext {
    private final NetworkEvent.Context delegate;

    private ForgePacketContext(NetworkEvent.Context delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public static void dispatch(
            Supplier<NetworkEvent.Context> contextSupplier,
            Consumer<PacketContext> handler
    ) {
        handler.accept(new ForgePacketContext(contextSupplier.get()));
    }

    @Override
    public void enqueue(Runnable work) {
        delegate.enqueueWork(work);
    }

    @Override
    public void enqueueClient(Runnable work) {
        delegate.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> work.run()));
    }

    @Override
    @Nullable
    public ServerPlayer sender() {
        return delegate.getSender();
    }

    @Override
    public void markHandled() {
        delegate.setPacketHandled(true);
    }
}
