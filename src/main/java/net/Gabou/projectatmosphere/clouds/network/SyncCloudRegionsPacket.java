package net.Gabou.projectatmosphere.clouds.network;

import net.Gabou.projectatmosphere.clouds.transport.CloudRegionRenderData;
import net.Gabou.projectatmosphere.platform.network.PacketContext;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Packet serveur vers client pour synchroniser les régions de nuage PA.
 * Ce packet transporte uniquement des CloudRegionRenderData.
 * Il ne transporte pas CloudRegionState et ne crée pas de CloudRenderSnapshot.
 */
public final class SyncCloudRegionsPacket {

    private final List<CloudRegionRenderData> regions;

    public SyncCloudRegionsPacket(Collection<CloudRegionRenderData> regions) {
        this.regions = regions != null ? List.copyOf(regions) : List.of();
    }

    public SyncCloudRegionsPacket(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<CloudRegionRenderData> decodedRegions = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            decodedRegions.add(CloudRegionRenderData.decode(buffer));
        }

        this.regions = List.copyOf(decodedRegions);
    }

    /**
     * Écrit le packet dans le buffer réseau.
     *
     * @param buffer buffer réseau cible
     */
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(regions.size());

        for (CloudRegionRenderData region : regions) {
            region.encode(buffer);
        }
    }

    /**
     * Décode un packet depuis le buffer réseau.
     *
     * @param buffer buffer réseau source
     * @return packet décodé
     */
    public static SyncCloudRegionsPacket decode(FriendlyByteBuf buffer) {
        return new SyncCloudRegionsPacket(buffer);
    }

    /**
     * Gère le packet côté client.
     *
     * @param packet packet reçu
     * @param contextSupplier contexte réseau
     */
    public static void handle(SyncCloudRegionsPacket packet, PacketContext context) {
        context.enqueueClient(() -> CloudRegionPacketDispatcher.handleClientRegions(packet.regions));
        context.markHandled();
    }
}
