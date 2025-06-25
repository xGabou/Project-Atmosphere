package net.Gabou.projectatmosphere.registry;

import net.Gabou.projectatmosphere.network.SyncBiomeDataLoginPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Optional;

public class ModNetworking {
    private static final String PROTOCOL_VERSION = "1";
    static int ID = 0;

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation("projectatmosphere", "main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(s -> true)
            .serverAcceptedVersions(s -> true)
            .simpleChannel();

    public static void register() {
        ModNetworking.CHANNEL.messageBuilder(SyncBiomeDataLoginPacket.class, ID++, NetworkDirection.LOGIN_TO_CLIENT)
                .loginIndex(SyncBiomeDataLoginPacket::getAsInt, SyncBiomeDataLoginPacket::setLoginIndex)
                .buildLoginPacketList(isLocal -> {
                    if (isLocal) return List.of(); // Pas de sync côté serveur local

                    // Retarde l'envoi jusqu'à ce que les données soient prêtes
                    return ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers().stream()
                            .map(player -> Pair.of("projectatmosphere.biome", new SyncBiomeDataLoginPacket("biome preloaded")))
                            .toList();
                })

                .decoder(SyncBiomeDataLoginPacket::new)
                .encoder(SyncBiomeDataLoginPacket::encode)
                .add();

    }


}
