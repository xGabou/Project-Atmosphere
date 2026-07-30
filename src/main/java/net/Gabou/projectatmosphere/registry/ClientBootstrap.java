package net.Gabou.projectatmosphere.registry;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.config.AtmoConfigScreen;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.VolumetricCloudClientLifecycle;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.locale.Language;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.Map;

/** Client-only bootstrap. This class is never resolved on a dedicated server. */
@OnlyIn(Dist.CLIENT)
public final class ClientBootstrap {
    private ClientBootstrap() {
    }

    public static void register(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener((FMLClientSetupEvent event) -> onClientSetup(event, modEventBus));
        modEventBus.addListener(VolumetricCloudClientLifecycle::registerReloadListener);
        IConfigScreenFactory configScreenFactory =
                (container, screen) -> new AtmoConfigScreen(screen);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, configScreenFactory);
    }

    private static void onClientSetup(FMLClientSetupEvent event, IEventBus modEventBus) {
        event.enqueueWork(() -> {
            if (ProjectAtmosphere.DEBUG_MODE) {
                ProjectAtmosphere.LOGGER.info("Setting up Project Atmosphere (Client)");
            }
            ClientOnlyRegistrar.registerClient(modEventBus);
            Map<String, String> translations = Language.getInstance().getLanguageData();
            translations.put("sandstorm.debug.blocked", "Nothing to report. Stay alert.");
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.BAROMETER_BLOCK.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.THERMOMETER_BLOCK.get(), RenderType.translucent());
        });

    }
}
