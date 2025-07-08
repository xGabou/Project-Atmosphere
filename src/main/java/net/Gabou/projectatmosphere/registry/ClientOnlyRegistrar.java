package net.Gabou.projectatmosphere.registry;

import net.Gabou.projectatmosphere.client.ClientTickHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

// src/main/java/net/Gabou/projectatmosphere/ClientOnlyRegistrar.java
@OnlyIn(Dist.CLIENT)
public class ClientOnlyRegistrar {
    public static void registerClient(IEventBus modEventBus) {
        MinecraftForge.EVENT_BUS.register(ClientTickHandler.class);
    }
}
