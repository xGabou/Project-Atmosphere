package net.Gabou.projectatmosphere.registry;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.config.AtmoConfigScreen;
import net.Gabou.projectatmosphere.clouds.client.render.volumetric.VolumetricCloudClientLifecycle;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.locale.Language;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.Map;

/** Client-only bootstrap. This class is never resolved on a dedicated server. */
@OnlyIn(Dist.CLIENT)
public final class ClientBootstrap {
    private ClientBootstrap() {
    }

    public static void register() {
        FMLJavaModLoadingContext context = FMLJavaModLoadingContext.get();
        context.getModEventBus().addListener((FMLClientSetupEvent event) -> onClientSetup(event, context));
        context.getModEventBus().addListener(VolumetricCloudClientLifecycle::registerReloadListener);
    }

    @SuppressWarnings("removal")
    private static void onClientSetup(FMLClientSetupEvent event, FMLJavaModLoadingContext context) {
        event.enqueueWork(() -> {
            if (ProjectAtmosphere.DEBUG_MODE) {
                ProjectAtmosphere.LOGGER.info("Setting up Project Atmosphere (Client)");
            }
            ClientOnlyRegistrar.registerClient(MinecraftForge.EVENT_BUS, context);
            Map<String, String> translations = Language.getInstance().getLanguageData();
            translations.put("sandstorm.debug.blocked", "Nothing to report. Stay alert.");
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.BAROMETER_BLOCK.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.THERMOMETER_BLOCK.get(), RenderType.translucent());
        });

        context.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, screen) -> new AtmoConfigScreen(screen))
        );
    }
}
