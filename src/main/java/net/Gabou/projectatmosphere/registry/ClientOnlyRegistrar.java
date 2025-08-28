package net.Gabou.projectatmosphere.registry;

import net.Gabou.projectatmosphere.client.ClientTickHandler;
import net.Gabou.projectatmosphere.config.AtmoConfigScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;


@OnlyIn(Dist.CLIENT)
public class ClientOnlyRegistrar {
    public static void registerClient(IEventBus modEventBus) {
        modEventBus.register(ClientTickHandler.class);
        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class,
                () -> (mc, screen) -> new AtmoConfigScreen(screen));
    }
}
