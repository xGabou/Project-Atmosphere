package net.Gabou.projectatmosphere.registry;

import net.Gabou.projectatmosphere.client.ClientRenderHook;
import net.Gabou.projectatmosphere.client.ClientTickHandler;
import net.Gabou.projectatmosphere.client.HUDOverlayRenderer;
import net.Gabou.projectatmosphere.client.TornadoShaders;
import net.Gabou.projectatmosphere.config.AtmoConfigScreen;
import net.Gabou.projectatmosphere.util.ParticleAtlasDebugger;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.common.NeoForge;


@OnlyIn(Dist.CLIENT)
public class ClientOnlyRegistrar {
    public static void registerClient(IEventBus modEventBus) {
        ModClient.register(modEventBus);
        TornadoShaders.init(modEventBus);
        HUDOverlayRenderer.register();
        ParticleAtlasDebugger.register(modEventBus);
        ClientRenderHook.register();
        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class,
                () -> (mc, screen) -> new AtmoConfigScreen(screen));
    }
}
