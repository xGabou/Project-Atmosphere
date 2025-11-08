package net.Gabou.projectatmosphere.registry;

import net.Gabou.projectatmosphere.client.ClientTickHandler;
import net.Gabou.projectatmosphere.compat.CompatHandler;
import net.Gabou.projectatmosphere.compat.rainbows.RainbowWeatherTracker;
import net.Gabou.projectatmosphere.config.AtmoConfigScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;


@OnlyIn(Dist.CLIENT)
public class ClientOnlyRegistrar {
    public static void registerClient(IEventBus modEventBus, FMLJavaModLoadingContext context) {
        modEventBus.register(ClientTickHandler.class);
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, screen) -> new AtmoConfigScreen(screen)));
        RainbowWeatherTracker.setEnabled(CompatHandler.isRainbowsLoaded());
    }
}
