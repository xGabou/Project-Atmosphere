package net.Gabou.projectatmosphere.platform.network;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/** Loader-neutral execution context presented to decoded packet handlers. */
public interface PacketContext {
    void enqueue(Runnable work);

    void enqueueClient(Runnable work);

    @Nullable
    ServerPlayer sender();

    void markHandled();
}
