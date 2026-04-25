package net.Gabou.projectatmosphere.client.render;

import dev.nonamecrackers2.simpleclouds.client.dh.pipeline.DhSupportPipeline;
import dev.nonamecrackers2.simpleclouds.client.event.impl.DetermineCloudRenderPipelineEvent;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ProjectAtmosphere.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SimpleCloudsDhPipelineSelector {
    private SimpleCloudsDhPipelineSelector() {
    }

    @SubscribeEvent
    public static void selectDhPipeline(DetermineCloudRenderPipelineEvent event) {
        if (ModList.get().isLoaded("distanthorizons")) {
            event.overridePipeline(DhSupportPipeline.INSTANCE);
        }
    }
}
