package net.Gabou.projectatmosphere.client.render.pipeline;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import dev.nonamecrackers2.simpleclouds.client.dh.pipeline.DhSupportPipeline;
import dev.nonamecrackers2.simpleclouds.client.event.impl.DetermineCloudRenderPipelineEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class SimpleCloudsDhPipelineSelector {
    private SimpleCloudsDhPipelineSelector() {
    }

    @SubscribeEvent
    public static void selectDhPipeline(DetermineCloudRenderPipelineEvent event) {
        if (SimpleCloudsMod.dhLoaded()) {
            event.overridePipeline(DhSupportPipeline.INSTANCE);
        }
    }
}
